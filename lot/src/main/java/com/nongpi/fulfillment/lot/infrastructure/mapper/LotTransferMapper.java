package com.nongpi.fulfillment.lot.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotTransferPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 批次流转记录 Mapper — 对应 t_lot_transfer 表
 */
@Mapper
public interface LotTransferMapper extends BaseMapper<LotTransferPO> {
}
