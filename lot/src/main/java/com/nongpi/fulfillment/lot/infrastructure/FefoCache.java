package com.nongpi.fulfillment.lot.infrastructure;

import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.domain.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * FEFO 缓存 — 门面层，委托策略工厂查询，委托 Redis 策略写入
 *
 * <p>查询路径（{@link #getEarliest}）委托给 {@link FefoStrategyFactory}，
 * 工厂内部按策略模式选择 Redis 或数据库实现，调用方无感知。</p>
 *
 * <p>写入路径（{@link #addLot}/{@link #removeLot}）委托给 {@link RedisFefoStrategy}，
 * 因为写入只有 Redis 一条路径（数据库是源头，不需要"写入数据库"）。</p>
 *
 * <p>客户端统一：使用 Redisson 替代 StringRedisTemplate，
 * 与分布式锁共用 RedissonClient 连接池。</p>
 */
@Service
public class FefoCache {

    private final FefoStrategyFactory strategyFactory;
    private final RedisFefoStrategy redisStrategy;

    public FefoCache(FefoStrategyFactory strategyFactory, RedisFefoStrategy redisStrategy) {
        this.strategyFactory = strategyFactory;
        this.redisStrategy = redisStrategy;
    }

    /**
     * 获取最早过期的一批批次号，累计满足 qty 为止
     * <p>委托给策略工厂，工厂优先 Redis ZSet，降级数据库查询。</p>
     *
     * @param zone 温区
     * @param qty  需要凑够的数量
     * @param skuId SKU（null 不过滤，防止同温区误扣其他 SKU 批次）
     */
    public List<LotNo> getEarliest(TempZone zone, BigDecimal qty, Long skuId) {
        return strategyFactory.getStrategy().getEarliest(zone, qty, skuId);
    }

    /**
     * 批次入库后加入 Redis ZSet
     */
    public void addLot(Lot lot) {
        redisStrategy.addLot(lot.getLotNo().value(), lot.getTempZone(), lot.getExpireDate());
    }

    /**
     * 批次入库后加入 Redis ZSet（离散参数版本，供 MQ 消费者使用）
     */
    public void addLot(String lotNo, TempZone tempZone, LocalDate expireDate) {
        redisStrategy.addLot(lotNo, tempZone, expireDate);
    }

    /**
     * 从所有温区的 ZSet 中移除批次（不确定温区时使用）
     */
    public void removeLot(String lotNo) {
        redisStrategy.removeLot(lotNo);
    }

    /**
     * 从指定温区的 ZSet 中移除批次（供 MQ 消费者使用，已知温区时更高效）
     */
    public void removeLot(String lotNo, TempZone tempZone) {
        redisStrategy.removeLot(lotNo, tempZone);
    }
}
