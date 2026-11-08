package com.wuba.crm.tx.tcc.client;

import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccBeginRequest;
import com.wuba.crm.tx.tcc.api.TccBeginResult;
import com.wuba.crm.tx.tcc.client.internal.TccCoordinatorOperations;

/**
 * 注解 TCC 的事务管理器。
 *
 * <p>负责 begin、ThreadLocal 当前事务、commit/rollback。</p>
 */
public class TccTransactionManager {

    private final TccCoordinatorOperations coordinator;
    private final ThreadLocal<TccAnnotationTransaction> current = new ThreadLocal<>();

    /**
     * 创建管理器。
     *
     * @param coordinator Coordinator 操作
     */
    public TccTransactionManager(TccCoordinatorOperations coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * 开启事务。
     *
     * @param options begin 参数
     * @return 活动事务
     */
    public TccAnnotationTransaction begin(TccTransactionOptions options) {
        if (current.get() != null) {
            throw new IllegalStateException("nested TCC transaction is not supported");
        }
        TccBeginResult result = coordinator.begin(new TccBeginRequest(
                options.requestId(),
                options.bizCode(),
                options.bizId(),
                timeoutSeconds(options)));
        if (result.status() != GlobalStatus.TRYING) {
            throw new IllegalStateException("begin returned " + result.status());
        }
        TccAnnotationTransaction transaction = new TccAnnotationTransaction(
                result.xid(), coordinator);
        current.set(transaction);
        return transaction;
    }

    /**
     * 当前事务。
     *
     * @return 当前事务
     */
    public TccAnnotationTransaction current() {
        TccAnnotationTransaction transaction = current.get();
        if (transaction == null) {
            throw new IllegalStateException("no active annotation TCC transaction");
        }
        return transaction;
    }

    /**
     * 清理当前事务。
     */
    public void clear() {
        current.remove();
    }

    private int timeoutSeconds(TccTransactionOptions options) {
        long seconds = options.tryTimeout().toSeconds();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, seconds));
    }
}
