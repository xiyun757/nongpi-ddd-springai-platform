package com.nongpi.fulfillment.lot.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批次流转记录持久化对象 — 对应 t_lot_transfer 表
 *
 * <p>入库/出库/转库/报损操作的业务流水，与领域事件 Outbox 表（t_lot_event）职责分离：
 * 查询流转记录读本表，事件投递读 Outbox 表。</p>
 */
@Data
@TableName("t_lot_transfer")
public class LotTransferPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流转单号 */
    private String transferNo;

    /** 批次号 */
    private String lotNo;

    /** 流转类型：1=入库 2=出库 3=转库 4=报损 */
    private Integer transferType;

    /** 数量 */
    private BigDecimal qty;

    /** 来源库位 */
    private String fromLocation;

    /** 目标库位 */
    private String toLocation;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;
}
