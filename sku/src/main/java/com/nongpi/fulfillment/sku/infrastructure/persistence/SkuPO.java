package com.nongpi.fulfillment.sku.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SKU 持久化对象 — 对应 t_sku 表
 *
 * <p>PO 与领域对象 {@link com.nongpi.fulfillment.sku.domain.Sku} 分离，
 * 避免 MyBatis 注解污染领域层（DDD 分层一致）。</p>
 */
@Data
@NoArgsConstructor
@TableName("t_sku")
public class SkuPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String spec;

    private String unit;

    private String barcode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
