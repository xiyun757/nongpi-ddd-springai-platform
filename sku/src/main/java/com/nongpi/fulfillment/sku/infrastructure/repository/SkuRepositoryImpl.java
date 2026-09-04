package com.nongpi.fulfillment.sku.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongpi.fulfillment.sku.domain.ISkuRepository;
import com.nongpi.fulfillment.sku.domain.Sku;
import com.nongpi.fulfillment.sku.infrastructure.mapper.SkuMapper;
import com.nongpi.fulfillment.sku.infrastructure.persistence.SkuPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SKU 仓储实现 — infrastructure 层
 */
@Repository
public class SkuRepositoryImpl implements ISkuRepository {

    private final SkuMapper skuMapper;

    public SkuRepositoryImpl(SkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    @Override
    public Optional<Sku> findById(Long id) {
        SkuPO po = skuMapper.selectById(id);
        return Optional.ofNullable(po).map(SkuRepositoryImpl::toDomain);
    }

    @Override
    public List<Sku> findAll() {
        LambdaQueryWrapper<SkuPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SkuPO::getId);
        return skuMapper.selectList(wrapper).stream().map(SkuRepositoryImpl::toDomain).toList();
    }

    @Override
    public boolean existsById(Long id) {
        return skuMapper.selectById(id) != null;
    }

    @Override
    public void save(Sku sku) {
        SkuPO po = toPO(sku);
        if (sku.getId() == null) {
            skuMapper.insert(po);
        } else {
            skuMapper.updateById(po);
        }
    }

    @Override
    public void deleteById(Long id) {
        skuMapper.deleteById(id);
    }

    // ── 转换 ──

    private static Sku toDomain(SkuPO po) {
        return Sku.reconstitute(po.getId(), po.getName(), po.getSpec(), po.getUnit(), po.getBarcode());
    }

    private static SkuPO toPO(Sku sku) {
        SkuPO po = new SkuPO();
        po.setId(sku.getId());
        po.setName(sku.getName());
        po.setSpec(sku.getSpec());
        po.setUnit(sku.getUnit());
        po.setBarcode(sku.getBarcode());
        return po;
    }
}
