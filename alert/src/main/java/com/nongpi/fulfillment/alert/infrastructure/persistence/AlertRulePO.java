package com.nongpi.fulfillment.alert.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AlertRule 持久化对象 — 对应 t_alert_rule 表
 */
@Data
@TableName("t_alert_rule")
public class AlertRulePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private String tempZone;

    private Integer thresholdDays;

    private String alertLevel;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
