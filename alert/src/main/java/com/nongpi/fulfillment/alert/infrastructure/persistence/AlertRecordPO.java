package com.nongpi.fulfillment.alert.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AlertRecord 持久化对象 — 对应 t_alert_record 表
 */
@Data
@TableName("t_alert_record")
public class AlertRecordPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String lotNo;

    private Long alertRuleId;

    private String alertLevel;

    private String message;

    private Boolean handled;

    private String handler;

    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
