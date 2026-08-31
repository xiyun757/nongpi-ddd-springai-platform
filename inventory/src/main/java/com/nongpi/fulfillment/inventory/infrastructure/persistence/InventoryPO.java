package com.nongpi.fulfillment.inventory.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Inventory 持久化对象 — 对应 t_inventory 表
 */
@Data
@TableName("t_inventory")
public class InventoryPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private String tempZone;

    private BigDecimal totalQty;

    private BigDecimal frozenQty;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
