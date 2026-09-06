package com.nongpi.fulfillment.lot.infrastructure;

import com.nongpi.fulfillment.lot.application.LotAppService.BatchOutbound;
import com.nongpi.fulfillment.lot.application.LotAppService.OutboundCommand;
import com.nongpi.fulfillment.lot.application.LotAppService.LotOutboundResult;
import com.nongpi.fulfillment.lot.domain.*;
import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.exception.BusinessException;
import com.nongpi.fulfillment.common.infrastructure.util.TransactionUtils;
import com.nongpi.fulfillment.inventory.infrastructure.mapper.InventoryMapper;
import com.nongpi.fulfillment.lot.infrastructure.config.RedissonConfig;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 出库编排服务 — 基于 FEFO 策略和 Redisson 分布式锁
 *
 * <p>执行流程：
 * <ol>
 *   <li>调用 {@link FefoCache#getEarliest} 获取需要出库的批次列表</li>
 *   <li>对每个批次获取分布式锁 {@code lock:lot:{lotNo}}</li>
 *   <li>加载聚合根、执行出库、保存（领域事件写入 Outbox）</li>
 *   <li>批次出清后通过 {@link TransactionSynchronization} 异步更新 Redis ZSet</li>
 * </ol>
 * </p>
 */
@Service
public class OutboundService {

    private static final Logger log = LoggerFactory.getLogger(OutboundService.class);

    private static final String LOCK_KEY_PREFIX = "lock:lot:";

    private final ILotRepository repository;
    private final FefoCache fefoCache;
    private final RedissonClient redissonClient;
    private final RedissonConfig redissonConfig;
    private final InventoryMapper inventoryMapper;

    public OutboundService(ILotRepository repository,
                           FefoCache fefoCache,
                           RedissonClient redissonClient,
                           RedissonConfig redissonConfig,
                           InventoryMapper inventoryMapper) {
        this.repository = repository;
        this.fefoCache = fefoCache;
        this.redissonClient = redissonClient;
        this.redissonConfig = redissonConfig;
        this.inventoryMapper = inventoryMapper;
    }

    /**
     * 执行出库
     *
     * <p>两种模式：</p>
     * <ul>
     *   <li>{@code cmd.lotNo()} 非空 → <b>指定批次直接出库</b>（前端批次行"出库"按钮），
     *       对目标批次加分布式锁，扣减该批次剩余量。修复前 FEFO 会误选同温区最早过期批次，
     *       导致用户点击的批次初始量/剩余量不变。</li>
     *   <li>{@code cmd.lotNo()} 为空 → <b>FEFO 自动选批</b>（按 SKU+温区，最早过期优先），
     *       累计出库直到凑够数量。</li>
     * </ul>
     *
     * @param cmd 出库命令
     * @return 出库结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LotOutboundResult execute(OutboundCommand cmd) {
        // 指定批次直接出库（批次行"出库"按钮场景）
        if (cmd.lotNo() != null && !cmd.lotNo().isBlank()) {
            return outboundSpecificLot(cmd);
        }
        return outboundByFefo(cmd);
    }

    /**
     * 指定批次直接出库 — 对目标批次加分布式锁，扣减该批次剩余量
     */
    private LotOutboundResult outboundSpecificLot(OutboundCommand cmd) {
        LotNo lotNo = LotNo.fromString(cmd.lotNo());
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + lotNo.value());
        try {
            boolean locked = lock.tryLock(
                    redissonConfig.getLockWaitTime(),
                    redissonConfig.getLockLeaseTime(),
                    TimeUnit.MILLISECONDS
            );
            if (!locked) {
                throw new BusinessException(409, "LOCK_FAILED",
                        "批次 " + cmd.lotNo() + " 正在被其他操作处理，请重试");
            }
            try {
                Lot lot = repository.getById(lotNo)
                        .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + cmd.lotNo()));

                // 指定批次出库时，SKU 和温区从批次取，不依赖 cmd（防止 LLM 传错 skuId/tempZone）
                Long lotSkuId = lot.getSkuId();
                String lotTempZone = lot.getTempZone().name();

                // 先扣库存（DB WHERE 条件防超卖），失败则批次不动，避免数据不一致
                int affected = inventoryMapper.decreaseStock(lotSkuId, lotTempZone, cmd.qty());
                if (affected == 0) {
                    throw new BusinessException(422, "INSUFFICIENT_QTY",
                            "可用库存不足：出库 " + cmd.qty() + "，SKU=" + lotSkuId + " 温区=" + lotTempZone);
                }

                // 库存扣减成功后再扣批次（canOutbound 内部校验温区匹配+库存充足+未过期）
                lot.outbound(cmd.qty(), cmd.toLocation());
                repository.update(lot);

                // 批次出清后，事务提交后再清理 Redis ZSet
                if (lot.getStatus() == LotStatus.FULLY_OUT) {
                    String lotNoValue = lot.getLotNo().value();
                    registerPostCommit(() -> {
                        try {
                            fefoCache.removeLot(lotNoValue, lot.getTempZone());
                        } catch (Exception e) {
                            log.error("Redis 移除 FEFO 缓存失败，批次 {}", lotNoValue, e);
                        }
                    });
                }

                return new LotOutboundResult(
                        lot.getLotNo().value(),
                        cmd.qty(),
                        lot.getRemainingQty(),
                        lot.getStatus(),
                        lot.getTempZone()
                );
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("出库操作被中断", e);
        }
    }

    /**
     * FEFO 自动选批出库 — 逐批加锁出库直到凑够数量
     */
    private LotOutboundResult outboundByFefo(OutboundCommand cmd) {
        // 1. 获取 FEFO 批次列表（按 SKU 过滤，防止同温区误扣其他商品批次）
        List<LotNo> lotNos = fefoCache.getEarliest(cmd.tempZone(), cmd.qty(), cmd.skuId());

        // 2. 逐批出库
        BigDecimal remaining = cmd.qty();//全局遍历，用于下次出库判断是否满足条件
        BigDecimal totalOut = BigDecimal.ZERO;
        LotStatus finalStatus = null;
        String finalLotNo = null;
        List<String> fullyOutLotNos = new ArrayList<>();
        List<BatchOutbound> batches = new ArrayList<>();

        for (LotNo lotNo : lotNos) {
            // 获取分布式锁
            RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + lotNo.value());
            try {
                boolean locked = lock.tryLock(
                        redissonConfig.getLockWaitTime(),
                        redissonConfig.getLockLeaseTime(),
                        TimeUnit.MILLISECONDS
                );
                if (!locked) {
                    log.warn("获取批次锁失败，跳过: {}", lotNo.value());
                    continue;
                }

                try {
                    // 3. 加载聚合根
                    Lot lot = repository.getById(lotNo)
                            .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + lotNo));

                    // 4. 计算本次出库数量
                    BigDecimal outQty = remaining.min(lot.getRemainingQty());

                    // 5. 执行出库
                    lot.outbound(outQty, cmd.toLocation());

                    // 6. 保存（事务内只做 DB，Redis/库存同步放事务后）
                    repository.update(lot);

                    // 7. 记录出清批次，待事务提交后清理 Redis
                    if (lot.getStatus() == LotStatus.FULLY_OUT) {
                        fullyOutLotNos.add(lot.getLotNo().value());
                    }

                    totalOut = totalOut.add(outQty);
                    remaining = remaining.subtract(outQty);
                    finalStatus = lot.getStatus();
                    finalLotNo = lot.getLotNo().value();
                    batches.add(new BatchOutbound(lot.getLotNo().value(), outQty, lot.getRemainingQty(), lot.getStatus(), lot.getTempZone()));

                    // 8. 如果已凑够数量，停止
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("出库操作被中断", e);
            }
        }

        if (totalOut.compareTo(BigDecimal.ZERO) == 0) {
            throw new NoAvailableLotException("无法完成出库：所有批次加锁失败或无可用批次");
        }

        // 9. 事务内原子扣减库存（DB WHERE 条件防超卖，无需 @Retryable）
        // (total_qty - frozen_qty) >= ? 在 DB 层校验可用量，避免 TOCTOU 竞态
        int affected = inventoryMapper.decreaseStock(cmd.skuId(), cmd.tempZone().name(), totalOut);
        if (affected == 0) {
            throw new BusinessException(422, "INSUFFICIENT_QTY",
                    "可用库存不足：出库 " + totalOut + "，SKU=" + cmd.skuId() + " 温区=" + cmd.tempZone());
        }

        // 10. 事务提交后清理已出清批次的 Redis ZSet（Redis 资源，崩溃由 Outbox+MQ 消费者补偿）
        registerPostCommit(() -> {
            for (String lotNo : fullyOutLotNos) {
                try {
                    fefoCache.removeLot(lotNo);
                } catch (Exception e) {
                    log.error("Redis 移除 FEFO 缓存失败，批次 {}", lotNo, e);
                }
            }
        });

        return new LotOutboundResult(
                finalLotNo,
                totalOut,
                BigDecimal.ZERO.max(remaining), // 剩余未出库数量
                finalStatus,
                batches
        );
    }

    /**
     * 注册事务提交后的回调（Redis 等非 DB 操作放事务外执行）
     * <p>统一收敛到 {@link TransactionUtils#registerPostCommit}，避免多份复制。</p>
     */
    private void registerPostCommit(Runnable action) {
        TransactionUtils.registerPostCommit(action);
    }
}