package com.wuba.crm.tx.tcc.api;

/**
 * 业务方发起 TCC 的唯一公共入口。
 *
 * <p>业务方只描述 branches 和每个 branch 的 Try 回调；begin、register、
 * report、commit/rollback 由实现统一处理。</p>
 */
public interface TccTransactionTemplate {

    /**
     * 执行一次 TCC。
     *
     * @param request 请求
     * @return 执行结果
     */
    TccResult execute(TccRequest request);
}
