package com.wuba.crm.tx.tcc.api;

import java.util.List;
import java.util.Objects;

/**
 * 全局事务查询结果。
 *
 * @param status   当前全局状态
 * @param branches 分支快照，按查询顺序返回
 */
public record TccQueryResult(GlobalStatus status, List<TccBranchInfo> branches) {

    public TccQueryResult {
        status = Objects.requireNonNull(status, "status");
        branches = List.copyOf(Objects.requireNonNull(branches, "branches"));
    }
}
