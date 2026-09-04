package com.nongpi.fulfillment.sku.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.sku.infrastructure.persistence.SkuPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * SKU 表 MyBatis-Plus Mapper
 */
@Mapper
public interface SkuMapper extends BaseMapper<SkuPO> {
}
