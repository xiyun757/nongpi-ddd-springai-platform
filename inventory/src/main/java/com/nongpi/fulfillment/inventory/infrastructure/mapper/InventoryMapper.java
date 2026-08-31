package com.nongpi.fulfillment.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.inventory.infrastructure.persistence.InventoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * Inventory MyBatis-Plus Mapper
 *
 * <p>包含原子 UPDATE 方法，供批次业务事务内强一致同步库存使用。
 * 绕过 {@code @Retryable} + 读-改-写，一条 SQL 行锁串行化，无乐观锁冲突。</p>
 */
@Mapper
public interface InventoryMapper extends BaseMapper<InventoryPO> {

    /**
     * 原子增加库存（入库用）— 不存在则创建，已存在则累加
     *
     * @return 影响行数（1=新增或更新成功）
     */
    @Update("INSERT INTO t_inventory (sku_id, temp_zone, total_qty, frozen_qty, version) " +
            "VALUES (#{skuId}, #{tempZone}, #{qty}, 0, 0) " +
            "ON DUPLICATE KEY UPDATE total_qty = total_qty + VALUES(total_qty), version = version + 1")
    int insertOrIncrease(@Param("skuId") Long skuId,
                         @Param("tempZone") String tempZone,
                         @Param("qty") BigDecimal qty);

    /**
     * 原子扣减库存（出库/转库用）— DB 层校验可用量防超卖
     *
     * @return 影响行数（1=扣减成功，0=库存不足）
     */
    @Update("UPDATE t_inventory SET total_qty = total_qty - #{qty}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND temp_zone = #{tempZone} AND (total_qty - frozen_qty) >= #{qty}")
    int decreaseStock(@Param("skuId") Long skuId,
                      @Param("tempZone") String tempZone,
                      @Param("qty") BigDecimal qty);
}
