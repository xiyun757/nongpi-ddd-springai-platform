package com.nongpi.fulfillment.sku.domain;

import com.nongpi.fulfillment.common.exception.ValidationException;

import java.util.Objects;

/**
 * SKU 聚合根 — 商品主数据
 *
 * <p>主数据（Master Data）：独立于业务动作存在，入库/出库只引用其 id。
 * 生命周期由 {@code Sku.createNew} 创建，创建后名称等字段可变。</p>
 */
public class Sku {

    private final Long id;
    private final String name;
    private final String spec;
    private final String unit;
    private final String barcode;

    private Sku(Long id, String name, String spec, String unit, String barcode) {
        this.id = id;
        this.name = name;
        this.spec = spec;
        this.unit = unit;
        this.barcode = barcode;
    }

    /**
     * 创建新商品（主数据）— 入参校验在此收敛
     */
    public static Sku createNew(String name, String spec, String unit, String barcode) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("商品名称不能为空");
        }
        if (unit == null || unit.isBlank()) {
            throw new ValidationException("计量单位不能为空");
        }
        return new Sku(null, name.trim(), trimToNull(spec), unit.trim(), trimToNull(barcode));
    }

    /**
     * 从持久化数据还原聚合根
     */
    public static Sku reconstitute(Long id, String name, String spec, String unit, String barcode) {
        return new Sku(id, name, spec, unit, barcode);
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    // ── getter（只读暴露，不提供 setter 保持不可变）──

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSpec() {
        return spec;
    }

    public String getUnit() {
        return unit;
    }

    public String getBarcode() {
        return barcode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Sku sku)) {
            return false;
        }
        return Objects.equals(id, sku.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
