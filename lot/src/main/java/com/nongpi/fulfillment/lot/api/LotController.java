package com.nongpi.fulfillment.lot.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nongpi.fulfillment.lot.api.dto.InboundRequest;
import com.nongpi.fulfillment.lot.api.dto.LotInboundResponse;
import com.nongpi.fulfillment.lot.api.dto.LotResponse;
import com.nongpi.fulfillment.lot.api.dto.LotTransferResponse;
import com.nongpi.fulfillment.lot.api.dto.OutboundRequest;
import com.nongpi.fulfillment.lot.api.dto.TransferRequest;
import com.nongpi.fulfillment.lot.application.LotAppService;
import com.nongpi.fulfillment.lot.application.LotAppService.*;
import com.nongpi.fulfillment.lot.application.LotQueryService;
import com.nongpi.fulfillment.lot.domain.Lot;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
     * <p>lotNo 可选：传入=指定批次直接出库（批次行按钮），不传=FEFO 自动选最早过期批次。</p>
     */
    @PostMapping("/outbound")
    public LotOutboundResult outbound(@Valid @RequestBody OutboundRequest req) {
        return lotAppService.outbound(new OutboundCommand(
                req.skuId(),
                req.tempZone(),
                req.qty(),
                req.toLocation(),
                req.lotNo()
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

    /**
     * 批次入库响应转换
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
