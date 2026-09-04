package com.nongpi.fulfillment.inventory.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nongpi.fulfillment.inventory.api.dto.AdjustRequest;
import com.nongpi.fulfillment.inventory.api.dto.FreezeRequest;
import com.nongpi.fulfillment.inventory.api.dto.InventoryDetailResponse;
import com.nongpi.fulfillment.inventory.api.dto.InventoryResponse;
import com.nongpi.fulfillment.inventory.application.InventoryAppService;
import com.nongpi.fulfillment.inventory.application.InventoryAppService.AdjustStockCommand;
import com.nongpi.fulfillment.inventory.application.InventoryAppService.FreezeCommand;
import com.nongpi.fulfillment.inventory.application.InventoryQueryService;
import com.nongpi.fulfillment.inventory.domain.Inventory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 库存 REST 控制器
 *
 * <p>提供库存查询、调整、冻结/解冻接口。
 * 查询委托 {@link InventoryQueryService}，写操作委托 {@link InventoryAppService}，
 * Controller 自身不再直接注入 Mapper。</p>
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryAppService inventoryAppService;
    private final InventoryQueryService inventoryQueryService;

    public InventoryController(InventoryAppService inventoryAppService,
                               InventoryQueryService inventoryQueryService) {
        this.inventoryAppService = inventoryAppService;
        this.inventoryQueryService = inventoryQueryService;
    }

    /**
     * 分页列表（支持 skuId、tempZone、minQty、maxQty 筛选）
     */
    @GetMapping("/list")
    public IPage<InventoryResponse> list(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String tempZone,
            @RequestParam(required = false) BigDecimal minQty,
            @RequestParam(required = false) BigDecimal maxQty,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryQueryService.list(skuId, tempZone, minQty, maxQty, page, size);
    }

    /**
     * 单个库存详情
     *
     * <p>读操作走 QueryService（从 PO 直接取 updatedAt 等时间字段），
     * 不经过聚合根 — 与 LotController 详情接口保持一致风格。</p>
     */
    @GetMapping("/{skuId}/{tempZone}")
    public InventoryDetailResponse getBySkuAndZone(
            @PathVariable Long skuId,
            @PathVariable String tempZone) {
        return inventoryQueryService.getDetail(skuId, tempZone);
    }

    /**
     * 调整库存（管理员用）
     */
    @PostMapping("/adjust")
    public InventoryDetailResponse adjust(@Valid @RequestBody AdjustRequest req) {
        AdjustStockCommand cmd = new AdjustStockCommand(
                req.skuId(), req.tempZone(), req.delta());
        Inventory inv = inventoryAppService.adjustStock(cmd);
        return toDetailResponse(inv);
    }

    /**
     * 冻结库存
     */
    @PostMapping("/freeze")
    public InventoryDetailResponse freeze(@Valid @RequestBody FreezeRequest req) {
        FreezeCommand cmd = new FreezeCommand(req.skuId(), req.tempZone(), req.qty());
        Inventory inv = inventoryAppService.freeze(cmd);
        return toDetailResponse(inv);
    }

    /**
     * 解冻库存
     */
    @PostMapping("/unfreeze")
    public InventoryDetailResponse unfreeze(@Valid @RequestBody FreezeRequest req) {
        FreezeCommand cmd = new FreezeCommand(req.skuId(), req.tempZone(), req.qty());
        Inventory inv = inventoryAppService.unfreeze(cmd);
        return toDetailResponse(inv);
    }

    /** 写操作后的响应（不含 updatedAt，从聚合根转换） */
    private InventoryDetailResponse toDetailResponse(Inventory inv) {
        return new InventoryDetailResponse(
                inv.getId(),
                inv.getSkuId(),
                inv.getTempZone().name(),
                inv.getTotalQty(),
                inv.getFrozenQty(),
                inv.getAvailableQty(),
                inv.getVersion(),
                null
        );
    }
}
