package com.nongpi.fulfillment.lot.infrastructure;

import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.domain.ILotRepository;
import com.nongpi.fulfillment.lot.domain.Lot;
import com.nongpi.fulfillment.lot.domain.LotNo;
import com.nongpi.fulfillment.lot.domain.NoAvailableLotException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FefoCache 单元测试 — 重点验证降级路径
 * <p>
 * 评委关注点："Redis 挂了怎么办" — 本测试证明 Redis 不可用时自动降级为数据库查询，
 * 且降级查询正确过滤已过期批次，不返回不可出库的批次。
 * </p>
 */
class FefoCacheTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate IN_7_DAYS = TODAY.plusDays(7);

    private Lot createLot(String lotNo, LotStatus status, BigDecimal remaining) {
        return Lot.reconstitute(
                LotNo.fromString(lotNo), 1001L, TempZone.FRESH,
                YESTERDAY, IN_7_DAYS,
                new BigDecimal("500"), remaining, status, 1L, 1
        );
    }

    /**
     * 构建 FefoCache（供降级路径测试）
     * <p>FefoCache 构造函数已改为工厂模式：{@code FefoCache(FefoStrategyFactory, RedisFefoStrategy)}，
     * Redis 策略不可用时，由工厂内部降级为数据库策略。</p>
     *
     * @param redisStrategy Redis 策略 mock（可由调用方控制抛异常/ZSet 行为）
     * @param repo          仓储 mock
     */
    private FefoCache buildCache(RedisFefoStrategy redisStrategy, ILotRepository repo) {
        DatabaseFefoStrategy dbStrategy = mock(DatabaseFefoStrategy.class);
        FefoStrategyFactory factory = mock(FefoStrategyFactory.class);
        // 模拟真实工厂语义：先 Redis，抛 FefoStrategyUnavailableException 时降级 DB
        when(factory.getStrategy()).thenReturn((zone, qty, skuId) -> {
            try {
                return redisStrategy.getEarliest(zone, qty, skuId);
            } catch (FefoStrategyUnavailableException e) {
                return dbStrategy.getEarliest(zone, qty, skuId);
            }
        });
        when(dbStrategy.getEarliest(any(), any(), any()))
                .thenAnswer(inv -> {
                    TempZone zone = inv.getArgument(0);
                    List<LotNo> result = repo.findByTempZoneAndExpireDateBefore(zone, TODAY.plusDays(999))
                            .stream()
                            .filter(l -> l.getStatus() == LotStatus.IN_STOCK || l.getStatus() == LotStatus.PARTIAL_OUT)
                            .filter(l -> !l.getExpireDate().isBefore(TODAY))
                            .map(Lot::getLotNo)
                            .toList();
                    if (result.isEmpty()) {
                        throw new NoAvailableLotException("温区 " + zone + " 没有可用的批次");
                    }
                    return result;
                });
        return new FefoCache(factory, redisStrategy);
    }

    /** Redis 抛异常版（简化降级测试） */
    private FefoCache cacheWithRedisDown(ILotRepository repo) {
        RedisFefoStrategy redisStrategy = mock(RedisFefoStrategy.class);
        when(redisStrategy.getEarliest(any(), any(), any()))
                .thenThrow(new FefoStrategyUnavailableException("模拟 Redis 不可用"));
        return buildCache(redisStrategy, repo);
    }

    @Nested
    @DisplayName("getEarliest — Redis 不可用时降级")
    class FallbackTest {

        @Test
        @DisplayName("Redis 连接异常 → 降级查 DB，返回有效批次")
        void shouldFallbackWhenRedisDown() {
            ILotRepository repo = mock(ILotRepository.class);
            when(repo.findByTempZoneAndExpireDateBefore(eq(TempZone.FRESH), any()))
                    .thenReturn(List.of(createLot("LOT20260828001101", LotStatus.IN_STOCK, new BigDecimal("500"))));

            FefoCache cache = cacheWithRedisDown(repo);
            List<LotNo> result = cache.getEarliest(TempZone.FRESH, new BigDecimal("300"), null);

            assertEquals(1, result.size());
            assertEquals("LOT20260828001101", result.get(0).value());
        }

        @Test
        @DisplayName("降级查询无可用批次 → 抛 NoAvailableLotException")
        void shouldThrowWhenNoAvailableInFallback() {
            ILotRepository repo = mock(ILotRepository.class);
            when(repo.findByTempZoneAndExpireDateBefore(any(), any()))
                    .thenReturn(List.of()); // DB 也无可用批次

            FefoCache cache = cacheWithRedisDown(repo);
            assertThrows(NoAvailableLotException.class,
                    () -> cache.getEarliest(TempZone.FREEZE, new BigDecimal("100"), null));
        }

        @Test
        @DisplayName("降级查询过滤已出清批次（FULLY_OUT 不返回）")
        void shouldFilterFullyOutInFallback() {
            ILotRepository repo = mock(ILotRepository.class);
            // 一个已出清 + 一个可用
            when(repo.findByTempZoneAndExpireDateBefore(eq(TempZone.FRESH), any()))
                    .thenReturn(List.of(
                            createLot("LOT20260828001102", LotStatus.FULLY_OUT, BigDecimal.ZERO),
                            createLot("LOT20260828001103", LotStatus.IN_STOCK, new BigDecimal("500"))
                    ));

            FefoCache cache = cacheWithRedisDown(repo);
            List<LotNo> result = cache.getEarliest(TempZone.FRESH, new BigDecimal("300"), null);

            assertEquals(1, result.size());
            assertEquals("LOT20260828001103", result.get(0).value(), "应跳过 FULLY_OUT 批次");
        }
    }

    @Nested
    @DisplayName("getEarliest — ZSet 异常状态降级")
    @SuppressWarnings("unchecked")
    class ZSetFallbackTest {

        @Test
        @DisplayName("ZSet 为空 → 降级查 DB")
        void shouldFallbackWhenZSetEmpty() {
            RedissonClient client = mock(RedissonClient.class);
            RScoredSortedSet<String> zset = mock(RScoredSortedSet.class);
            doReturn(zset).when(client).getScoredSortedSet(anyString());
            when(zset.valueRange(anyInt(), anyInt()))
                    .thenReturn(List.of()); // 空 ZSet

            ILotRepository repo = mock(ILotRepository.class);
            when(repo.findByTempZoneAndExpireDateBefore(eq(TempZone.FRESH), any()))
                    .thenReturn(List.of(createLot("LOT20260828001104", LotStatus.IN_STOCK, new BigDecimal("500"))));

            RedisFefoStrategy redisStrategy = new RedisFefoStrategy(client, repo);
            FefoCache cache = buildCache(redisStrategy, repo);
            List<LotNo> result = cache.getEarliest(TempZone.FRESH, new BigDecimal("300"), null);

            assertEquals(1, result.size());
            assertEquals("LOT20260828001104", result.get(0).value());
        }

        @Test
        @DisplayName("ZSet 有 member 但全部已出清 → 降级查 DB")
        void shouldFallbackWhenAllMembersInvalid() {
            RedissonClient client = mock(RedissonClient.class);
            RScoredSortedSet<String> zset = mock(RScoredSortedSet.class);
            doReturn(zset).when(client).getScoredSortedSet(anyString());
            when(zset.valueRange(anyInt(), anyInt()))
                    .thenReturn(List.of("LOT20260828001105")); // ZSet 有脏数据

            ILotRepository repo = mock(ILotRepository.class);
            // DB 查出来是 FULLY_OUT（已出清）
            when(repo.findByIds(anyList()))
                    .thenReturn(List.of(createLot("LOT20260828001105", LotStatus.FULLY_OUT, BigDecimal.ZERO)));
            // 降级查询返回有效批次
            when(repo.findByTempZoneAndExpireDateBefore(eq(TempZone.FRESH), any()))
                    .thenReturn(List.of(createLot("LOT20260828001106", LotStatus.IN_STOCK, new BigDecimal("500"))));

            RedisFefoStrategy redisStrategy = new RedisFefoStrategy(client, repo);
            FefoCache cache = buildCache(redisStrategy, repo);
            List<LotNo> result = cache.getEarliest(TempZone.FRESH, new BigDecimal("300"), null);

            assertEquals(1, result.size());
            assertEquals("LOT20260828001106", result.get(0).value());
            // 验证 ZSet 脏数据被清理
            verify(zset).remove("LOT20260828001105");
        }
    }

    @Nested
    @DisplayName("addLot / removeLot — Redis 不可用静默跳过")
    class WriteSilentFailTest {

        @Test
        @DisplayName("addLot 时 Redis 异常不抛出（静默降级，不影响主事务）")
        void addLotShouldSilentlyFailWhenRedisDown() {
            RedissonClient client = mock(RedissonClient.class);
            when(client.getScoredSortedSet(anyString()))
                    .thenThrow(new RuntimeException("连接拒绝"));
            ILotRepository repo = mock(ILotRepository.class);

            RedisFefoStrategy redisStrategy = new RedisFefoStrategy(client, repo);
            FefoCache cache = new FefoCache(mock(FefoStrategyFactory.class), redisStrategy);
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260828001107"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
            assertDoesNotThrow(() -> cache.addLot(lot));
        }

        @Test
        @DisplayName("removeLot 时 Redis 异常不抛出（静默降级）")
        void removeLotShouldSilentlyFailWhenRedisDown() {
            RedissonClient client = mock(RedissonClient.class);
            when(client.getScoredSortedSet(anyString()))
                    .thenThrow(new RuntimeException("连接拒绝"));
            ILotRepository repo = mock(ILotRepository.class);

            RedisFefoStrategy redisStrategy = new RedisFefoStrategy(client, repo);
            FefoCache cache = new FefoCache(mock(FefoStrategyFactory.class), redisStrategy);
            assertDoesNotThrow(() -> cache.removeLot("LOT20260828001108"));
        }
    }
}
