package com.nongpi.fulfillment.alert.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongpi.fulfillment.alert.domain.AlertRecord;
import com.nongpi.fulfillment.alert.domain.IAlertRecordRepository;
import com.nongpi.fulfillment.alert.infrastructure.mapper.AlertRecordMapper;
import com.nongpi.fulfillment.alert.infrastructure.persistence.AlertRecordPO;
import com.nongpi.fulfillment.common.domain.AlertLevel;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AlertRecord 仓储实现 — infrastructure 层
 */
@Repository
@Primary
public class AlertRecordRepositoryImpl implements IAlertRecordRepository {

    private final AlertRecordMapper alertRecordMapper;

    public AlertRecordRepositoryImpl(AlertRecordMapper alertRecordMapper) {
        this.alertRecordMapper = alertRecordMapper;
    }

    @Override
    public Optional<AlertRecord> findById(Long id) {
        AlertRecordPO po = alertRecordMapper.selectById(id);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public List<AlertRecord> findUnhandledByLotNo(String lotNo) {
        LambdaQueryWrapper<AlertRecordPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecordPO::getLotNo, lotNo)
               .eq(AlertRecordPO::getHandled, false)
               .orderByDesc(AlertRecordPO::getCreatedAt);
        return alertRecordMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void save(AlertRecord record) {
        AlertRecordPO po = toPO(record);
        alertRecordMapper.insert(po);
        record.assignId(po.getId());
    }

    @Override
    public boolean saveIfNotExists(AlertRecord record) {
        int rows = alertRecordMapper.insertIfNotExists(
                record.getLotNo(),
                record.getAlertRuleId(),
                record.getAlertLevel().name(),
                record.getMessage()
        );
        return rows > 0;
    }

    @Override
    public void update(AlertRecord record) {
        AlertRecordPO po = toPO(record);
        alertRecordMapper.updateById(po);
    }

    // ── 转换方法 ────────────────────────────────────────────

    private AlertRecordPO toPO(AlertRecord record) {
        AlertRecordPO po = new AlertRecordPO();
        BeanUtil.copyProperties(record, po, "alertLevel");
        po.setAlertLevel(record.getAlertLevel().name());
        return po;
    }

    private AlertRecord toDomain(AlertRecordPO po) {
        return AlertRecord.reconstitute(
                po.getId(),
                po.getLotNo(),
                po.getAlertRuleId(),
                AlertLevel.valueOf(po.getAlertLevel()),
                po.getMessage(),
                po.getHandled(),
                po.getHandler(),
                po.getHandledAt(),
                po.getCreatedAt()
        );
    }
}
