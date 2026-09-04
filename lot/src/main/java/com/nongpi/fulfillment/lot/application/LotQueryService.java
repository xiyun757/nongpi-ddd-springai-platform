package com.nongpi.fulfillment.lot.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nongpi.fulfillment.common.exception.NotFoundException;
import com.nongpi.fulfillment.lot.api.dto.LotResponse;
import com.nongpi.fulfillment.lot.api.dto.LotTransferResponse;
import com.nongpi.fulfillment.lot.infrastructure.mapper.LotMapper;
import com.nongpi.fulfillment.lot.infrastructure.mapper.LotTransferMapper;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotPO;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotTransferPO;
import com.nongpi.fulfillment.sku.infrastructure.mapper.SkuMapper;
import com.nongpi.fulfillment.sku.infrastructure.persistence.SkuPO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批次查询服务 — 应用层
 * <p>负责批次列表/详情/流转记录等读操作，与写操作（{@link LotAppService}）分离（CQRS 思想）。
 * Controller 不再直接注入 Mapper。</p>
 */
@Service
public class LotQueryService {

    /** t_lot_transfer.transfer_type 枚举 → 前端展示类型 */
    private static final int TRANSFER_TYPE_INBOUND = 1;
    private static final int TRANSFER_TYPE_OUTBOUND = 2;
    private static final int TRANSFER_TYPE_TRANSFER = 3;
    private static final int TRANSFER_TYPE_LOSS = 4;

    private final LotMapper lotMapper;
    private final LotTransferMapper lotTransferMapper;
    private final SkuMapper skuMapper;

    public LotQueryService(LotMapper lotMapper, LotTransferMapper lotTransferMapper,
                           SkuMapper skuMapper) {
        this.lotMapper = lotMapper;
        this.lotTransferMapper = lotTransferMapper;
        this.skuMapper = skuMapper;
    }

    public IPage<LotResponse> list(Long skuId, String tempZone, String status, String lotNo,
                                  int page, int size) {
        LambdaQueryWrapper<LotPO> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) {
            wrapper.eq(LotPO::getSkuId, skuId);
        }
        if (tempZone != null && !tempZone.isBlank()) {
            wrapper.eq(LotPO::getTempZone, tempZone);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(LotPO::getStatus, status);
        }
        if (lotNo != null && !lotNo.isBlank()) {
            wrapper.like(LotPO::getLotNo, lotNo);
        }
        wrapper.orderByDesc(LotPO::getLotNo);

        Page<LotPO> poPage = lotMapper.selectPage(new Page<>(page, size), wrapper);
        // 批量查 SKU 名（一次 IN 查询），主数据 → 交易数据关联展示
        Map<Long, String> skuNames = loadSkuNames(poPage.getRecords());
        return poPage.convert(po -> LotResponse.fromPO(po, skuNames.get(po.getSkuId())));
    }

    public LotResponse getDetail(String lotNo) {
        LotPO po = lotMapper.selectById(lotNo);
        if (po == null) {
            throw new NotFoundException("批次 " + lotNo + " 不存在");
        }
        Map<Long, String> skuNames = loadSkuNames(List.of(po));
        return LotResponse.fromPO(po, skuNames.get(po.getSkuId()));
    }

    /**
     * 批量加载 SKU 名（skuId → 名称）。SKU 已删除时返回 null，前端显示 skuId 兜底。
     */
    private Map<Long, String> loadSkuNames(List<LotPO> lots) {
        Set<Long> skuIds = lots.stream()
                .map(LotPO::getSkuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        return skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(SkuPO::getId, SkuPO::getName, (a, b) -> a));
    }

    /**
     * 查询批次的出入库记录（读 t_lot_transfer 业务流水表）
     * <p>与 Outbox 表（t_lot_event）职责分离：业务查询读流水表，消息投递读 Outbox。</p>
     */
    public List<LotTransferResponse> listTransfers(String lotNo) {
        // 先校验批次存在
        LotPO lot = lotMapper.selectById(lotNo);
        if (lot == null) {
            throw new NotFoundException("批次 " + lotNo + " 不存在");
        }

        LambdaQueryWrapper<LotTransferPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LotTransferPO::getLotNo, lotNo)
               .orderByAsc(LotTransferPO::getId);

        return lotTransferMapper.selectList(wrapper).stream()
                .map(LotQueryService::toTransferResponse)
                .toList();
    }

    private static LotTransferResponse toTransferResponse(LotTransferPO po) {
        return new LotTransferResponse(
                po.getId(),
                po.getLotNo(),
                mapTransferType(po.getTransferType()),
                po.getQty(),
                po.getFromLocation(),
                po.getToLocation(),
                po.getOperator(),
                po.getCreatedAt() != null ? po.getCreatedAt().toString() : null
        );
    }

    /** 流转类型精确映射（switch 而非字符串匹配） */
    private static String mapTransferType(Integer transferType) {
        if (transferType == null) {
            return "TRANSFER";
        }
        return switch (transferType) {
            case TRANSFER_TYPE_INBOUND -> "INBOUND";
            case TRANSFER_TYPE_OUTBOUND -> "OUTBOUND";
            case TRANSFER_TYPE_LOSS -> "LOSS";
            default -> "TRANSFER";
        };
    }
}
