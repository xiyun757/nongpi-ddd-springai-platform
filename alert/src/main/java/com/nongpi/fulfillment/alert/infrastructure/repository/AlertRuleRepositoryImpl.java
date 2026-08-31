package com.nongpi.fulfillment.alert.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongpi.fulfillment.alert.domain.AlertRule;
import com.nongpi.fulfillment.alert.domain.IAlertRuleRepository;
import com.nongpi.fulfillment.alert.infrastructure.mapper.AlertRuleMapper;
import com.nongpi.fulfillment.alert.infrastructure.persistence.AlertRulePO;
import com.nongpi.fulfillment.common.domain.AlertLevel;
import com.nongpi.fulfillment.common.domain.TempZone;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AlertRule 仓储实现 — infrastructure 层
 */
@Repository
@Primary
public class AlertRuleRepositoryImpl implements IAlertRuleRepository {

    private final AlertRuleMapper alertRuleMapper;

    public AlertRuleRepositoryImpl(AlertRuleMapper alertRuleMapper) {
        this.alertRuleMapper = alertRuleMapper;
    }

    @Override
    public Optional<AlertRule> findById(Long id) {
        AlertRulePO po = alertRuleMapper.selectById(id);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public List<AlertRule> findAllEnabled() {
        LambdaQueryWrapper<AlertRulePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRulePO::getEnabled, true);
        return alertRuleMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void save(AlertRule rule) {
        AlertRulePO po = toPO(rule);
        alertRuleMapper.insert(po);
        rule.assignId(po.getId());
    }

    @Override
    public void update(AlertRule rule) {
        AlertRulePO po = toPO(rule);
        alertRuleMapper.updateById(po);
    }

    @Override
    public void deleteById(Long id) {
        alertRuleMapper.deleteById(id);
    }

    // ── 转换方法 ────────────────────────────────────────────

    private AlertRulePO toPO(AlertRule rule) {
        AlertRulePO po = new AlertRulePO();
        BeanUtil.copyProperties(rule, po, "tempZone", "alertLevel");
        po.setTempZone(rule.getTempZone() != null ? rule.getTempZone().name() : null);
        po.setAlertLevel(rule.getAlertLevel().name());
        return po;
    }

    private AlertRule toDomain(AlertRulePO po) {
        return AlertRule.reconstitute(
                po.getId(),
                po.getSkuId(),
                po.getTempZone() != null ? TempZone.valueOf(po.getTempZone()) : null,
                po.getThresholdDays(),
                AlertLevel.valueOf(po.getAlertLevel()),
                po.getEnabled()
        );
    }
}
