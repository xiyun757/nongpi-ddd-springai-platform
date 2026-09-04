package com.nongpi.fulfillment.sku.domain;

import java.util.List;
import java.util.Optional;

/**
 * SKU 仓储接口 — 领域层依赖倒置
 */
public interface ISkuRepository {

    Optional<Sku> findById(Long id);

    List<Sku> findAll();

    /** 判断商品是否存在（lot 入库时校验主数据闭环） */
    boolean existsById(Long id);

    void save(Sku sku);

    void deleteById(Long id);
}
