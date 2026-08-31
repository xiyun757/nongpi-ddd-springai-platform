USE nongpi;

-- 1. t_lot：批次主表
CREATE TABLE IF NOT EXISTS t_lot (
    lot_no VARCHAR(64) NOT NULL COMMENT '批次号',
    sku_id BIGINT NOT NULL COMMENT '商品SKU ID',
    temp_zone VARCHAR(16) NOT NULL COMMENT '温区：FREEZE/FRESH/NORMAL',
    produce_date DATE NOT NULL COMMENT '生产日期',
    expire_date DATE NOT NULL COMMENT '过期日期',
    initial_qty DECIMAL(10,2) NOT NULL COMMENT '初始数量',
    remaining_qty DECIMAL(10,2) NOT NULL COMMENT '剩余数量',
    status VARCHAR(16) NOT NULL DEFAULT 'IN_STOCK' COMMENT '批次状态：IN_STOCK/PARTIAL_OUT/FULLY_OUT/EXPIRED',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (lot_no),
    INDEX idx_lot_fefo (temp_zone, status, expire_date) COMMENT 'FEFO先进先出索引：温区+状态+过期时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批次主表';

-- 2. t_lot_transfer：批次流转记录
CREATE TABLE IF NOT EXISTS t_lot_transfer (
    id BIGINT AUTO_INCREMENT NOT NULL COMMENT '主键ID',
    transfer_no VARCHAR(64) NOT NULL COMMENT '流转单号',
    lot_no VARCHAR(64) NOT NULL COMMENT '批次号',
    transfer_type TINYINT NOT NULL COMMENT '流转类型：1=入库 2=出库 3=转库 4=报损',
    qty DECIMAL(10,2) NOT NULL COMMENT '数量',
    from_location VARCHAR(64) DEFAULT NULL COMMENT '来源库位',
    to_location VARCHAR(64) DEFAULT NULL COMMENT '目标库位',
    operator VARCHAR(32) DEFAULT NULL COMMENT '操作人',
    remark VARCHAR(256) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_transfer_lotno (lot_no) COMMENT '按批次号查询流转记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批次流转记录表';

-- 3. t_inventory：库存汇总表
CREATE TABLE IF NOT EXISTS t_inventory (
    id BIGINT AUTO_INCREMENT NOT NULL COMMENT '主键ID',
    sku_id BIGINT NOT NULL COMMENT '商品SKU ID',
    temp_zone VARCHAR(16) NOT NULL COMMENT '温区',
    total_qty DECIMAL(10,2) NOT NULL COMMENT '总库存数量',
    frozen_qty DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '冻结数量',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_sku_zone (sku_id, temp_zone) COMMENT '同一商品同一温区仅一条库存记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存汇总表';

-- 4. t_alert_rule：预警规则表
CREATE TABLE IF NOT EXISTS t_alert_rule (
    id BIGINT AUTO_INCREMENT NOT NULL COMMENT '主键ID',
    sku_id BIGINT DEFAULT NULL COMMENT '商品SKU ID（NULL表示全局规则）',
    temp_zone VARCHAR(16) DEFAULT NULL COMMENT '温区（NULL表示所有温区）',
    threshold_days INT NOT NULL COMMENT '过期警戒天数',
    alert_level VARCHAR(16) NOT NULL COMMENT '预警级别：INFO/WARNING/CRITICAL',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用 0=禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警规则表';

-- 5. t_alert_record：预警记录表
CREATE TABLE IF NOT EXISTS t_alert_record (
    id BIGINT AUTO_INCREMENT NOT NULL COMMENT '主键ID',
    lot_no VARCHAR(64) NOT NULL COMMENT '批次号',
    alert_rule_id BIGINT DEFAULT NULL COMMENT '关联预警规则ID',
    alert_level VARCHAR(16) NOT NULL COMMENT '预警级别：INFO/WARNING/CRITICAL',
    message VARCHAR(512) NOT NULL COMMENT '预警消息',
    handled TINYINT NOT NULL DEFAULT 0 COMMENT '是否已处理：0=未处理 1=已处理',
    handler VARCHAR(32) DEFAULT NULL COMMENT '处理人',
    handled_at DATETIME DEFAULT NULL COMMENT '处理时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警记录表';

-- 6. t_lot_event：领域事件 Outbox 表
CREATE TABLE IF NOT EXISTS t_lot_event (
    id BIGINT AUTO_INCREMENT NOT NULL COMMENT '主键ID',
    aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合根ID（批次号）',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型全限定名',
    payload JSON NOT NULL COMMENT '事件载荷（JSON格式）',
    published TINYINT NOT NULL DEFAULT 0 COMMENT '是否已发布：0=未发布 1=已发布',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态机：PENDING→IN_FLIGHT→PUBLISHED/DEAD',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数，≥3次标记DEAD进死信队列',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    in_flight_at DATETIME DEFAULT NULL COMMENT '进入IN_FLIGHT时间，用于超时恢复判断',
    PRIMARY KEY (id),
    INDEX idx_event_status (status) COMMENT '按投递状态扫描待发布事件',
    INDEX idx_inflight_recover (status, in_flight_at) COMMENT 'IN_FLIGHT超时恢复扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件Outbox表';

-- 7. t_mq_consume_log：MQ消费幂等日志表
CREATE TABLE IF NOT EXISTS t_mq_consume_log (
    message_id VARCHAR(64) NOT NULL COMMENT '消息唯一ID（等于领域事件ID）',
    consumer VARCHAR(64) NOT NULL COMMENT '消费者名称',
    consumed_at DATETIME NOT NULL COMMENT '消费时间',
    PRIMARY KEY (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消费幂等日志表';