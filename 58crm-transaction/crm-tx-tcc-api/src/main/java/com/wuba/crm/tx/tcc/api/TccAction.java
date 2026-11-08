package com.wuba.crm.tx.tcc.api;

public interface TccAction {

    TccTryResult tryAction(BusinessActionContext context);

    TccPhaseResult confirmAction(BusinessActionContext context);

    TccPhaseResult cancelAction(BusinessActionContext context);
}
