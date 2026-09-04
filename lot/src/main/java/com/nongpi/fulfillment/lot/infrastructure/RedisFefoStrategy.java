package com.nongpi.fulfillment.lot.infrastructure;

import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.domain.*;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis ZSet 驱动的 FEFO 查询策略
 * <p>使用 Redisson ScoredSortedSet 按 expireDate 排序，O(logN) 取最早到期批次。
 * Redis 不可用或 ZSet 为空时抛出 {@link FefoStrategyUnavailableException}，
 * 由 {@link FefoStrategyFactory} 降级为 {@link DatabaseFefoStrategy}。</p>
 */
@Component
public class RedisFefoStrategy implements FefoStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedisFefoStrategy.class);
    private static final String KEY_PREFIX = "fefo:";
    private static final int MAX_CANDIDATES = 200;

    private final RedissonClient redissonClient;
    private final ILotRepository repository;

    public RedisFefoStrategy(RedissonClient redissonClient, ILotRepository repository) {
        this.redissonClient = redissonClient;
        this.repository = repository;
    }

    @Override
    public List<LotNo> getEarliest(TempZone zone, BigDecimal qty, Long skuId) {
        String key = fefoKey(zone);
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
        Collection<String> members = zset.valueRange(0, MAX_CANDIDATES - 1);

        if (members == null || members.isEmpty()) {
            throw new FefoStrategyUnavailableException("ZSet 为空: zone=" + zone);
        }

        // 批量查 DB 验证（一次 IN 查询替代 N 次 getById）
        List<LotNo> lotNos = members.stream().map(LotNo::fromString).toList();
        List<Lot> lots = repository.findByIds(lotNos);

        // 内存过滤：仅保留 IN_STOCK/PARTIAL_OUT + 未过期 + SKU 匹配（防止同温区误扣其他 SKU 批次），按 expireDate 升序
        // 与 DatabaseFefoStrategy 过滤逻辑对齐：过期批次必须排除，否则 outbound()→canOutbound() 抛 ExpiredException 导致整个 FEFO 出库事务回滚
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Lot> validLots = lots.stream()
                .filter(l -> l.getStatus() == LotStatus.IN_STOCK
                        || l.getStatus() == LotStatus.PARTIAL_OUT)
                .filter(l -> !l.getExpireDate().isBefore(today))
                .filter(l -> skuId == null || Objects.equals(l.getSkuId(), skuId))
                .sorted(Comparator.comparing(Lot::getExpireDate))
                .toList();

        // 清理 ZSet 脏数据（DB 已删 / 已出清 / 已过期）
        Set<String> validLotNoStrs = validLots.stream()
                .map(l -> l.getLotNo().value())
                .collect(Collectors.toSet());
        for (String m : members) {
            if (!validLotNoStrs.contains(m)) {
                zset.remove(m);
            }
        }

        // 累加 remainingQty 直到满足
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
            throw new FefoStrategyUnavailableException("ZSet member 全部失效: zone=" + zone);
        }
        return result;
    }

    /**
     * 批次入库后加入 Redis ZSet
     */
    public void addLot(String lotNo, TempZone tempZone, java.time.LocalDate expireDate) {
        try {
            double score = expireDate.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(fefoKey(tempZone));
            zset.add(score, lotNo);
        } catch (Exception e) {
            log.warn("Redis 不可用，跳过 ZSet addLot: {}", e.getMessage());
        }
    }

    /**
     * 从指定温区的 ZSet 中移除批次
     */
    public void removeLot(String lotNo, TempZone tempZone) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(fefoKey(tempZone));
            zset.remove(lotNo);
        } catch (Exception e) {
            log.warn("Redis 不可用，跳过 ZSet removeLot: {}" , e.getMessage());
        }
    }

    /**
     * 从所有温区的 ZSet 中移除批次（不确定温区时使用）
     */
    public void removeLot(String lotNo) {
        for (TempZone zone : TempZone.values()) {
            try {
                RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(fefoKey(zone));
                zset.remove(lotNo);
            } catch (Exception e) {
                log.warn("Redis 不可用，跳过 ZSet removeLot: {}", e.getMessage());
            }
        }
    }

    static String fefoKey(TempZone zone) {
        return KEY_PREFIX + zone.name().toLowerCase();
    }
}
