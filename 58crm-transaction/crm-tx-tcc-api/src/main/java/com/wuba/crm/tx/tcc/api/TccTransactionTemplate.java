package com.wuba.crm.tx.tcc.api;

/**
 * TM 侧发起 TCC 控制流的接口。
 *
 * <p>实现负责把请求转成 TccCoordinator RPC，并把结果映射为 API 返回模型。
 * Try 不在这里发起。</p>
 */
public interface TccTransactionTemplate {

    /**
     * 开启全局事务。
     *
     * @param request begin 请求
     * @return 全局事务号和初始状态
     */
    TccBeginResult begin(TccBeginRequest request);

    /**
     * 注册分支。
     *
     * @param request 注册请求
     * @return 分支号和初始状态
     */
    TccBranchRegisterResult registerBranch(TccBranchRegisterRequest request);

    /**
     * 上报 Try 结果。
     *
     * @param request 上报请求
     */
    void reportBranch(TccBranchReportRequest request);

    /**
     * 提交全局事务。
     *
     * @param xid 全局事务号
     * @return 当前全局状态
     */
    TccCommitResult commit(long xid);

    /**
     * 回滚全局事务。
     *
     * @param xid 全局事务号
     * @return 当前全局状态
     */
    TccRollbackResult rollback(long xid);

    /**
     * 查询全局事务。
     *
     * @param xid 全局事务号
     * @return 查询结果
     */
    TccQueryResult query(long xid);
}
