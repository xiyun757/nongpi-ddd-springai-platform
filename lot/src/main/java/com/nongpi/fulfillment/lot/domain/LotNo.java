package com.nongpi.fulfillment.lot.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 批次号值对象 — 强类型标识
 *
 * <p>格式：LOT + yyyyMMdd + supplierCode + 4位序列</p>
 *
 * <p>不可变，通过 {@link #fromString(String)} 或 {@link #generate(String)} 创建。</p>
 *
 * @param value 原始批次号字符串
 */
public record LotNo(String value) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "LOT";

    /**
     * 从字符串创建 LotNo 值对象。
     *
     * @param value 批次号字符串
     * @return LotNo 实例
     * @throws IllegalArgumentException 如果 value 为 null 或空
     */
    public static LotNo fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LotNo must not be null or blank");
        }
        return new LotNo(value.trim());
    }

    /**
     * 生成一个新的批次号。
     * <p>规则：LOT + yyyyMMdd + supplierCode + 4位随机序列</p>
     * <p>4位随机数(9000种组合)，碰撞由 DB lot_no 主键约束兜底</p>
     *
     * @param supplierCode 供应商编码（用于嵌入批次号）
     * @return LotNo 实例
     * @throws IllegalArgumentException 如果 supplierCode 为 null 或空
     */
    public static LotNo generate(String supplierCode) {
        if (supplierCode == null || supplierCode.isBlank()) {
            throw new IllegalArgumentException("supplierCode must not be null or blank");
        }
        String datePart = LocalDate.now().format(DATE_FORMAT);
        int seq = ThreadLocalRandom.current().nextInt(1000, 10_000);
        String value = PREFIX + datePart + supplierCode.trim() + seq;
        return new LotNo(value);
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Java Bean 属性访问器，兼容 Jackson / Spring 序列化
     */
    public String getLotNo() {
        return value;
    }
}