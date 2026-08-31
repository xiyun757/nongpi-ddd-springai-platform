package com.nongpi.fulfillment.alert.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.alert.infrastructure.persistence.AlertRulePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AlertRule MyBatis-Plus Mapper
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRulePO> {
}
