package com.nongpi.fulfillment.inventory.application;

import com.nongpi.fulfillment.common.domain.OptimisticLockException;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.common.exception.NotFoundException;
import com.nongpi.fulfillment.inventory.domain.IInventoryRepository;
import com.nongpi.fulfillment.inventory.domain.Inventory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 库存应用服务 — 用例编排层
 *
 * <p>编排库存调整、冻结/解冻、查询等业务用例，事务边界在此层控制。</p>
 * <p>乐观锁冲突时自动重试 3 次，间隔 100ms。</p>
 */
@Service
@EnableRetry
public class InventoryAppService {

    private final IInventoryRepository repository;

    public InventoryAppService(IInventoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 调整库存（增加或扣减）
     *
     * <p>若 SKU+温区无库存记录且为增加操作，则自动创建。</p>
     *
     * @param cmd 调整库存命令
     * @return 调整后的库存聚合根
     */
    @Retryable(retryFor = OptimisticLockException.class,
               maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional(rollbackFor = Exception.class)
    public Inventory adjustStock(AdjustStockCommand cmd) {
        boolean isIncrease = cmd.delta().signum() >= 0;
        BigDecimal absDelta = cmd.delta().abs();

        Optional<Inventory> existing = repository.findBySkuAndZone(cmd.skuId(), cmd.tempZone());

        if (existing.isPresent()) {
            Inventory inv = existing.get();
            if (isIncrease) {
                inv.increaseQty(absDelta);
            } else {
                inv.decreaseQty(absDelta);
            }
            repository.update(inv);
            return inv;
        }

        // 库存不存在 — "不存在则创建" 业务规则下沉到领域层
        Inventory inv = Inventory.createOrAdjust(
                cmd.skuId(), cmd.tempZone(), absDelta, isIncrease);
        repository.save(inv);
        return inv;
    }

    /**
     * 冻结库存
     *
     * @param cmd 冻结命令
     * @return 冻结后的库存聚合根
     */
    @Retryable(retryFor = OptimisticLockException.class,
               maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional(rollbackFor = Exception.class)
    public Inventory freeze(FreezeCommand cmd) {
        Inventory inv = repository.findBySkuAndZone(cmd.skuId(), cmd.tempZone())
                .orElseThrow(() -> new NotFoundException("INVENTORY_NOT_FOUND",
                        "库存记录不存在：skuId=" + cmd.skuId() + " tempZone=" + cmd.tempZone()));
        inv.freezeQty(cmd.qty());
        repository.update(inv);
        return inv;
    }

    /**
     * 解冻库存
     *
     * @param cmd 解冻命令
     * @return 解冻后的库存聚合根
     */
    @Retryable(retryFor = OptimisticLockException.class,
               maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional(rollbackFor = Exception.class)
    public Inventory unfreeze(FreezeCommand cmd) {
        Inventory inv = repository.findBySkuAndZone(cmd.skuId(), cmd.tempZone())
                .orElseThrow(() -> new NotFoundException("INVENTORY_NOT_FOUND",
                        "库存记录不存在：skuId=" + cmd.skuId() + " tempZone=" + cmd.tempZone()));
        inv.unfreezeQty(cmd.qty());
        repository.update(inv);
        return inv;
    }

    /**
     * 查询库存
     *
     * @param skuId    SKU 标识
     * @param tempZone 温区
     * @return 库存聚合根
     */
    public Optional<Inventory> getInventory(Long skuId, TempZone tempZone) {
        return repository.findBySkuAndZone(skuId, tempZone);
    }

    // ── 命令 Records ────────────────────────────────────────

    /**
     * 调整库存命令
     *
     * @param skuId   SKU 标识
     * @param tempZone 温区
     * @param delta   变动数量（正数=增加，负数=扣减）
     */
    public record AdjustStockCommand(Long skuId, TempZone tempZone, BigDecimal delta) {}

    /**
     * 冻结/解冻命令
     *
     * @param skuId   SKU 标识
     * @param tempZone 温区
     * @param qty     数量（必须 > 0）
     */
    public record FreezeCommand(Long skuId, TempZone tempZone, BigDecimal qty) {}
}
