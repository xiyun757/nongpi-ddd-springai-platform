package com.nongpi.fulfillment.lot.infrastructure;

import com.nongpi.fulfillment.lot.domain.*;
import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.TempZone;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FEFO 缓存 — Redis ZSet 驱动的先到期先出策略
 *
 * <p>每个温区对应一个 ZSet key {@code fefo:<tempZone>}，
 * score = expireDate 的 UTC 纪元秒数，member = lotNo。</p>
 *
 * <p>Redis 不可用时自动降级为数据库查询。</p>
 *
 * <p><b>客户端统一</b>：使用 Redisson 的 {@link RScoredSortedSet} 替代
 * {@code StringRedisTemplate.opsForZSet()}，与分布式锁共用同一个
 * {@link RedissonClient} 连接池，避免双连接池资源浪费。</p>
 */
@Service
public class FefoCache {

    private static final Logger log = LoggerFactory.getLogger(FefoCache.class);
    private static final String KEY_PREFIX = "fefo:";
    private static final int FALLBACK_DAYS = 999;
    private static final int MAX_CANDIDATES = 200;

    private final RedissonClient redissonClient;
    private final ILotRepository repository;

    public FefoCache(RedissonClient redissonClient, ILotRepository repository) {
        this.redissonClient = redissonClient;
        this.repository = repository;
    }

    /**
     * 批次入库后加入 Redis ZSet
     */
    public void addLot(Lot lot) {
        addLot(lot.getLotNo().value(), lot.getTempZone(), lot.getExpireDate());
    }

    /**
     * 批次入库后加入 Redis ZSet（离散参数版本，供 MQ 消费者使用）
     */
    public void addLot(String lotNo, TempZone tempZone, LocalDate expireDate) {
        String key = fefoKey(tempZone);
        double score = expireDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            zset.add(score, lotNo);
        } catch (Exception e) {
            log.warn("Redis 不可用，跳过 FefoCache.addLot: {}", e.getMessage());
        }
    }

    /**
     * 批次全部出库后从所有温区的 ZSet 中移除（用于不确定在具体的某一温区时
     */
    public void removeLot(String lotNo) {
        for (TempZone zone : TempZone.values()) {
            try {
                RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(fefoKey(zone));
                zset.remove(lotNo);
            } catch (Exception e) {
                log.warn("Redis 不可用，跳过 FefoCache.removeLot: {}", e.getMessage());
            }
        }
    }

    /**
     * 从指定温区的 ZSet 中移除批次（供 MQ 消费者使用，已知温区时更高效）
     */
    public void removeLot(String lotNo, TempZone tempZone) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(fefoKey(tempZone));
            zset.remove(lotNo);
        } catch (Exception e) {
            log.warn("Redis 不可用，跳过 FefoCache.removeLot: {}", e.getMessage());
        }
    }

    /**
     * 获取最早过期的一批批次号，累计满足 qty 为止
     *
     * <p>采用非破坏性读取：仅 rangeByScore 查询，不移除 ZSet 成员。
     * 无效批次（已出清/已过期/DB不存在）标记待清理，在 OutboundService 出库成功后
     * 通过 removeLot 清理。避免出库失败时 Redis 缓存与 DB 不一致。</p>
     *
     * <p>N+1 修复：先一次性取出 ZSet 候选 member，再通过
     * {@link ILotRepository#findByIds(List)} 批量查 DB，避免逐条查询。</p>
     *
     * @param zone 目标温区
     * @param qty  需要凑够的数量
     * @return 批次号列表（按过期日期升序）
     * @throws NoAvailableLotException 没有可用批次
     */
    public List<LotNo> getEarliest(TempZone zone, BigDecimal qty) {
        String key = fefoKey(zone);

        try {
            // 1. 取 ZSet 前 MAX_CANDIDATES 个 member（按 score 升序，即过期日期升序）
            //    valueRange(start, start+count) 限制返回数量，避免全量读取
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            Collection<String> members = zset.valueRange(0, MAX_CANDIDATES - 1);
            if (members == null || members.isEmpty()) {
                // ZSet 为空（应用重启 / 缓存未预热），降级为 DB 查询
                log.info("ZSet 为空，降级为数据库查询: zone={}", zone);
                return fallbackGetEarliest(zone, qty);
            }

            // 2. 批量查 DB（一次 IN 查询替代 N 次 getById）
            List<LotNo> lotNos = members.stream()
                    .map(LotNo::fromString)
                    .toList();
            List<Lot> lots = repository.findByIds(lotNos);

            // 3. 内存过滤：仅保留 IN_STOCK/PARTIAL_OUT，并按 expireDate 升序
            List<Lot> validLots = lots.stream()
                    .filter(l -> l.getStatus() == LotStatus.IN_STOCK
                            || l.getStatus() == LotStatus.PARTIAL_OUT)
                    .sorted(Comparator.comparing(Lot::getExpireDate))
                    .toList();

            // 4. 清理 ZSet 中的脏数据（DB 已删 / 已出清已过期）
            Set<String> validLotNoStrs = validLots.stream()
                    .map(l -> l.getLotNo().value())
                    .collect(Collectors.toSet());
            for (String m : members) {
                if (!validLotNoStrs.contains(m)) {
                    zset.remove(m);
                }
            }

            // 5. 累加 remainingQty 直到满足
            BigDecimal accumulated = BigDecimal.ZERO;
            List<LotNo> result = new ArrayList<>();
            for (Lot lot : validLots) {
                result.add(lot.getLotNo());
                accumulated = accumulated.add(lot.getRemainingQty());
                if (accumulated.compareTo(qty) >= 0) {
                    break;
                }
            }

            if (result.isEmpty()) {
                // ZSet 有 member 但 DB 过滤后全部无效（已出清/已过期），降级查 DB
                log.info("ZSet member 全部失效，降级为数据库查询: zone={}", zone);
                return fallbackGetEarliest(zone, qty);
            }
            return result;
        } catch (NoAvailableLotException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis 不可用，降级为数据库查询: {}", e.getMessage());
            return fallbackGetEarliest(zone, qty);
        }
    }

    // ── 内部方法 ──

    private static String fefoKey(TempZone zone) {
        return KEY_PREFIX + zone.name().toLowerCase();
    }

    /**
     * 数据库降级查询：查询未过期批次，按温区/过期日期过滤
     * <p>用 {@link ILotRepository#findByTempZoneAndExpireDateBefore} 在 DB 端
     * 按温区过滤（原代码查全表再内存过滤温区，数据量大时性能差）。
     * 同时排除已过期批次 — Lot.outbound() 会因 ExpiredException 拒绝出库，
     * 选中过期批次只会导致出库失败。</p>
     */
    private List<LotNo> fallbackGetEarliest(TempZone zone, BigDecimal qty) {
        LocalDate today = LocalDate.now();
        List<Lot> candidates = repository.findByTempZoneAndExpireDateBefore(zone, today.plusDays(FALLBACK_DAYS))
                .stream()
                .filter(l -> l.getStatus() == LotStatus.IN_STOCK
                        || l.getStatus() == LotStatus.PARTIAL_OUT)
                .filter(l -> !l.getExpireDate().isBefore(today)) // 排除已过期批次
                .toList();

        BigDecimal accumulated = BigDecimal.ZERO;
        List<LotNo> result = new ArrayList<>();
        for (Lot lot : candidates) {
            result.add(lot.getLotNo());
            accumulated = accumulated.add(lot.getRemainingQty());
            if (accumulated.compareTo(qty) >= 0) {
                break;
            }
        }

        if (result.isEmpty()) {
            throw new NoAvailableLotException("温区 " + zone + " 没有可用的批次");
        }
        return result;
    }
}
