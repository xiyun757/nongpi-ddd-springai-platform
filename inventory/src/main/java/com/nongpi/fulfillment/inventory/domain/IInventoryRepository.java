package com.nongpi.fulfillment.inventory.domain;

import com.nongpi.fulfillment.common.domain.TempZone;

import java.util.Optional;

/**
 * 库存仓储接口 — 领域层定义
 *
 * <p>定义 Inventory 聚合根的持久化契约。仓储实现位于 infrastructure 层。</p>
 */
public interface IInventoryRepository {

    /**
     * 根据 SKU + 温区查询库存（唯一业务键）
     *
     * @param skuId    SKU 标识
     * @param tempZone 温区
     * @return Optional 包裹的 Inventory 聚合根
     */
    Optional<Inventory> findBySkuAndZone(Long skuId, TempZone tempZone);

    /**
     * 保存新聚合根（首次创建库存记录时调用）
     *
     * @param inventory 库存聚合根
     */
    void save(Inventory inventory);

    /**
     * 更新聚合根（乐观锁由 MyBatis-Plus @Version 控制）
     *
     * @param inventory 库存聚合根
     */
    void update(Inventory inventory);
}
