package com.nongpi.fulfillment.inventory.domain;

import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.common.exception.BusinessException;
import com.nongpi.fulfillment.common.exception.NotFoundException;
import com.nongpi.fulfillment.common.exception.ValidationException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Inventory 聚合根 — SKU + 温区维度的库存汇总
 *
 * <p>管理库存的总量、冻结量、可用量。通过乐观锁（version）保证并发安全。</p>
 *
 * <pre>
 * 可用量 = totalQty - frozenQty
 * </pre>
 */
public class Inventory {

    private Long id;
    private final Long skuId;
    private final TempZone tempZone;
    private BigDecimal totalQty;
    private BigDecimal frozenQty;
    private int version;

    // ── 构造器 ──────────────────────────────────────────────

    private Inventory(Long skuId, TempZone tempZone, BigDecimal totalQty) {
        this.skuId = skuId;
        this.tempZone = tempZone;
        this.totalQty = totalQty;
        this.frozenQty = BigDecimal.ZERO;
        this.version = 0;
    }

    // ── 静态工厂方法 ────────────────────────────────────────

    /**
     * 创建新库存记录
     *
     * @param skuId       SKU 标识
     * @param tempZone    温区
     * @param initialQty  初始库存数量
     * @return Inventory 聚合根
     */
    public static Inventory create(Long skuId, TempZone tempZone, BigDecimal initialQty) {
        Objects.requireNonNull(skuId, "SKU ID 不能为空");
        Objects.requireNonNull(tempZone, "温区不能为空");
        Objects.requireNonNull(initialQty, "初始数量不能为空");

        if (initialQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("初始数量不能为负数");
        }

        return new Inventory(skuId, tempZone, initialQty);
    }

    /**
     * 从持久化数据还原聚合根（不触发事件）
     */
    public static Inventory reconstitute(Long id, Long skuId, TempZone tempZone,
                                         BigDecimal totalQty, BigDecimal frozenQty,
                                         int version) {
        Inventory inv = new Inventory(skuId, tempZone, totalQty);
        inv.id = id;
        inv.frozenQty = frozenQty;
        inv.version = version;
        return inv;
    }

    /**
     * 创建或调整库存 — "不存在则创建" 业务规则下沉到领域层
     *
     * <p>应用层调用此方法时传入 {@code isIncrease} 即可，
     * 不需要在 AppService 中决策"不存在时的处理"。</p>
     *
     * @param skuId      SKU 标识
     * @param tempZone   温区
     * @param qty        数量（必须 ≥ 0）
     * @param isIncrease true 表示增加（允许创建新记录）；false 表示扣减（不存在则抛 NotFoundException）
     * @return 新建的库存聚合根（仅 isIncrease=true 时返回）
     * @throws NotFoundException 当 isIncrease=false 且库存不存在时
     */
    public static Inventory createOrAdjust(Long skuId, TempZone tempZone,
                                           BigDecimal qty, boolean isIncrease) {
        if (!isIncrease) {
            throw new NotFoundException("INVENTORY_NOT_FOUND",
                    "库存记录不存在，无法扣减：skuId=" + skuId + " tempZone=" + tempZone);
        }
        return create(skuId, tempZone, qty);
    }

    // ── 业务方法 ────────────────────────────────────────────

    /**
     * 增加库存总量（入库确认后调用）
     *
     * @param qty 增加数量（必须 > 0）
     */
    public void increaseQty(BigDecimal qty) {
        Objects.requireNonNull(qty, "数量不能为空");
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("增加数量必须大于 0");
        }
        this.totalQty = this.totalQty.add(qty);
    }

    /**
     * 减少库存总量（出库确认后调用），同时减少可用量
     *
     * @param qty 减少数量（必须 > 0，且不超过可用量）
     */
    public void decreaseQty(BigDecimal qty) {
        Objects.requireNonNull(qty, "数量不能为空");
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("减少数量必须大于 0");
        }
        BigDecimal available = getAvailableQty();
        if (qty.compareTo(available) > 0) {
            throw new BusinessException(422, "INSUFFICIENT_QTY",
                    "可用库存不足：需要 " + qty + "，可用 " + available);
        }
        this.totalQty = this.totalQty.subtract(qty);
    }

    /**
     * 冻结库存（订单占用，减少可用量）
     *
     * @param qty 冻结数量（必须 > 0，且不超过当前可用量）
     */
    public void freezeQty(BigDecimal qty) {
        Objects.requireNonNull(qty, "数量不能为空");
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("冻结数量必须大于 0");
        }
        BigDecimal available = getAvailableQty();
        if (qty.compareTo(available) > 0) {
            throw new BusinessException(422, "INSUFFICIENT_QTY",
                    "可用库存不足：需要冻结 " + qty + "，可用 " + available);
        }
        this.frozenQty = this.frozenQty.add(qty);
    }

    /**
     * 解冻库存（订单取消/释放，恢复可用量）
     *
     * @param qty 解冻数量（必须 > 0，且不超过当前冻结量）
     */
    public void unfreezeQty(BigDecimal qty) {
        Objects.requireNonNull(qty, "数量不能为空");
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("解冻数量必须大于 0");
        }
        if (qty.compareTo(this.frozenQty) > 0) {
            throw new BusinessException(422, "FROZEN_QTY_EXCEEDED",
                    "解冻数量超过冻结量：需要解冻 " + qty + "，已冻结 " + this.frozenQty);
        }
        this.frozenQty = this.frozenQty.subtract(qty);
    }

    // ── 派生属性 ────────────────────────────────────────────

    /**
     * 可用量 = 总量 - 冻结量
     */
    public BigDecimal getAvailableQty() {
        return this.totalQty.subtract(this.frozenQty);
    }

    // ── Getter 方法 ─────────────────────────────────────────

    public Long getId() {
        return id;
    }

    /**
     * 供仓储层在 insert 后回填自增主键使用，业务代码不应调用。
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已存在，不允许重复赋值");
        }
        this.id = id;
    }

    public Long getSkuId() {
        return skuId;
    }

    public TempZone getTempZone() {
        return tempZone;
    }

    public BigDecimal getTotalQty() {
        return totalQty;
    }

    public BigDecimal getFrozenQty() {
        return frozenQty;
    }

    public int getVersion() {
        return version;
    }
}
