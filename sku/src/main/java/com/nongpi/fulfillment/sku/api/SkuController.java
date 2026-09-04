package com.nongpi.fulfillment.sku.api;

import com.nongpi.fulfillment.sku.api.dto.CreateSkuRequest;
import com.nongpi.fulfillment.sku.api.dto.SkuResponse;
import com.nongpi.fulfillment.sku.application.SkuAppService;
import com.nongpi.fulfillment.sku.application.SkuAppService.CreateSkuCommand;
import com.nongpi.fulfillment.sku.application.SkuQueryService;
import com.nongpi.fulfillment.sku.domain.Sku;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品 REST 控制器
 *
 * <p>查询委托 {@link SkuQueryService}，写操作委托 {@link SkuAppService}，
 * Controller 不直接注入 Mapper（DDD 分层一致）。</p>
 */
@RestController
@RequestMapping("/api/skus")
public class SkuController {

    private final SkuAppService skuAppService;
    private final SkuQueryService skuQueryService;

    public SkuController(SkuAppService skuAppService, SkuQueryService skuQueryService) {
        this.skuAppService = skuAppService;
        this.skuQueryService = skuQueryService;
    }

    /**
     * 商品列表（下拉选择 / 商品名展示）
     */
    @GetMapping
    public List<SkuResponse> list() {
        return skuQueryService.listAll().stream().map(SkuResponse::fromDomain).toList();
    }

    /**
     * 新建商品
     */
    @PostMapping
    public SkuResponse create(@Valid @RequestBody CreateSkuRequest req) {
        Sku sku = skuAppService.create(new CreateSkuCommand(
                req.name(), req.spec(), req.unit(), req.barcode()));
        return SkuResponse.fromDomain(sku);
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        skuAppService.delete(id);
    }
}
