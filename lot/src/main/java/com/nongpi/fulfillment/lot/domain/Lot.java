package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.common.exception.ValidationException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lot 聚合根 — 农批履约中台的核心聚合
 *
 * <p>管理批次的完整生命周期：入库（createNew）→ 出库（outbound）。
 * 所有业务规则在聚合根内部校验。领域事件通过 List 收集，
 * 由仓储在 save 时持久化到 Outbox 表。</p>
 */
@Getter
public class Lot {

    private final LotNo lotNo;
    private final Long skuId;
    private final TempZone tempZone;
    private final LocalDate produceDate;
    private final LocalDate expireDate;
    private final BigDecimal initialQty;
    private final Long supplierId;

    private BigDecimal remainingQty;
    private LotStatus status;
    private int version;
    private final List<DomainEvent> domainEvents;

    // ── 构造器 ──────────────────────────────────────────────

    private Lot(LotNo lotNo, Long skuId, TempZone tempZone,
                LocalDate produceDate, LocalDate expireDate,
                BigDecimal initialQty, Long supplierId) {
        this.lotNo = lotNo;
        this.skuId = skuId;
        this.tempZone = tempZone;
        this.produceDate = produceDate;
        this.expireDate = expireDate;
        this.initialQty = initialQty;
        this.remainingQty = initialQty;
        this.supplierId = supplierId;
        this.status = LotStatus.IN_STOCK;
        this.domainEvents = new ArrayList<>();
    }

    /**
     * 静态工厂方法 — 创建新批次并触发入库事件
     */
    public static Lot createNew(LotNo lotNo, Long skuId, TempZone tempZone,
                                LocalDate produceDate, LocalDate expireDate,
                                BigDecimal initialQty, Long supplierId) {
        Objects.requireNonNull(lotNo, "批次号不能为空");
        Objects.requireNonNull(skuId, "SKU ID 不能为空");
        Objects.requireNonNull(tempZone, "温区不能为空");
        Objects.requireNonNull(produceDate, "生产日期不能为空");
        Objects.requireNonNull(expireDate, "过期日期不能为空");
        Objects.requireNonNull(initialQty, "初始数量不能为空");
        Objects.requireNonNull(supplierId, "供应商 ID 不能为空");

        if (produceDate.isAfter(expireDate)) {
            throw new ValidationException("生产日期不能晚于过期日期");
        }
        if (initialQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("初始数量必须大于 0");
        }

        Lot lot = new Lot(lotNo, skuId, tempZone, produceDate, expireDate, initialQty, supplierId);

        // 触发入库事件
        lot.addDomainEvent(new LotInboundEvent(
                lotNo,
                initialQty,
                supplierId,
                tempZone,
                expireDate
        ));

        return lot;
    }

    /**
     * 静态工厂方法 — 从持久化数据还原聚合根（不触发事件）
     */
    public static Lot reconstitute(LotNo lotNo, Long skuId, TempZone tempZone,
                                   LocalDate produceDate, LocalDate expireDate,
                                   BigDecimal initialQty, BigDecimal remainingQty,
                                   LotStatus status, Long supplierId, int version) {
        Objects.requireNonNull(lotNo, "批次号不能为空");
        Lot lot = new Lot(lotNo, skuId, tempZone, produceDate, expireDate, initialQty, supplierId);
        lot.remainingQty = remainingQty;
        lot.status = status;
        lot.version = version;
        return lot;
    }

    // ── 业务方法 ────────────────────────────────────────────

    /**
     * 出库前置校验 — 三点断言
     *
     * @param qty         出库数量
     * @param targetZone  目标温区
     * @throws TempZoneMismatchException 温区不匹配
     * @throws InsufficientQtyException  库存不足
     * @throws ExpiredException          批次已过期
     */
    public void canOutbound(BigDecimal qty, TempZone targetZone) {
        // 断言 1：温区匹配
        if (this.tempZone != targetZone) {
            throw new TempZoneMismatchException(this.tempZone, targetZone);
        }

        // 断言 2：库存充足
        if (qty.compareTo(this.remainingQty) > 0) {
            throw new InsufficientQtyException(qty, this.remainingQty);
        }

        // 断言 3：未过期
        if (java.time.LocalDate.now().isAfter(this.expireDate)) {
            throw new ExpiredException(this.expireDate);
        }
    }

    /**
     * 执行出库操作
     *
     * @param qty         出库数量
     * @param toLocation  目标库位
     * @return 出库转移凭证 LotTransfer
     */
    public LotTransfer outbound(BigDecimal qty, String toLocation) {
        // 前置校验
        canOutbound(qty, this.tempZone);

        // 扣减库存
        this.remainingQty = this.remainingQty.subtract(qty);

        // 更新状态
        if (this.remainingQty.compareTo(BigDecimal.ZERO) == 0) {
            this.status = LotStatus.FULLY_OUT;
        } else {
            this.status = LotStatus.PARTIAL_OUT;
        }

        // 触发出库事件
        addDomainEvent(new LotOutboundedEvent(
                this.lotNo,
                qty,
                toLocation,
                this.tempZone,
                this.status == LotStatus.FULLY_OUT
        ));

        return new LotTransfer(
                this.lotNo,
                qty,
                toLocation,
                this.remainingQty
        );
    }

    /**
     * 判断是否即将过期（过期日期在 thresholdDays 天内）
     */
    public boolean isExpiring(long thresholdDays) {
        LocalDate threshold = LocalDate.now().plusDays(thresholdDays);
        return !this.expireDate.isAfter(threshold) && !this.expireDate.isBefore(LocalDate.now());
    }

    /**
     * 同步乐观锁版本号 — 由仓储在 DB 更新成功后调用
     *
     * <p>MyBatis-Plus 乐观锁拦截器会在 UPDATE 时自动执行
     * {@code version = version + 1},本方法将递增后的最新版本号
     * 回写聚合根,确保内存状态与持久化一致(后续再次 update 时使用正确版本)。</p>
     *
     * @param version DB 中递增后的最新版本号
     */
    public void syncVersion(int version) {
        this.version = version;
    }

    // ── 领域事件收集 ─────────────────────────────────────────

    public void addDomainEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    // ── Getter 方法（@Getter 自动生成，getDomainEvents 保留自定义封装）──

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}