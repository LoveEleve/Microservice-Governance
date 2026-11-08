package com.wuba.crm.tx.tcc.api;

/**
 * 参与方本地 TCC 动作契约。
 *
 * <p>Try 由业务 API 调用；Confirm/Cancel 由 participant SDK 在 barrier
 * 事务中调用。实现方必须保证三个方法自身的业务幂等。</p>
 */
public interface TccAction {

    /**
     * 执行业务预占。
     *
     * @param context 分支上下文
     * @return Try 结果
     */
    TccTryResult tryAction(BusinessActionContext context);

    /**
     * 执行业务确认。
     *
     * @param context 分支上下文
     * @return Confirm 结果
     */
    TccPhaseResult confirmAction(BusinessActionContext context);

    /**
     * 执行业务取消/释放。
     *
     * @param context 分支上下文
     * @return Cancel 结果
     */
    TccPhaseResult cancelAction(BusinessActionContext context);
}
