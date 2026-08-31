package com.nongpi.fulfillment.alert.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.alert.infrastructure.persistence.AlertRecordPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AlertRecord MyBatis-Plus Mapper
 */
@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecordPO> {
}
