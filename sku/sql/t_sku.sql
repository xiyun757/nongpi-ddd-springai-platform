-- 商品主数据表（SKU）— 入库时校验 sku_id 必须存在于此表，实现主数据 → 交易数据闭环
-- 与 t_lot.sku_id / t_inventory.sku_id 关联
CREATE TABLE IF NOT EXISTS `t_sku` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    `name`       VARCHAR(128) NOT NULL COMMENT '商品名称',
    `spec`       VARCHAR(64)  DEFAULT NULL COMMENT '规格（如 500g/袋）',
    `unit`       VARCHAR(16)  NOT NULL DEFAULT 'kg' COMMENT '计量单位',
    `barcode`    VARCHAR(32)  DEFAULT NULL COMMENT '条形码',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_barcode` (`barcode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品主数据表（SKU）';

-- 种子数据：与 t_lot 已有批次关联（sku_id 1001~1030），保证历史数据自洽
INSERT INTO `t_sku` (`id`, `name`, `spec`, `unit`, `barcode`) VALUES
(1001, '冻虾仁', '500g/袋', 'kg', '6901001'),
(1002, '冰鲜三文鱼', '1kg/条', 'kg', '6901002'),
(1003, '有机白菜', '1颗', 'kg', '6901003'),
(1004, '冷冻鸡胸肉', '1kg/袋', 'kg', '6901004'),
(1005, '冷藏牛奶', '1L/盒', 'L', '6901005'),
(1006, '常温大米', '5kg/袋', 'kg', '6901006'),
(1007, '冷藏酸奶', '200g/杯', '杯', '6901007'),
(1008, '冷冻水饺', '1kg/袋', 'kg', '6901008'),
(1009, '鲜鸡蛋', '30枚/板', '枚', '6901009'),
(1010, '冷冻牛肉卷', '500g/盒', 'kg', '6901010'),
(1011, '冰鲜鲈鱼', '1条/约1kg', 'kg', '6901011'),
(1012, '有机番茄', '1kg/袋', 'kg', '6901012'),
(1013, '冷冻鱼丸', '500g/袋', 'kg', '6901013'),
(1014, '冷藏果汁', '1L/瓶', 'L', '6901014'),
(1015, '常温挂面', '1kg/袋', 'kg', '6901015'),
(1016, '冷冻羊排', '1kg/盒', 'kg', '6901016'),
(1017, '冰鲜基围虾', '500g/盒', 'kg', '6901017'),
(1018, '有机西兰花', '1颗', 'kg', '6901018'),
(1019, '冷冻虾滑', '200g/袋', 'kg', '6901019'),
(1020, '冷藏黄油', '200g/块', 'g', '6901020'),
(1021, '常温绿豆', '1kg/袋', 'kg', '6901021'),
(1022, '冷冻鸡翅', '1kg/袋', 'kg', '6901022'),
(1023, '冰鲜带鱼', '1条/约800g', 'kg', '6901023'),
(1024, '有机胡萝卜', '1kg/袋', 'kg', '6901024'),
(1025, '冷冻肥牛卷', '500g/盒', 'kg', '6901025'),
(1026, '冷藏芝士片', '200g/包', 'g', '6901026'),
(1027, '常温小米', '2kg/袋', 'kg', '6901027'),
(1028, '冷冻鱿鱼圈', '500g/袋', 'kg', '6901028'),
(1029, '冰鲜扇贝', '500g/盒', 'kg', '6901029'),
(1030, '有机黄瓜', '1kg/袋', 'kg', '6901030')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
