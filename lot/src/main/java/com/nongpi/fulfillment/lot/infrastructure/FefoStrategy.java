package com.nongpi.fulfillment.lot.infrastructure;

import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.domain.LotNo;

import java.math.BigDecimal;
import java.util.List;

/**
 * FEFO 批次查询策略接口
 * <p>策略模式：定义"查找最早到期批次"的统一契约，
 * Redis 与数据库各自提供实现，由 {@link FefoStrategyFactory} 决定使用哪个。</p>
 */
public interface FefoStrategy {

    /**
     * 获取指定温区最早到期的批次列表，累计满足 qty 为止
     *
     * @param zone 目标温区
     * @param qty  需要凑够的数量
     * @param skuId 商品 SKU（null 表示不过滤）
     * @return 批次号列表（按过期日期升序）
     * @throws NoAvailableLotException 没有可用批次
     */
    List<LotNo> getEarliest(TempZone zone, BigDecimal qty, Long skuId);
}
