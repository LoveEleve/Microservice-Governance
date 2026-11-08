package com.wuba.crm.tx.e2e.quota;

import com.wuba.crm.tx.tcc.participant.action.TccActionRegistry;
import org.springframework.stereotype.Component;

/**
 * 注册 quota TCC 动作。
 */
@Component
public class QuotaActionRegistration {

    /**
     * 创建并注册。
     *
     * @param registry 动作注册表
     * @param action   quota 动作
     */
    public QuotaActionRegistration(TccActionRegistry registry, QuotaTccAction action) {
        registry.register("reserveQuota", action);
    }
}
