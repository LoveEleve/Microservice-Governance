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
 *
 * <p>推荐语法：{@code #{...}} 明确表示表达式，表达式外的 # 原样保留；
 * 例如 {@code "order:#{#orderId}"}。为兼容旧写法，值以 # 或单引号开头时
 * 仍按整段 SpEL 解析，例如 {@code "#userId"}、{@code "'quota:' + #userId"}。</p>
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
     * @param value  字面量、{@code #{}} 模板或旧版整段 SpEL
     * @param method 方法
     * @param args   参数
     * @return 字符串
     */
    public String resolve(String value, Method method, Object[] args) {
        if (value == null) {
            return "";
        }
        String resolved = environment == null ? value : environment.resolvePlaceholders(value);
        StandardEvaluationContext context = evaluationContext(method, args);
        if (resolved.contains("#{")) {
            return evaluateTemplate(resolved, context);
        }
        if (isLegacyExpression(resolved)) {
            return evaluate(resolved, context);
        }
        return resolved;
    }

    private StandardEvaluationContext evaluationContext(Method method, Object[] args) {
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
        return context;
    }

    private String evaluateTemplate(String template, StandardEvaluationContext context) {
        StringBuilder result = new StringBuilder();
        int position = 0;
        while (position < template.length()) {
            int start = template.indexOf("#{", position);
            if (start < 0) {
                result.append(template, position, template.length());
                break;
            }
            int end = template.indexOf('}', start + 2);
            if (end < 0) {
                throw new IllegalArgumentException("unclosed SpEL expression in: " + template);
            }
            result.append(template, position, start);
            result.append(evaluate(template.substring(start + 2, end), context));
            position = end + 1;
        }
        return result.toString();
    }

    private String evaluate(String expression, StandardEvaluationContext context) {
        String result = parser.parseExpression(expression).getValue(context, String.class);
        return result == null ? "" : result;
    }

    private boolean isLegacyExpression(String value) {
        return value.startsWith("#") || value.startsWith("'");
    }
}
