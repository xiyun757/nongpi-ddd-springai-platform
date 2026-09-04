package com.nongpi.fulfillment.sku.application;

import com.nongpi.fulfillment.sku.domain.ISkuRepository;
import com.nongpi.fulfillment.sku.domain.Sku;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品查询服务 — 应用层只读操作
 */
@Service
public class SkuQueryService {

    private final ISkuRepository repository;

    public SkuQueryService(ISkuRepository repository) {
        this.repository = repository;
    }

    /**
     * 商品列表（按 id 升序）— 供前端下拉选择 / 批次列表展示商品名
     */
    public List<Sku> listAll() {
        return repository.findAll();
    }
}
