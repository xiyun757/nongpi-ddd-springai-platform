package com.nongpi.fulfillment.inventory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nongpi.fulfillment.common.exception.NotFoundException;
import com.nongpi.fulfillment.inventory.api.dto.InventoryDetailResponse;
import com.nongpi.fulfillment.inventory.api.dto.InventoryResponse;
import com.nongpi.fulfillment.inventory.infrastructure.mapper.InventoryMapper;
import com.nongpi.fulfillment.inventory.infrastructure.persistence.InventoryPO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 库存查询服务 — 应用层
 * <p>负责库存列表等读操作，Controller 不再直接注入 Mapper。</p>
 */
@Service
public class InventoryQueryService {

    private final InventoryMapper inventoryMapper;

    public InventoryQueryService(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    public IPage<InventoryResponse> list(Long skuId, String tempZone,
                                        BigDecimal minQty, BigDecimal maxQty,
                                        int page, int size) {
        LambdaQueryWrapper<InventoryPO> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) {
            wrapper.eq(InventoryPO::getSkuId, skuId);
        }
        if (tempZone != null && !tempZone.isBlank()) {
            wrapper.eq(InventoryPO::getTempZone, tempZone);
        }
        if (minQty != null) {
            wrapper.ge(InventoryPO::getTotalQty, minQty);
        }
        if (maxQty != null) {
            wrapper.le(InventoryPO::getTotalQty, maxQty);
        }
        Page<InventoryPO> poPage = inventoryMapper.selectPage(new Page<>(page, size), wrapper);
        return poPage.convert(InventoryResponse::fromPO);
    }

    /**
     * 单个库存详情（从 PO 直接返回，含 updatedAt）
     *
     * <p>读操作走 PO → DTO，与 {@link com.nongpi.fulfillment.lot.application.LotQueryService#getDetail}
     * 保持一致风格；写操作仍走聚合根。</p>
     */
    public InventoryDetailResponse getDetail(Long skuId, String tempZone) {
        LambdaQueryWrapper<InventoryPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryPO::getSkuId, skuId)
               .eq(InventoryPO::getTempZone, tempZone);
        InventoryPO po = inventoryMapper.selectOne(wrapper);
        if (po == null) {
            throw new NotFoundException(
                    "库存不存在：skuId=" + skuId + " tempZone=" + tempZone);
        }
        return InventoryDetailResponse.fromPO(po);
    }
}

