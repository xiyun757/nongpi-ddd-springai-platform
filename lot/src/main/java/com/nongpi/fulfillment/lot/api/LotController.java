package com.nongpi.fulfillment.lot.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.api.dto.LotResponse;
import com.nongpi.fulfillment.lot.api.dto.LotTransferResponse;
import com.nongpi.fulfillment.lot.application.LotAppService;
import com.nongpi.fulfillment.lot.application.LotAppService.*;
import com.nongpi.fulfillment.lot.application.LotQueryService;
import com.nongpi.fulfillment.lot.domain.Lot;
import com.nongpi.fulfillment.lot.domain.LotNo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 批次 REST 控制器
 *
 * <p>提供入库、出库、转库接口以及批次查询接口。
 * 查询委托 {@link LotQueryService}，写操作委托 {@link LotAppService}，
 * Controller 自身不再直接注入 Mapper，符合 DDD 分层。</p>
 */
@RestController
@RequestMapping("/api/lots")
public class LotController {

    private final LotAppService lotAppService;
    private final LotQueryService lotQueryService;

    public LotController(LotAppService lotAppService, LotQueryService lotQueryService) {
        this.lotAppService = lotAppService;
        this.lotQueryService = lotQueryService;
    }

    /**
     * 入库
     */
    @PostMapping("/inbound")
    public LotInboundResponse inbound(@Valid @RequestBody InboundRequest req) {
        Lot lot = lotAppService.inbound(new InboundCommand(
                req.skuId(),
                req.tempZone(),
                req.produceDate(),
                req.expireDate(),
                req.qty(),
                req.supplierId()
        ));
        return toInboundResponse(lot);
    }

    /**
     * 出库
     */
    @PostMapping("/outbound")
    public LotOutboundResult outbound(@Valid @RequestBody OutboundRequest req) {
        return lotAppService.outbound(new OutboundCommand(
                req.skuId(),
                req.tempZone(),
                req.qty(),
                req.toLocation()
        ));
    }

    /**
     * 转库
     */
    @PostMapping("/transfer")
    public LotOutboundResult transfer(@Valid @RequestBody TransferRequest req) {
        return lotAppService.transfer(new TransferCommand(
                req.lotNo(),
                req.qty(),
                req.fromLocation(),
                req.toLocation()
        ));
    }

    /**
     * 批次详情
     */
    @GetMapping("/{lotNo}")
    public LotResponse getById(@PathVariable String lotNo) {
        return lotQueryService.getDetail(lotNo);
    }

    /**
     * 批次出入库记录
     */
    @GetMapping("/{lotNo}/transfers")
    public List<LotTransferResponse> getTransfers(@PathVariable String lotNo) {
        return lotQueryService.listTransfers(lotNo);
    }

    /**
     * 分页列表（支持 skuId、tempZone、status、lotNo 筛选）
     */
    @GetMapping("/list")
    public IPage<LotResponse> list(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String tempZone,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String lotNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lotQueryService.list(skuId, tempZone, status, lotNo, page, size);
    }

    // ── 响应 / 请求 DTO ──

    public record LotInboundResponse(
            String lotNo,
            Long skuId,
            String tempZone,
            String produceDate,
            String expireDate,
            BigDecimal initialQty,
            BigDecimal remainingQty,
            String status,
            Long supplierId
    ) {}

    public record InboundRequest(
            @NotNull(message = "skuId不能为空") Long skuId,
            @NotNull(message = "tempZone不能为空") TempZone tempZone,
            @NotNull(message = "生产日期不能为空") LocalDate produceDate,
            @NotNull(message = "过期日期不能为空") LocalDate expireDate,
            @NotNull(message = "数量不能为空") @DecimalMin(value = "0.01", message = "数量必须大于0") BigDecimal qty,
            @NotNull(message = "supplierId不能为空") Long supplierId
    ) {}

    public record OutboundRequest(
            @NotNull(message = "skuId不能为空") Long skuId,
            @NotNull(message = "tempZone不能为空") TempZone tempZone,
            @NotNull(message = "数量不能为空") @DecimalMin(value = "0.01", message = "数量必须大于0") BigDecimal qty,
            String toLocation
    ) {}

    public record TransferRequest(
            @NotBlank(message = "批次号不能为空") String lotNo,
            @NotNull(message = "数量不能为空") @DecimalMin(value = "0.01", message = "数量必须大于0") BigDecimal qty,
            String fromLocation,
            String toLocation
    ) {}

    /**
     * 批次出入库记录响应 DTO — 已下沉至 {@link com.nongpi.fulfillment.lot.api.dto.LotTransferResponse}
     */
    private LotInboundResponse toInboundResponse(Lot lot) {
        return new LotInboundResponse(
                lot.getLotNo().value(),
                lot.getSkuId(),
                lot.getTempZone().name(),
                lot.getProduceDate().toString(),
                lot.getExpireDate().toString(),
                lot.getInitialQty(),
                lot.getRemainingQty(),
                lot.getStatus().name(),
                lot.getSupplierId()
        );
    }
}
