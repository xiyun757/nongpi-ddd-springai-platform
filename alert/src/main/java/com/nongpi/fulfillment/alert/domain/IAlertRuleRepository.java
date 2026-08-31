package com.nongpi.fulfillment.alert.domain;

import java.util.List;
import java.util.Optional;

/**
 * 预警规则仓储接口 — 领域层定义
 */
public interface IAlertRuleRepository {

    /**
     * 根据 ID 查询规则
     */
    Optional<AlertRule> findById(Long id);

    /**
     * 查询所有已启用的规则
     */
    List<AlertRule> findAllEnabled();

    /**
     * 保存新规则
     */
    void save(AlertRule rule);

    /**
     * 更新规则
     */
    void update(AlertRule rule);

    /**
     * 根据 ID 删除规则
     */
    void deleteById(Long id);
}
