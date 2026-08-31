package com.nongpi.fulfillment.common.infrastructure.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务工具类
 *
 * <p>提供事务提交后的回调注册。Redis 等非 DB 操作应在事务提交后执行，
 * 防止事务回滚后 Redis 与 DB 不一致。</p>
 *
 * <p>原实现分散在 LotAppService / OutboundService 中的匿名内部类，
 * 现已收敛到此处统一复用。</p>
 */
public final class TransactionUtils {

    private TransactionUtils() {
    }

    /**
     * 注册事务提交后的回调；无活跃事务时立即执行。
     *
     * @param action 事务提交后要执行的动作
     */
    public static void registerPostCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
        } else {
            // 无事务时直接执行
            action.run();
        }
    }
}
