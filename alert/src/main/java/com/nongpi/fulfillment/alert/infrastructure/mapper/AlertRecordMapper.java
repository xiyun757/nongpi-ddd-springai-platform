package com.nongpi.fulfillment.alert.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.alert.infrastructure.persistence.AlertRecordPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AlertRecord MyBatis-Plus Mapper
 */
@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecordPO> {

    /**
     * 原子幂等插入：同一批次+同一规则+未处理时只插入一条
     * <p>用 INSERT ... SELECT ... WHERE NOT EXISTS 一条 SQL 完成"检查+插入"，
     * 避免先查后插的 TOCTOU 竞态。并发时由数据库行锁保证原子性。</p>
     *
     * @return 受影响行数：1=插入成功，0=已存在跳过
     */
    @Insert("""
            INSERT INTO t_alert_record (lot_no, alert_rule_id, alert_level, message, handled, created_at)
            SELECT #{lotNo}, #{alertRuleId}, #{alertLevel}, #{message}, 0, NOW()
            FROM DUAL
            WHERE NOT EXISTS (
                SELECT 1 FROM t_alert_record
                WHERE lot_no = #{lotNo}
                  AND alert_rule_id = #{alertRuleId}
                  AND handled = 0
            )
            """)
    int insertIfNotExists(@Param("lotNo") String lotNo,
                          @Param("alertRuleId") Long alertRuleId,
                          @Param("alertLevel") String alertLevel,
                          @Param("message") String message);
}
