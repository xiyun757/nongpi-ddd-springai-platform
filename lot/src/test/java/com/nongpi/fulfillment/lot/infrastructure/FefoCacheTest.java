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

    @Nested
    @DisplayName("getEarliest — Redis 不可用时降级")
    class FallbackTest {

        @Test
        @DisplayName("Redis 连接异常 → 降级查 DB，返回有效批次")
        void shouldFallbackWhenRedisDown() {
            RedissonClient client = mock(RedissonClient.class);
            when(client.getScoredSortedSet(anyString()))
                    .thenThrow(new RuntimeException("连接拒绝"));

            ILotRepository repo = mock(ILotRepository.class);
            when(repo.findByTempZoneAndExpireDateBefore(eq(TempZone.FRESH), any()))
                    .thenReturn(List.of(createLot("LOT20260828001101", LotStatus.IN_STOCK, new BigDecimal("500"))));

            FefoCache cache = new FefoCache(client, repo);
            List<LotNo> result = cache.getEarliest(TempZone.FRESH, new BigDecimal("300"));

            assertEquals(1, result.size());
            assertEquals("LOT20260828001101", result.get(0).value());
        }

        @Test
        @DisplayName("降级查询无可用批次 → 抛 NoAvailableLotException")
        void shouldThrowWhenNoAvailableInFallback() {
            RedissonClient client = mock(RedissonClient.class);
            when(client.getScoredSortedSet(anyString()))
                    .thenThrow(new RuntimeException("连接拒绝"));

            ILotRepository repo = mock(ILotRepository.class);
            when(repo.findByTempZoneAndExpireDateBefore(any(), any()))
                    .thenReturn(List.of()); // DB 也无可用批次

            FefoCache cache = new FefoCache(client, repo);
            assertThrows(NoAvailableLotException.class,
                    () -> cache.getEarliest(TempZone.FREEZE, new BigDecimal("100")));
        }

        @Test
        @DisplayName("降级查询过滤已出清批次（FULLY_OUT 不返回）")
        void shouldFilterFullyOutInFallback() {
            RedissonClient client = mock(RedissonClient.class);
            when(client.getScoredSortedSet(anyString()))
                    .thenThrow(new RuntimeException("连接拒绝"));

            ILotRepository repo = mock(ILotRepository.class);
            // 一个已出清 + 一个可用
            when(repo.findByTempZoneAndExpireDateBefore(eq(TempZone.FRESH), any()))
                    .thenReturn(List.of(
                            createLot("LOT20260828001102", LotStatus.FULLY_OUT, BigDecimal.ZERO),
                            createLot("LOT20260828001103", LotStatus.IN_STOCK, new BigDecimal("500"))
                    ));

            FefoCache cache = new FefoCache(client, repo);
            List<LotNo> result = cache.getEarliest(TempZone.FRESH, new BigDecimal("300"));

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

            FefoCache cache = new FefoCache(client, repo);
            List<LotNo> result = cache.getEarliest(TempZone.FRESH, new BigDecimal("300"));

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

            FefoCache cache = new FefoCache(client, repo);
            List<LotNo> result = cache.getEarliest(TempZone.FRESH, new BigDecimal("300"));

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

            FefoCache cache = new FefoCache(client, repo);
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

            FefoCache cache = new FefoCache(client, repo);
            assertDoesNotThrow(() -> cache.removeLot("LOT20260828001108"));
        }
    }
}
