package com.nongpi.fulfillment.lot.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.fulfillment.lot.domain.*;
import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.OptimisticLockException;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.lot.infrastructure.mapper.LotEventMapper;
import com.nongpi.fulfillment.lot.infrastructure.mapper.LotMapper;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotEventPO;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotPO;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lot 仓储实现 — infrastructure 层
 *
 * <p>使用 MyBatis-Plus BaseMapper 操作数据库。每个写操作在同一事务中
 * 同时保存/更新 Lot 数据，并将聚合根收集的领域事件批量写入 Outbox 表。</p>
 */
@Repository
@Primary
public class LotRepositoryImpl implements ILotRepository {

    private final LotMapper lotMapper;
    private final LotEventMapper lotEventMapper;
    private final ObjectMapper objectMapper;

    // 注入 Spring 托管的 ObjectMapper（含 JavaTimeModule），避免手动 new
    public LotRepositoryImpl(LotMapper lotMapper, LotEventMapper lotEventMapper,
                             ObjectMapper objectMapper) {
        this.lotMapper = lotMapper;
        this.lotEventMapper = lotEventMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Lot lot) {
        // 1. 保存 Lot 聚合根数据
        LotPO po = toPO(lot);
        lotMapper.insert(po);

        // 2. 获取聚合根收集的领域事件并写入 Outbox
        saveDomainEvents(lot);
    }

    @Override
    public Optional<Lot> getById(LotNo lotNo) {
        LotPO po = lotMapper.selectById(lotNo.value());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public List<Lot> findByExpireDateBefore(LocalDate date) {
        LambdaQueryWrapper<LotPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(LotPO::getExpireDate, date);
        List<LotPO> pos = lotMapper.selectList(wrapper);
        return pos.stream().map(this::toDomain).toList();
    }

    @Override
    public List<Lot> findByTempZoneAndExpireDateBefore(TempZone tempZone, LocalDate date) {
        LambdaQueryWrapper<LotPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LotPO::getTempZone, tempZone.name());
        wrapper.lt(LotPO::getExpireDate, date);
        wrapper.orderByAsc(LotPO::getExpireDate);
        List<LotPO> pos = lotMapper.selectList(wrapper);
        return pos.stream().map(this::toDomain).toList();
    }

    @Override
    public List<Lot> findByIds(List<LotNo> lotNos) {
        if (lotNos == null || lotNos.isEmpty()) {
            return List.of();
        }
        List<String> ids = lotNos.stream().map(LotNo::value).collect(Collectors.toList());
        LambdaQueryWrapper<LotPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(LotPO::getLotNo, ids);
        List<LotPO> pos = lotMapper.selectList(wrapper);
        return pos.stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Lot lot) {
        // 乐观锁更新 — 由 MyBatis-Plus @Version 拦截器自动生成
        // UPDATE t_lot SET ..., version = version + 1 WHERE lot_no = ? AND version = 旧值
        // ⚠ 注意:不能将 entity.version 置 null,拦截器遇到 null 版本号会直接跳过(乐观锁失效)
        // TOCTOU 防护:出库路径在 Redisson 分布式锁内调用,锁覆盖 read-modify-write 全程,
        // 并发同批次出库时 version 必不匹配 → 抛 OptimisticLockException 由上层重试或跳过。
        LotPO po = toPO(lot);
        int rows = lotMapper.updateById(po);
        if (rows == 0) {
            throw new OptimisticLockException(
                    "批次 " + lot.getLotNo().value() + " 已被其他操作修改，请重试");
        }

        // 将 DB 递增后的最新版本号回写聚合根，保持内存状态与持久化一致
        lot.syncVersion(po.getVersion());

        // 将新产生的领域事件写入 Outbox
        saveDomainEvents(lot);
    }

    // ── 内部辅助方法 ────────────────────────────────────────────

    /**
     * 将聚合根中的未处理领域事件批量写入 t_lot_event Outbox 表。
     */
    private void saveDomainEvents(Lot lot) {
        List<DomainEvent> events = lot.getDomainEvents();
        if (events == null || events.isEmpty()) {
            return;
        }

        List<LotEventPO> eventPOs = new ArrayList<>(events.size());
        for (DomainEvent event : events) {
            LotEventPO eventPO = new LotEventPO();
            eventPO.setAggregateId(lot.getLotNo().value());
            eventPO.setEventType(event.getClass().getName());
            eventPO.setPayload(serializeEvent(event));
            eventPO.setPublished(0);
            eventPOs.add(eventPO);
        }

        // 逐条 insert。单次操作事件数通常仅 1 条，批量 insert 无收益。
        // 如需批量，引入 MyBatis-Plus IService 即可，当前不值得增加 Service 层复杂度。
        for (LotEventPO ep : eventPOs) {
            lotEventMapper.insert(ep);
        }

        // 清空聚合根中已收集的事件（避免重复保存）
        lot.clearDomainEvents();
    }

    private String serializeEvent(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化领域事件失败: " + event.getClass().getName(), e);
        }
    }

    /**
     * Lot 领域对象 → LotPO 转换
     */
    private LotPO toPO(Lot lot) {
        LotPO po = new LotPO();
        BeanUtil.copyProperties(lot, po, "lotNo", "tempZone", "status");
        po.setLotNo(lot.getLotNo().value());
        po.setTempZone(lot.getTempZone().name());
        po.setStatus(lot.getStatus().name());
        return po;
    }

    /**
     * LotPO → Lot 领域对象转换（不含领域事件——事件只在写操作时产生）
     */
    private Lot toDomain(LotPO po) {
        return Lot.reconstitute(
                LotNo.fromString(po.getLotNo()),
                po.getSkuId(),
                TempZone.valueOf(po.getTempZone()),
                po.getProduceDate(),
                po.getExpireDate(),
                po.getInitialQty(),
                po.getRemainingQty(),
                LotStatus.valueOf(po.getStatus()),
                po.getSupplierId(),
                po.getVersion()
        );
    }
}