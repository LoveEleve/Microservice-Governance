package com.wuba.crm.tx.tcc.coordinator.callback;

import com.wuba.crm.tx.tcc.protocol.model.TccCallbackResult;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchEntity;

/**
 * TC 到 RM 的二阶段回调客户端。
 *
 * <p>实现可以替换为 in-process mock 或真实 gRPC 客户端；Coordinator 的
 * 状态机只依赖这个接口。</p>
 */
public interface TccParticipantClient {

    /**
     * 调用 RM Confirm。
     *
     * @param branch 分支快照
     * @return 回调结果
     */
    TccCallbackResult confirm(TccBranchEntity branch);

    /**
     * 调用 RM Cancel。
     *
     * @param branch 分支快照
     * @return 回调结果
     */
    TccCallbackResult cancel(TccBranchEntity branch);
}
