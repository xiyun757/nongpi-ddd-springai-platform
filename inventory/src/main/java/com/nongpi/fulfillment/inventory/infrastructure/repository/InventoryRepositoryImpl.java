package com.nongpi.fulfillment.inventory.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongpi.fulfillment.common.domain.OptimisticLockException;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.inventory.domain.IInventoryRepository;
import com.nongpi.fulfillment.inventory.domain.Inventory;
import com.nongpi.fulfillment.inventory.infrastructure.mapper.InventoryMapper;
import com.nongpi.fulfillment.inventory.infrastructure.persistence.InventoryPO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Inventory 仓储实现 — infrastructure 层
 *
 * <p>使用 MyBatis-Plus BaseMapper 操作数据库。
 * 乐观锁通过 InventoryPO 的 @Version 注解由 MyBatis-Plus 自动控制。</p>
 */
@Repository
@Primary
public class InventoryRepositoryImpl implements IInventoryRepository {

    private final InventoryMapper inventoryMapper;

    public InventoryRepositoryImpl(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    @Override
    public Optional<Inventory> findBySkuAndZone(Long skuId, TempZone tempZone) {
        LambdaQueryWrapper<InventoryPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryPO::getSkuId, skuId)
               .eq(InventoryPO::getTempZone, tempZone.name());
        InventoryPO po = inventoryMapper.selectOne(wrapper);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public void save(Inventory inventory) {
        InventoryPO po = toPO(inventory);
        inventoryMapper.insert(po);
        inventory.assignId(po.getId());
    }

    @Override
    public void update(Inventory inventory) {
        InventoryPO po = toPO(inventory);
        int rows = inventoryMapper.updateById(po);
        if (rows == 0) {
            throw new OptimisticLockException(
                    "库存 " + inventory.getSkuId() + "/" + inventory.getTempZone()
                    + " 已被其他操作修改，请重试");
        }
    }

    // ── 内部转换方法 ────────────────────────────────────────

    private InventoryPO toPO(Inventory inv) {
        InventoryPO po = new InventoryPO();
        BeanUtil.copyProperties(inv, po, "tempZone");
        po.setTempZone(inv.getTempZone().name());
        return po;
    }

    private Inventory toDomain(InventoryPO po) {
        return Inventory.reconstitute(
                po.getId(),
                po.getSkuId(),
                TempZone.valueOf(po.getTempZone()),
                po.getTotalQty(),
                po.getFrozenQty(),
                po.getVersion()
        );
    }
}
