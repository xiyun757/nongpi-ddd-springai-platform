package com.nongpi.fulfillment.lot.domain;

import com.nongpi.fulfillment.common.domain.TempZone;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 批次仓储接口 — 领域层定义
 *
 * <p>定义 Lot 聚合根的持久化契约。仓储实现位于 infrastructure 层。
 * 所有写操作（add / update）需在同一个事务内将聚合根收集的领域事件
 * 持久化到 t_lot_event Outbox 表。</p>
 */
public interface ILotRepository {

    /**
     * 保存新聚合根（含领域事件）。
     * 实现需：先保存 Lot 数据到 t_lot，再将聚合根中的领域事件
     * 批量插入 t_lot_event Outbox 表。
     */
    void add(Lot lot);

    /**
     * 根据批次号查询聚合根。
     *
     * @param lotNo 批次号值对象
     * @return Optional 包裹的 Lot 聚合根
     */
    Optional<Lot> getById(LotNo lotNo);

    /**
     * 查询过期日期早于指定日期的批次列表（FEFO 出库候选）。
     *
     * @param date 阈值日期
     * @return 符合条件的批次列表
     */
    List<Lot> findByExpireDateBefore(LocalDate date);

    /**
     * 查询指定温区且过期日期早于指定日期的批次列表。
     *
     * @param tempZone 温区
     * @param date     阈值日期
     * @return 符合条件的批次列表，按过期日期升序
     */
    List<Lot> findByTempZoneAndExpireDateBefore(TempZone tempZone, LocalDate date);

    /**
     * 按批次号批量查询 — 修复 FefoCache.getEarliest 的 N+1 问题
     *
     * @param lotNos 批次号值对象列表
     * @return 命中的批次列表（顺序不保证，调用方需自行排序）
     */
    List<Lot> findByIds(List<LotNo> lotNos);

    /**
     * 更新聚合根（含新产生的领域事件）。
     * 实现需：先更新 t_lot 数据，再将新产生的领域事件
     * 批量插入 t_lot_event Outbox 表。
     */
    void update(Lot lot);
}