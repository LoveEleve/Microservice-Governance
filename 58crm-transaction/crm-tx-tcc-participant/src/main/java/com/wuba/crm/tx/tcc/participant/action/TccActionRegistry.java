package com.wuba.crm.tx.tcc.participant.action;

import com.wuba.crm.tx.tcc.api.TccAction;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.api.TccResultCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参与方本地 TCC 动作注册表。
 *
 * <p>业务方在启动时按 actionName 注册动作；Confirm/Cancel 回调只通过
 * actionName 查找，不通过反射或全局 Bean 名推断。</p>
 */
@Component
public class TccActionRegistry {

    private final Map<String, TccAction> actions = new ConcurrentHashMap<>();

    /**
     * 注册动作。
     *
     * @param actionName 动作名
     * @param action     动作实现
     */
    public void register(String actionName, TccAction action) {
        if (actionName == null || actionName.isBlank()) {
            throw new IllegalArgumentException("actionName must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        TccAction previous = actions.putIfAbsent(actionName, action);
        if (previous != null) {
            throw new IllegalStateException("duplicate TCC action: " + actionName);
        }
    }

    /**
     * 查找动作。
     *
     * @param actionName 动作名
     * @return 动作实现
     */
    public TccAction require(String actionName) {
        TccAction action = actions.get(actionName);
        if (action == null) {
            throw new TccException(TccResultCode.TCC_NOT_FOUND,
                    "unknown TCC action: " + actionName);
        }
        return action;
    }
}
