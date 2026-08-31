package com.nongpi.fulfillment.lot.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 领域事件 Outbox 持久化对象 — 对应 t_lot_event 表
 *
 * <p>状态流转（{@link #status} 字段）：</p>
 * <ul>
 *   <li>PENDING — 待发送，定时中继扫描会拾取</li>
 *   <li>IN_FLIGHT — 已发送至 Broker，等待 Publisher Confirm 回调</li>
 *   <li>PUBLISHED — 收到 ack 确认，投递成功</li>
 *   <li>DEAD — 重试次数超限（{@code retry_count >= MAX_RETRY}），需人工介入</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@TableName("t_lot_event")
public class LotEventPO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_FLIGHT = "IN_FLIGHT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DEAD = "DEAD";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String aggregateId;
    private String eventType;
    private String payload;
    private Integer published;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 进入 IN_FLIGHT 的时间，用于超时恢复判断；PENDING/PUBLISHED/DEAD 时为 null */
    private LocalDateTime inFlightAt;
    private Integer retryCount;
    private String status;
}
