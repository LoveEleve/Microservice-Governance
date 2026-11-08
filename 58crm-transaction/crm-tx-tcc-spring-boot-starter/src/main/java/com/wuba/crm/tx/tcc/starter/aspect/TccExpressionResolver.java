package com.wuba.crm.tx.tcc.starter.aspect;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.env.Environment;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * 注解 SpEL/占位符解析。
 */
public class TccExpressionResolver implements EnvironmentAware {

    private final ExpressionParser parser = new SpelExpressionParser();
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 解析注解值。
     *
     * @param value  字面量或 # 表达式
     * @param method 方法
     * @param args   参数
     * @return 字符串
     */
    public String resolve(String value, Method method, Object[] args) {
        if (value == null) {
            return "";
        }
        String withPlaceholders = environment.resolvePlaceholders(value);
        if (!withPlaceholders.contains("#")) {
            return withPlaceholders;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("method", method);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("a" + i, args[i]);
            context.setVariable("p" + i, args[i]);
        }
        String[] parameterNames = new DefaultParameterNameDiscoverer().getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return parser.parseExpression(withPlaceholders).getValue(context, String.class);
    }
}
