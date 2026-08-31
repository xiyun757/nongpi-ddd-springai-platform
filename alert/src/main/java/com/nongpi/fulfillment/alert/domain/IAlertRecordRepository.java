package com.nongpi.fulfillment.alert.domain;

import java.util.List;
import java.util.Optional;

/**
 * 预警记录仓储接口 — 领域层定义
 */
public interface IAlertRecordRepository {

    /**
     * 根据 ID 查询记录
     */
    Optional<AlertRecord> findById(Long id);

    /**
     * 查询指定批次未处理的预警记录
     */
    List<AlertRecord> findUnhandledByLotNo(String lotNo);

    /**
     * 保存新预警记录
     */
    void save(AlertRecord record);

    /**
     * 更新预警记录
     */
    void update(AlertRecord record);
}
