package com.nongpi.fulfillment.common.domain;

/**
 * 批次状态枚举 — 农批履约中台
 *
 * <p>定义一批商品（Lot）在完整生命周期中的状态流转。</p>
 *
 * <pre>
 * 状态机：
 *   IN_STOCK ──┬─→ PARTIAL_OUT ──→ FULLY_OUT
 *              └─→ EXPIRED
 * </pre>
 */
public enum LotStatus {

    /** 在库 — 商品已入库，库存完整 */
    IN_STOCK,

    /** 部分出库 — 已出库一部分，但仍有剩余库存 */
    PARTIAL_OUT,

    /** 全部出库 — 批次所有库存已出库完成 */
    FULLY_OUT,

    /** 过期报废 — 超过保质期，已移入废品区待处理 */
    EXPIRED
}