package com.nongpi.fulfillment.common.domain;

/**
 * 温区枚举 — 农批履约中台
 *
 * <p>定义商品存储/运输所需的温度区间，用于批次（Lot）的仓储条件和车辆调度约束。</p>
 */
public enum TempZone {

    /** 冷冻：≤ -18°C — 肉类、海鲜、速冻品 */
    FREEZE,

    /** 冷藏：0°C ~ 8°C — 奶制品、鲜肉、蔬菜 */
    FRESH,

    /** 常温：无需温控 — 干货、包装食品、日用品 */
    NORMAL
}