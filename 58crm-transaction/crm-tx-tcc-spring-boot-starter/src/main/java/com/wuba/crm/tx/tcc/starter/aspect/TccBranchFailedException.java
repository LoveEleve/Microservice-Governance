package com.wuba.crm.tx.tcc.starter.aspect;

/**
 * 分支 Try 失败，通知外层事务回滚。
 */
public class TccBranchFailedException extends RuntimeException {

    /**
     * 创建异常。
     *
     * @param message 说明
     */
    public TccBranchFailedException(String message) {
        super(message);
    }
}
