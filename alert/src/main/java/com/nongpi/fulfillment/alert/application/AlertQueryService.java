package com.nongpi.fulfillment.alert.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nongpi.fulfillment.alert.api.dto.AlertRecordResponse;
import com.nongpi.fulfillment.alert.api.dto.AlertRuleResponse;
import com.nongpi.fulfillment.alert.infrastructure.mapper.AlertRecordMapper;
import com.nongpi.fulfillment.alert.infrastructure.mapper.AlertRuleMapper;
import com.nongpi.fulfillment.alert.infrastructure.persistence.AlertRecordPO;
import com.nongpi.fulfillment.alert.infrastructure.persistence.AlertRulePO;
import com.nongpi.fulfillment.common.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 预警查询服务 — 应用层
 * <p>负责预警记录列表、规则列表等读操作，Controller 不再直接注入 Mapper。</p>
 */
@Service
public class AlertQueryService {

    private final AlertRecordMapper alertRecordMapper;
    private final AlertRuleMapper alertRuleMapper;

    public AlertQueryService(AlertRecordMapper alertRecordMapper,
                             AlertRuleMapper alertRuleMapper) {
        this.alertRecordMapper = alertRecordMapper;
        this.alertRuleMapper = alertRuleMapper;
    }

    /**
     * 预警记录分页列表（支持 handled、alertLevel、lotNo、时间范围筛选）
     */
    public IPage<AlertRecordResponse> listRecords(Boolean handled, String alertLevel,
                                                   String lotNo, LocalDate startDate,
                                                   LocalDate endDate, int page, int size) {
        LambdaQueryWrapper<AlertRecordPO> wrapper = new LambdaQueryWrapper<>();
        if (handled != null) {
            wrapper.eq(AlertRecordPO::getHandled, handled);
        }
        if (alertLevel != null && !alertLevel.isBlank()) {
            wrapper.eq(AlertRecordPO::getAlertLevel, alertLevel);
        }
        if (lotNo != null && !lotNo.isBlank()) {
            wrapper.like(AlertRecordPO::getLotNo, lotNo);
        }
        if (startDate != null) {
            wrapper.ge(AlertRecordPO::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(AlertRecordPO::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(AlertRecordPO::getCreatedAt);

        Page<AlertRecordPO> poPage = alertRecordMapper.selectPage(new Page<>(page, size), wrapper);
        return poPage.convert(AlertRecordResponse::fromPO);
    }

    /**
     * 查询预警规则列表（支持 skuId / tempZone / enabled 筛选，按创建时间倒序）
     */
    public List<AlertRuleResponse> listRules(Long skuId, String tempZone, Boolean enabled) {
        LambdaQueryWrapper<AlertRulePO> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) {
            wrapper.eq(AlertRulePO::getSkuId, skuId);
        }
        if (tempZone != null && !tempZone.isBlank()) {
            wrapper.eq(AlertRulePO::getTempZone, tempZone);
        }
        if (enabled != null) {
            wrapper.eq(AlertRulePO::getEnabled, enabled);
        }
        wrapper.orderByDesc(AlertRulePO::getCreatedAt);
        return alertRuleMapper.selectList(wrapper).stream()
                .map(AlertQueryService::toRuleResponse)
                .toList();
    }

    /**
     * 根据 ID 查询单条规则（用于创建/切换后回填 createdAt）
     */
    public AlertRuleResponse getRuleById(Long id) {
        AlertRulePO po = alertRuleMapper.selectById(id);
        if (po == null) {
            throw new NotFoundException("预警规则 " + id + " 不存在");
        }
        return toRuleResponse(po);
    }

    private static AlertRuleResponse toRuleResponse(AlertRulePO po) {
        return new AlertRuleResponse(
                po.getId(),
                po.getSkuId(),
                po.getTempZone(),
                po.getThresholdDays(),
                po.getAlertLevel(),
                po.getEnabled() != null && po.getEnabled(),
                po.getCreatedAt() != null ? po.getCreatedAt().toString() : null
        );
    }
}
