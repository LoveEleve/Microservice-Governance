package com.wuba.crm.tx.tcc.api;

/**
 * 业务方提供给 {@link TccTransactionTemplate} 的 Try 回调。
 *
 * <p>框架负责注册分支和上报结果；回调内部通常是 OpenFeign 调下游 Try API。</p>
 */
@FunctionalInterface
public interface TccTryAction {

    /**
     * 执行业务 Try。
     *
     * @param context Try 上下文
     * @return Try 结果
     */
    TccTryResult execute(TccTryContext context);
}
