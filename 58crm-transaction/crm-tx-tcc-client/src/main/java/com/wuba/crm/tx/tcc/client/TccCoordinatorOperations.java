package com.wuba.crm.tx.tcc.client;

import com.wuba.crm.tx.tcc.api.TccBeginRequest;
import com.wuba.crm.tx.tcc.api.TccBeginResult;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.api.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.api.TccCommitResult;
import com.wuba.crm.tx.tcc.api.TccRollbackResult;

/**
 * DefaultTccTransactionTemplate 对 Coordinator 的最小依赖。
 *
 * <p>这是客户端内部协议边界；业务方只依赖 TccTransactionTemplate。</p>
 */
public interface TccCoordinatorOperations {

    /**
     * 开启全局事务。
     *
     * @param request 请求
     * @return 结果
     */
    TccBeginResult begin(TccBeginRequest request);

    /**
     * 注册分支。
     *
     * @param request 请求
     * @return 结果
     */
    TccBranchRegisterResult registerBranch(TccBranchRegisterRequest request);

    /**
     * 上报 Try 结果。
     *
     * @param request 请求
     */
    void reportBranch(TccBranchReportRequest request);

    /**
     * 提交。
     *
     * @param xid 全局事务号
     * @return 结果
     */
    TccCommitResult commit(long xid);

    /**
     * 回滚。
     *
     * @param xid 全局事务号
     * @return 结果
     */
    TccRollbackResult rollback(long xid);
}
