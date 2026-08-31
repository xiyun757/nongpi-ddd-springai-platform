package com.nongpi.fulfillment.lot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotEventPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 领域事件 Outbox 表 MyBatis-Plus Mapper
 */
@Mapper
public interface LotEventMapper extends BaseMapper<LotEventPO> {
}