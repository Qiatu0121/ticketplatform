package com.ticket.platform.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务生命周期回调工具，用于在事务提交/回滚后执行 Redis 等非事务操作。
 */
public final class TransactionHooks {

    private TransactionHooks() {
    }

    /** 事务提交后执行；没有活动事务时立即执行。 */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** 事务回滚后执行；没有活动事务时立即执行。 */
    public static void afterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        action.run();
                    }
                }
            });
        } else {
            action.run();
        }
    }
}
