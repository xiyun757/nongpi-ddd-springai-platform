package com.nongpi.fulfillment.inventory.api.dto;

import com.nongpi.fulfillment.inventory.infrastructure.persistence.InventoryPO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存详情响应 DTO — 独立于 Controller 的 API 层对象
 *
 * <p>由 {@code inventory.application.InventoryQueryService} 组装并返回，
 * 应用层不依赖 Controller 嵌套类型（DDD 分层倒置修复）。</p>
 *
 * <p>可用量计算规则与领域对象 {@code Inventory#getAvailableQty()} 保持一致：
 * 可用量 = totalQty - frozenQty。PO → DTO 转换统一走 {@link #fromPO}。</p>
 *
 * @param id            库存记录 ID
 * @param skuId         商品 SKU ID
 * @param tempZone      温区
 * @param totalQty      总库存数量
 * @param frozenQty     冻结数量
 * @param availableQty  可用数量（= totalQty - frozenQty）
 * @param version       乐观锁版本号
 * @param updatedAt     最后更新时间
 */
public record InventoryDetailResponse(
        Long id,
        Long skuId,
        String tempZone,
        BigDecimal totalQty,
        BigDecimal frozenQty,
        BigDecimal availableQty,
        int version,
        LocalDateTime updatedAt
) {
    public static InventoryDetailResponse fromPO(InventoryPO po) {
        return new InventoryDetailResponse(
                po.getId(),
                po.getSkuId(),
                po.getTempZone(),
                po.getTotalQty(),
                po.getFrozenQty(),
                po.getTotalQty().subtract(po.getFrozenQty()),
                po.getVersion() != null ? po.getVersion() : 0,
                po.getUpdatedAt()
        );
    }
}
