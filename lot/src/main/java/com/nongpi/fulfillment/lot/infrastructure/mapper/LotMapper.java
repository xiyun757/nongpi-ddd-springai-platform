package com.nongpi.fulfillment.lot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Lot 表 MyBatis-Plus Mapper
 *
 * <p>BaseMapper 提供基础 CRUD：insert、selectById、updateById 等。</p>
 *
 * <p>出库 TOCTOU 防护：出库路径在 Redisson 分布式锁 {@code lock:lot:{lotNo}} 内执行，
 * 锁覆盖 read-modify-write 全程，并发同批次操作 version 必不匹配 → 由乐观锁兜底。</p>
 */
@Mapper
public interface LotMapper extends BaseMapper<LotPO> {
}