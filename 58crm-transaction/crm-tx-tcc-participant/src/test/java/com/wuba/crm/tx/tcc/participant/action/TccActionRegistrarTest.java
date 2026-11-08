package com.wuba.crm.tx.tcc.participant.action;

import com.wuba.crm.tx.tcc.api.BusinessActionContext;
import com.wuba.crm.tx.tcc.api.TccAction;
import com.wuba.crm.tx.tcc.api.TccActionHandler;
import com.wuba.crm.tx.tcc.api.TccPhaseResult;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 @TccActionHandler Bean 自动注册。
 */
class TccActionRegistrarTest {

    @Test
    void registerAll_findsAnnotatedBean() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(TccActionRegistry.class, TccActionRegistry::new);
        context.registerBean(QuotaAction.class, QuotaAction::new);
        context.refresh();

        TccActionRegistry registry = context.getBean(TccActionRegistry.class);
        new TccActionRegistrar(context, registry).registerAll();

        assertEquals(QuotaAction.class, registry.require("reserveQuota").getClass());
        context.close();
    }

    /**
     * 测试动作。
     */
    @TccActionHandler("reserveQuota")
    static class QuotaAction implements TccAction {

        @Override
        public TccTryResult tryAction(BusinessActionContext context) {
            return TccTryResult.SUCCESS;
        }

        @Override
        public TccPhaseResult confirmAction(BusinessActionContext context) {
            return TccPhaseResult.SUCCESS;
        }

        @Override
        public TccPhaseResult cancelAction(BusinessActionContext context) {
            return TccPhaseResult.SUCCESS;
        }
    }
}
