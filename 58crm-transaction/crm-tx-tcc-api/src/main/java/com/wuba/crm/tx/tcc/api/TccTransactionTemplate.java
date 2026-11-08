package com.wuba.crm.tx.tcc.api;

public interface TccTransactionTemplate {

    TccBeginResult begin(TccBeginRequest request);

    TccBranchRegisterResult registerBranch(TccBranchRegisterRequest request);

    void reportBranch(TccBranchReportRequest request);

    TccCommitResult commit(long xid);

    TccRollbackResult rollback(long xid);

    TccQueryResult query(long xid);
}
