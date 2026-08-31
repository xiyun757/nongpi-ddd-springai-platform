package com.nongpi.fulfillment.inventory.domain;

import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.common.exception.BusinessException;
import com.nongpi.fulfillment.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inventory 聚合根单元测试
 * <p>
 * 覆盖：创建/增减库存/冻结解冻/可用量计算
 * </p>
 */
class InventoryTest {

    @Nested
    @DisplayName("create — 创建新库存")
    class CreateTest {

        @Test
        @DisplayName("正常创建应正确初始化")
        void shouldCreate() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("1000"));
            assertAll(
                    () -> assertEquals(1001L, inv.getSkuId()),
                    () -> assertEquals(TempZone.FRESH, inv.getTempZone()),
                    () -> assertEquals(0, new BigDecimal("1000").compareTo(inv.getTotalQty())),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(inv.getFrozenQty())),
                    () -> assertEquals(0, new BigDecimal("1000").compareTo(inv.getAvailableQty())),
                    () -> assertEquals(0, inv.getVersion())
            );
        }

        @Test
        @DisplayName("负数初始数量应抛异常")
        void shouldThrowOnNegativeInitialQty() {
            assertThrows(ValidationException.class,
                    () -> Inventory.create(1001L, TempZone.FRESH, new BigDecimal("-100")));
        }
    }

    @Nested
    @DisplayName("increaseQty — 增加库存")
    class IncreaseQtyTest {

        @Test
        @DisplayName("增加后 totalQty 和 availableQty 同步增加")
        void shouldIncrease() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("1000"));
            inv.increaseQty(new BigDecimal("500"));
            assertAll(
                    () -> assertEquals(0, new BigDecimal("1500").compareTo(inv.getTotalQty())),
                    () -> assertEquals(0, new BigDecimal("1500").compareTo(inv.getAvailableQty()))
            );
        }

        @Test
        @DisplayName("增加 0 或负数应抛异常")
        void shouldThrowOnNonPositive() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("1000"));
            assertThrows(ValidationException.class, () -> inv.increaseQty(BigDecimal.ZERO));
            assertThrows(ValidationException.class, () -> inv.increaseQty(new BigDecimal("-10")));
        }
    }

    @Nested
    @DisplayName("decreaseQty — 减少库存")
    class DecreaseQtyTest {

        @Test
        @DisplayName("正常减少应更新 totalQty 和 availableQty")
        void shouldDecrease() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("1000"));
            inv.decreaseQty(new BigDecimal("300"));
            assertAll(
                    () -> assertEquals(0, new BigDecimal("700").compareTo(inv.getTotalQty())),
                    () -> assertEquals(0, new BigDecimal("700").compareTo(inv.getAvailableQty()))
            );
        }

        @Test
        @DisplayName("减少量超过可用量应抛异常")
        void shouldThrowWhenExceedsAvailable() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("100"));
            assertThrows(BusinessException.class,
                    () -> inv.decreaseQty(new BigDecimal("200")));
        }

        @Test
        @DisplayName("冻结后减少量不应超过可用量（总量 - 冻结量）")
        void shouldRespectFrozenQty() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("100"));
            inv.freezeQty(new BigDecimal("40"));
            assertEquals(0, new BigDecimal("60").compareTo(inv.getAvailableQty()));
            // 应允许扣减 60
            inv.decreaseQty(new BigDecimal("60"));
            assertEquals(0, new BigDecimal("40").compareTo(inv.getTotalQty()));
            // 超过可用量（60→递减后可用=0）应抛
            Inventory inv2 = Inventory.create(1002L, TempZone.FRESH, new BigDecimal("100"));
            inv2.freezeQty(new BigDecimal("40"));
            assertThrows(BusinessException.class,
                    () -> inv2.decreaseQty(new BigDecimal("70")));
        }
    }

    @Nested
    @DisplayName("freezeQty / unfreezeQty — 冻结解冻")
    class FreezeTest {

        @Test
        @DisplayName("冻结后 availableQty 减少")
        void shouldFreeze() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("1000"));
            inv.freezeQty(new BigDecimal("200"));
            assertAll(
                    () -> assertEquals(0, new BigDecimal("1000").compareTo(inv.getTotalQty())),
                    () -> assertEquals(0, new BigDecimal("200").compareTo(inv.getFrozenQty())),
                    () -> assertEquals(0, new BigDecimal("800").compareTo(inv.getAvailableQty()))
            );
        }

        @Test
        @DisplayName("冻结量不能超过可用量")
        void shouldThrowWhenFreezeExceedsAvailable() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("100"));
            assertThrows(BusinessException.class,
                    () -> inv.freezeQty(new BigDecimal("200")));
        }

        @Test
        @DisplayName("解冻后 availableQty 恢复")
        void shouldUnfreeze() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("1000"));
            inv.freezeQty(new BigDecimal("300"));
            inv.unfreezeQty(new BigDecimal("100"));
            assertAll(
                    () -> assertEquals(0, new BigDecimal("200").compareTo(inv.getFrozenQty())),
                    () -> assertEquals(0, new BigDecimal("800").compareTo(inv.getAvailableQty()))
            );
        }

        @Test
        @DisplayName("解冻量不能超过冻结量")
        void shouldThrowWhenUnfreezeExceedsFrozen() {
            Inventory inv = Inventory.create(1001L, TempZone.FRESH, new BigDecimal("1000"));
            inv.freezeQty(new BigDecimal("100"));
            assertThrows(BusinessException.class,
                    () -> inv.unfreezeQty(new BigDecimal("200")));
        }
    }
}