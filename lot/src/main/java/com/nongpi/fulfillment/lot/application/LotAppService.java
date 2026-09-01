package com.nongpi.fulfillment.lot.application;

import com.nongpi.fulfillment.common.domain.LotStatus;
import com.nongpi.fulfillment.common.domain.TempZone;
import com.nongpi.fulfillment.common.exception.BusinessException;
import com.nongpi.fulfillment.common.exception.NotFoundException;
import com.nongpi.fulfillment.common.infrastructure.util.TransactionUtils;

import com.nongpi.fulfillment.inventory.infrastructure.mapper.InventoryMapper;
import com.nongpi.fulfillment.lot.domain.*;
import com.nongpi.fulfillment.lot.infrastructure.FefoCache;
import com.nongpi.fulfillment.lot.infrastructure.OutboundService;
import com.nongpi.fulfillment.lot.infrastructure.config.RedissonConfig;
import com.nongpi.fulfillment.lot.infrastructure.mapper.LotTransferMapper;
import com.nongpi.fulfillment.lot.infrastructure.persistence.LotTransferPO;
import com.nongpi.fulfillment.lot.infrastructure.service.OutboxMessageRelay;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 批次应用服务 — 用例编排层
 *
 * <p>编排领域对象和基础设施服务完成完整的业务用例：入库、出库、转库。
 * 事务边界在此层控制。</p>
 */
@Service
public class LotAppService {

    private static final Logger log = LoggerFactory.getLogger(LotAppService.class);

    private final ILotRepository repository;
    private final FefoCache fefoCache;
    private final OutboundService outboundService;
    private final InventoryMapper inventoryMapper;
    private final LotTransferMapper lotTransferMapper;
    private final RedissonClient redissonClient;
    private final RedissonConfig redissonConfig;
    private final com.nongpi.fulfillment.sku.domain.ISkuRepository skuRepository;

    private static final String LOCK_KEY_PREFIX = "lock:lot:";

    /** t_lot_transfer.transfer_type 枚举：1=入库 2=出库 3=转库 4=报损（避免魔法数字） */
    private static final int TRANSFER_TYPE_INBOUND = 1;
    private static final int TRANSFER_TYPE_OUTBOUND = 2;
    private static final int TRANSFER_TYPE_TRANSFER = 3;

    /** 入库 lotNo 生成冲突时最大重试次数（4位随机数9000组合，5次重试后碰撞概率≈0.6%@200单/日） */
    private static final int LOT_NO_GENERATE_MAX_RETRY = 5;

    public LotAppService(ILotRepository repository,
                         FefoCache fefoCache,
                         OutboundService outboundService,
                         InventoryMapper inventoryMapper,
                         LotTransferMapper lotTransferMapper,
                         RedissonClient redissonClient,
                         RedissonConfig redissonConfig,
                         com.nongpi.fulfillment.sku.domain.ISkuRepository skuRepository) {
        this.repository = repository;
        this.fefoCache = fefoCache;
        this.outboundService = outboundService;
        this.inventoryMapper = inventoryMapper;
        this.lotTransferMapper = lotTransferMapper;
        this.redissonClient = redissonClient;
        this.redissonConfig = redissonConfig;
        this.skuRepository = skuRepository;
    }

    /**
     * 入库用例
     *
     * <ol>
     *   <li>生成批次号（主键冲突时最多重试 {@link #LOT_NO_GENERATE_MAX_RETRY} 次）</li>
     *   <li>调用 {@link Lot#createNew} 创建聚合根</li>
     *   <li>持久化（含入库领域事件 → Outbox）</li>
     *   <li>事务提交后将批次加入 FEFO Redis ZSet</li>
     *   <li>写入 t_lot_transfer 入库记录</li>
     * </ol>
     */
/*    入库时，LotAppService.inbound() 开启 @Transactional 事务，同一事务内 lotMapper.insert(po) 写 t_lot + saveDomainEvents(lot) 写 t_lot_event（status=PENDING），保证业务数据和事件原子一致。事务提交后执行回调：同步库存 + 直接更新 FEFO 缓存（低延迟路径，但不可靠——应用崩溃会丢）。

    OutboxMessageRelay 每 2 秒扫描 PENDING 事件，标记 IN_FLIGHT + in_flight_at=now 后发 RabbitMQ，基于 Publisher Confirm 驱动状态机：ack 标记 PUBLISHED，nack 重试 3 次超限进死信，IN_FLIGHT 超 30 秒自动恢复 PENDING 重投（防应用崩溃丢消息）。

    消费者收到消息先写 t_mq_consume_log 唯一键幂等去重，再 fefoCache.addLot 补偿更新缓存，失败抛 AmqpRejectAndDontRequeueException 路由到死信队列。这样 FEFO 缓存有双写保证——直接调用快但不可靠，MQ 补偿慢但可靠，崩溃时消费者把缺失的缓存补上。
     */
    @Transactional(rollbackFor = Exception.class)
    public Lot inbound(InboundCommand cmd) {
        // 0. 主数据校验：skuId 必须存在于 t_sku（主数据 → 交易数据闭环）
        if (!skuRepository.existsById(cmd.skuId())) {
            throw new BusinessException(422, "SKU_NOT_FOUND",
                    "商品不存在：skuId=" + cmd.skuId() + "，请先在商品管理中创建该 SKU");
        }

        Lot lot = null;
        for (int i = 0; i < LOT_NO_GENERATE_MAX_RETRY; i++) {
            LotNo lotNo = LotNo.generate(String.valueOf(cmd.supplierId()));
            lot = Lot.createNew(//传入参数的时候就开始进行规则校验
                    lotNo,
                    cmd.skuId(),
                    cmd.tempZone(),
                    cmd.produceDate(),
                    cmd.expireDate(),
                    cmd.qty(),
                    cmd.supplierId()
            );
            try {
                repository.add(lot);
                break;
            } catch (DuplicateKeyException e) {
                if (i == LOT_NO_GENERATE_MAX_RETRY - 1) {
                    log.error("入库批次号生成冲突已达最大重试次数 {}，供应商={}",
                            LOT_NO_GENERATE_MAX_RETRY, cmd.supplierId());
                    throw e;
                }
                log.warn("入库批次号生成冲突，第 {}/{} 次重试，lotNo={}",
                        i + 1, LOT_NO_GENERATE_MAX_RETRY, lot.getLotNo().value());
            }
        }

        Lot resultLot = lot;

        // 事务内原子更新库存（强一致，DB 行锁串行化，无需 @Retryable）
        // 用 ON DUPLICATE KEY UPDATE 替代 adjustStock 的读-改-写，一条 SQL 无并发冲突
        inventoryMapper.insertOrIncrease(cmd.skuId(), cmd.tempZone().name(), cmd.qty());

        // FEFO 缓存（Redis 资源）放事务提交后，崩溃由 Outbox+MQ 消费者补偿
        registerPostCommit(() -> {
            try {
                fefoCache.addLot(resultLot);
            } catch (Exception e) {
                log.error("Redis 写入 FEFO 缓存失败，批次 {}", resultLot.getLotNo().value(), e);
            }
        });

        insertTransferRecord(lot.getLotNo().value(), TRANSFER_TYPE_INBOUND, cmd.qty(), null, null, cmd.tempZone());

        return lot;
    }

    /**
     * 出库用例 — 委托给 {@link OutboundService}
     */
    public LotOutboundResult outbound(OutboundCommand cmd) {
        LotOutboundResult result = outboundService.execute(cmd);

        // 写入出库流转记录
        insertTransferRecord(result.lotNo(), TRANSFER_TYPE_OUTBOUND, result.outQty(), null, cmd.toLocation(), cmd.tempZone());

        return result;
    }

    /**
     * 转库用例 — 指定批次号直接出库（Redisson 分布式锁 + 乐观锁双保险）
     *
     * <ol>
     *   <li>获取 lotNo 粒度分布式锁</li>
     *   <li>根据批次号加载聚合根</li>
     *   <li>执行出库（转移至新库位）</li>
     *   <li>持久化更新</li>
     *   <li>事务提交后批次出清时清理 FEFO ZSet</li>
     *   <li>写入 t_lot_transfer 调拨记录</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public LotOutboundResult transfer(TransferCommand cmd) {
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
                        .orElseThrow(() -> new NotFoundException("批次不存在: " + cmd.lotNo()));

                // 执行出库（canOutbound 内部由 outbound 方法调用）
                lot.outbound(cmd.qty(), cmd.toLocation());
                repository.update(lot);

                // 事务内原子扣减库存（DB WHERE 条件防超卖，无需 @Retryable）
                // (total_qty - frozen_qty) >= ? 在 DB 层校验可用量，避免 TOCTOU 竞态
                int affected = inventoryMapper.decreaseStock(lot.getSkuId(), lot.getTempZone().name(), cmd.qty());
                if (affected == 0) {
                    throw new BusinessException(422, "INSUFFICIENT_QTY",
                            "可用库存不足：转库 " + cmd.qty() + "，SKU=" + lot.getSkuId() + " 温区=" + lot.getTempZone());
                }

                // 批次出清后，事务提交后再清理 Redis ZSet
                if (lot.getStatus() == LotStatus.FULLY_OUT) {
                    String lotNoValue = lot.getLotNo().value();
                    registerPostCommit(() -> {
                        try {
                            fefoCache.removeLot(lotNoValue);
                        } catch (Exception e) {
                            log.error("Redis 移除 FEFO 缓存失败，批次 {}", lotNoValue, e);
                        }
                    });
                }

                // 写入调拨流转记录
                insertTransferRecord(cmd.lotNo(), TRANSFER_TYPE_TRANSFER, cmd.qty(), cmd.fromLocation(), cmd.toLocation(), lot.getTempZone());

                return new LotOutboundResult(
                        lot.getLotNo().value(),
                        cmd.qty(),
                        lot.getRemainingQty(),
                        lot.getStatus()
                );
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("转库操作被中断", e);
        }
    }

    // ── 内部方法 ────────────────────────────────────────────

    /**
     * 注册事务提交后的回调（Redis 等非 DB 操作放事务外执行，防止回滚后不一致）
     * <p>统一收敛到 {@link TransactionUtils#registerPostCommit}，避免多份复制。</p>
     */
    private void registerPostCommit(Runnable action) {
        TransactionUtils.registerPostCommit(action);
    }

    /**
     * 写入 t_lot_transfer 流转记录
     *
     * @param lotNo       批次号
     * @param transferType 流转类型 (1=入库 2=出库 3=调拨 4=报损)
     * @param qty         数量
     * @param fromLocation 来源库位
     * @param toLocation   目标库位
     * @param tempZone    温区
     */
    private void insertTransferRecord(String lotNo, int transferType,
                                      BigDecimal qty, String fromLocation,
                                      String toLocation, TempZone tempZone) {
        // transfer_no 格式: TRF + 日期 + 序号（简化用时间戳）
        LotTransferPO po = new LotTransferPO();
        po.setTransferNo("TRF" + System.currentTimeMillis());
        po.setLotNo(lotNo);
        po.setTransferType(transferType);
        po.setQty(qty);
        po.setFromLocation(fromLocation);
        po.setToLocation(toLocation);
        po.setCreatedAt(LocalDateTime.now());
        lotTransferMapper.insert(po);
    }

    // ── Command Records ──────────────────────────────────────

    /**
     * 入库命令
     */
    public record InboundCommand(
            Long skuId,
            TempZone tempZone,
            LocalDate produceDate,
            LocalDate expireDate,
            BigDecimal qty,
            Long supplierId
    ) {}

    /**
     * 出库命令
     *
     * @param lotNo      指定批次号（非空=指定批次直接出库；空=FEFO 自动选最早过期批次）
     */
    public record OutboundCommand(
            Long skuId,
            TempZone tempZone,
            BigDecimal qty,
            String toLocation,
            String lotNo
    ) {}

    /**
     * 转库命令
     */
    public record TransferCommand(
            String lotNo,
            BigDecimal qty,
            String fromLocation,
            String toLocation
    ) {}

    /**
     * 出库结果
     */
    public record LotOutboundResult(
            String lotNo,
            BigDecimal outQty,
            BigDecimal remainingQty,
            LotStatus status
    ) {}
}
