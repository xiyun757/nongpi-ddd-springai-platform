package com.nongpi.fulfillment.lot.infrastructure;

import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.domain.LotNo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * FEFO 策略工厂 — 综合案例：工厂模式 + 策略模式
 * <p>工厂决定使用哪个策略：优先 Redis（快），失败降级数据库（稳）。
 * 调用方只需 {@code factory.getStrategy().getEarliest(zone, qty)}，
 * 无需关心底层是 Redis 还是 DB。</p>
 */
@Component
public class FefoStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(FefoStrategyFactory.class);

    private final RedisFefoStrategy redisStrategy;
    private final DatabaseFefoStrategy dbStrategy;

    public FefoStrategyFactory(RedisFefoStrategy redisStrategy, DatabaseFefoStrategy dbStrategy) {
        this.redisStrategy = redisStrategy;
        this.dbStrategy = dbStrategy;
    }

    /**
     * 获取 FEFO 查询策略 — 优先 Redis，降级 DB
     * <p>返回一个组合策略：先尝试 Redis，捕获 {@link FefoStrategyUnavailableException}
     * 后自动降级为数据库查询。调用方无感知。</p>
     */
    public FefoStrategy getStrategy() {
        return this::resolveEarliest;
    }

    private List<LotNo> resolveEarliest(TempZone zone, BigDecimal qty, Long skuId) {
        try {
            return redisStrategy.getEarliest(zone, qty, skuId);
        } catch (FefoStrategyUnavailableException e) {
            log.warn("Redis 策略不可用，降级为数据库查询: {}", e.getMessage());
            return dbStrategy.getEarliest(zone, qty, skuId);
        } catch (Exception e) {
            log.warn("Redis 异常，降级为数据库查询: {}", e.getMessage());
            return dbStrategy.getEarliest(zone, qty, skuId);
        }
    }
}
