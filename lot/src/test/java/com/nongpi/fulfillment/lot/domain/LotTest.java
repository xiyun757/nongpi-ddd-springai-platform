package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lot 聚合根单元测试
 * <p>
 * 覆盖：创建/还原/出库三断言/状态机/过期判断
 * </p>
 */
class LotTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final LocalDate IN_7_DAYS = TODAY.plusDays(7);

    @Nested
    @DisplayName("createNew — 创建新批次")
    class CreateNewTest {

        @Test
        @DisplayName("正常创建应返回 IN_STOCK 状态且发出入库事件")
        void shouldCreateNewAndEmitInboundEvent() {
            LotNo lotNo = LotNo.fromString("LOT20260718001123");
            Lot lot = Lot.createNew(
                    lotNo, 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );

            assertAll(
                    () -> assertEquals(lotNo, lot.getLotNo()),
                    () -> assertEquals(LotStatus.IN_STOCK, lot.getStatus()),
                    () -> assertEquals(0, new BigDecimal("500").compareTo(lot.getInitialQty())),
                    () -> assertEquals(0, new BigDecimal("500").compareTo(lot.getRemainingQty())),
                    () -> assertEquals(1, lot.getDomainEvents().size()),
                    () -> assertInstanceOf(LotInboundEvent.class, lot.getDomainEvents().get(0))
            );
        }

        @Test
        @DisplayName("生产日期晚于过期日期应抛异常")
        void shouldThrowWhenProduceDateAfterExpireDate() {
            assertThrows(ValidationException.class, () ->
                    Lot.createNew(
                            LotNo.fromString("LOT20260718001124"), 1001L, TempZone.FRESH,
                            TOMORROW, YESTERDAY, new BigDecimal("500"), 1L
                    )
            );
        }

        @Test
        @DisplayName("初始数量 ≤ 0 应抛异常")
        void shouldThrowWhenInitialQtyNotPositive() {
            assertThrows(ValidationException.class, () ->
                    Lot.createNew(
                            LotNo.fromString("LOT20260718001125"), 1001L, TempZone.FRESH,
                            YESTERDAY, IN_7_DAYS, BigDecimal.ZERO, 1L
                    )
            );
            assertThrows(ValidationException.class, () ->
                    Lot.createNew(
                            LotNo.fromString("LOT20260718001126"), 1001L, TempZone.FRESH,
                            YESTERDAY, IN_7_DAYS, new BigDecimal("-10"), 1L
                    )
            );
        }

        @Test
        @DisplayName("null 参数应抛 NullPointerException")
        void shouldThrowWhenNullParam() {
            assertThrows(NullPointerException.class, () ->
                    Lot.createNew(null, 1001L, TempZone.FRESH, YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L));
        }
    }

    @Nested
    @DisplayName("reconstitute — 从持久化还原")
    class ReconstituteTest {

        @Test
        @DisplayName("还原后状态应匹配且无领域事件")
        void shouldReconstituteWithoutEvents() {
            Lot lot = Lot.reconstitute(
                    LotNo.fromString("LOT20260718001127"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS,
                    new BigDecimal("500"), new BigDecimal("200"),
                    LotStatus.PARTIAL_OUT, 1L, 3
            );

            assertAll(
                    () -> assertEquals(LotStatus.PARTIAL_OUT, lot.getStatus()),
                    () -> assertEquals(0, new BigDecimal("200").compareTo(lot.getRemainingQty())),
                    () -> assertEquals(3, lot.getVersion()),
                    () -> assertTrue(lot.getDomainEvents().isEmpty())
            );
        }
    }

    @Nested
    @DisplayName("canOutbound — 出库前置校验")
    class CanOutboundTest {

        private Lot createValidLot() {
            return Lot.createNew(
                    LotNo.fromString("LOT20260718001128"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
        }

        @Test
        @DisplayName("温区匹配、库存充足、未过期 → 通过")
        void shouldPassAllAssertions() {
            Lot lot = createValidLot();
            assertDoesNotThrow(() -> lot.canOutbound(new BigDecimal("300"), TempZone.FRESH));
        }

        @Test
        @DisplayName("温区不匹配 → TempZoneMismatchException")
        void shouldThrowOnZoneMismatch() {
            Lot lot = createValidLot();
            assertThrows(TempZoneMismatchException.class,
                    () -> lot.canOutbound(new BigDecimal("300"), TempZone.FREEZE));
        }

        @Test
        @DisplayName("库存不足 → InsufficientQtyException")
        void shouldThrowOnInsufficientQty() {
            Lot lot = createValidLot();
            assertThrows(InsufficientQtyException.class,
                    () -> lot.canOutbound(new BigDecimal("600"), TempZone.FRESH));
        }

        @Test
        @DisplayName("批次已过期 → ExpiredException")
        void shouldThrowOnExpired() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001129"), 1001L, TempZone.FRESH,
                    YESTERDAY, YESTERDAY, new BigDecimal("500"), 1L
            );
            assertThrows(ExpiredException.class,
                    () -> lot.canOutbound(new BigDecimal("10"), TempZone.FRESH));
        }

        @Test
        @DisplayName("qty = 0 不应抛异常但语义不正确（已知缺陷 B1）")
        void shouldAllowZeroQty() {
            Lot lot = createValidLot();
            assertDoesNotThrow(() -> lot.canOutbound(BigDecimal.ZERO, TempZone.FRESH));
        }
    }

    @Nested
    @DisplayName("outbound — 执行出库")
    class OutboundTest {

        @Test
        @DisplayName("部分出库后状态变为 PARTIAL_OUT")
        void partialOutboundShouldChangeStatus() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001130"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
            lot.outbound(new BigDecimal("200"), "A-01");
            assertAll(
                    () -> assertEquals(LotStatus.PARTIAL_OUT, lot.getStatus()),
                    () -> assertEquals(0, new BigDecimal("300").compareTo(lot.getRemainingQty())),
                    () -> assertTrue(lot.getDomainEvents().stream()
                            .anyMatch(e -> e instanceof LotOutboundedEvent))
            );
        }

        @Test
        @DisplayName("全部出库后状态变为 FULLY_OUT")
        void fullOutboundShouldChangeStatus() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001131"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
            lot.outbound(new BigDecimal("500"), "B-01");
            assertAll(
                    () -> assertEquals(LotStatus.FULLY_OUT, lot.getStatus()),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(lot.getRemainingQty()))
            );
        }

        @Test
        @DisplayName("出库返回正确的 LotTransfer")
        void shouldReturnLotTransfer() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001132"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
            LotTransfer transfer = lot.outbound(new BigDecimal("200"), "A-01");
            assertAll(
                    () -> assertEquals("LOT20260718001132", transfer.lotNo().value()),
                    () -> assertEquals(0, new BigDecimal("200").compareTo(transfer.qty())),
                    () -> assertEquals("A-01", transfer.toLocation()),
                    () -> assertEquals(0, new BigDecimal("300").compareTo(transfer.remainingQty()))
            );
        }
    }

    @Nested
    @DisplayName("isExpiring — 过期判断")
    class IsExpiringTest {

        @Test
        @DisplayName("过期日在阈值内应返回 true")
        void shouldReturnTrueWhenWithinThreshold() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001133"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
            assertTrue(lot.isExpiring(10));
        }

        @Test
        @DisplayName("过期日超出阈值应返回 false")
        void shouldReturnFalseWhenOutsideThreshold() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001134"), 1001L, TempZone.FRESH,
                    YESTERDAY, TODAY.plusDays(20), new BigDecimal("500"), 1L
            );
            assertFalse(lot.isExpiring(10));
        }

        @Test
        @DisplayName("已过期批次返回 false（阈值内但已过去）")
        void shouldReturnFalseForAlreadyExpired() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001135"), 1001L, TempZone.FRESH,
                    YESTERDAY, YESTERDAY, new BigDecimal("500"), 1L
            );
            assertFalse(lot.isExpiring(10));
        }
    }

    @Nested
    @DisplayName("状态机 — IN_STOCK → PARTIAL_OUT → FULLY_OUT")
    class StateMachineTest {

        @Test
        @DisplayName("IN_STOCK → 部分出库 → PARTIAL_OUT")
        void inStockToPartialOut() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001136"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
            assertEquals(LotStatus.IN_STOCK, lot.getStatus());
            lot.outbound(new BigDecimal("200"), "A-01");
            assertEquals(LotStatus.PARTIAL_OUT, lot.getStatus());
        }

        @Test
        @DisplayName("PARTIAL_OUT → 全部出库 → FULLY_OUT")
        void partialOutToFullyOut() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001137"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
            lot.outbound(new BigDecimal("200"), "A-01");
            assertEquals(LotStatus.PARTIAL_OUT, lot.getStatus());
            lot.outbound(new BigDecimal("300"), "B-01");
            assertEquals(LotStatus.FULLY_OUT, lot.getStatus());
        }

        @Test
        @DisplayName("IN_STOCK → 一次全部出库 → FULLY_OUT（跳过 PARTIAL_OUT）")
        void inStockToFullyOut() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001138"), 1001L, TempZone.FRESH,
                    YESTERDAY, IN_7_DAYS, new BigDecimal("500"), 1L
            );
            lot.outbound(new BigDecimal("500"), "A-01");
            assertEquals(LotStatus.FULLY_OUT, lot.getStatus());
        }

        @Test
        @DisplayName("EXPIRED 状态仅通过设置到达，domain 无此路径（缺失 EXPIRED 逻辑）")
        void expiredStateNotReachableViaDomain() {
            Lot lot = Lot.createNew(
                    LotNo.fromString("LOT20260718001139"), 1001L, TempZone.FRESH,
                    YESTERDAY, YESTERDAY, new BigDecimal("500"), 1L
            );
            // isExpiring 返回 false，但 outbound() 会抛 ExpiredException
            assertFalse(lot.isExpiring(10));
            assertThrows(ExpiredException.class, () -> lot.outbound(new BigDecimal("10"), "A-01"));
            // 状态仍然是 IN_STOCK，因为没有专门的 expire() 方法
            assertEquals(LotStatus.IN_STOCK, lot.getStatus());
            // 领域模型中缺失 "标记过期" 的业务方法，EXPIRED 状态不可达
            // 这是领域模型的缺口
        }
    }
}