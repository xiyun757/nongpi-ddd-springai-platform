package com.nongpi.fulfillment.lot.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lot 持久化对象 — 对应 t_lot 表
 *
 * <p>PO 与领域对象 {@link com.nongpi.fulfillment.lot.domain.Lot} 分离，
 * 避免 JPA/MyBatis 注解污染领域层。</p>
 */
@Data
@NoArgsConstructor
@TableName("t_lot")
public class LotPO {

    @TableId
    private String lotNo;
    private Long skuId;
    private String tempZone;
    private LocalDate produceDate;
    private LocalDate expireDate;
    private BigDecimal initialQty;
    private BigDecimal remainingQty;
    private String status;
    private Long supplierId;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
