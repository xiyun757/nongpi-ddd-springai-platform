package com.nongpi.fulfillment.lot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Lot 表 MyBatis-Plus Mapper
 *
 * <p>BaseMapper 提供基础 CRUD：insert、selectById、updateById 等。</p>
 */
@Mapper
public interface LotMapper extends BaseMapper<LotPO> {
}