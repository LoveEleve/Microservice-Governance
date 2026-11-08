package com.wuba.crm.tx.tcc.starter.aspect;

import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccContext;
import com.wuba.crm.tx.tcc.api.TccResult;
import com.wuba.crm.tx.tcc.api.TccTransactional;
import com.wuba.crm.tx.tcc.client.TccAnnotationTransaction;
import com.wuba.crm.tx.tcc.client.TccTransactionManager;
import com.wuba.crm.tx.tcc.client.TccTransactionOptions;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.time.Duration;
import java.util.List;

/**
 * @TccTransactional AOP。
 */
@Aspect
@Order(10)
public class TccTransactionalAspect {

    private final TccTransactionManager manager;
    private final TccExpressionResolver expressions;

    /**
     * 创建 aspect。
     *
     * @param manager     transaction manager
     * @param expressions expression resolver
     */
    public TccTransactionalAspect(
            TccTransactionManager manager, TccExpressionResolver expressions) {
        this.manager = manager;
        this.expressions = expressions;
    }

    /**
     * 包裹业务方法。
     *
     * @param joinPoint   切点
     * @param annotation  注解
     * @return 方法结果或 TccResult
     * @throws Throwable 原异常
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, TccTransactional annotation) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        String requestId = expressions.resolve(annotation.requestId(), signature.getMethod(), args);
        String bizCode = expressions.resolve(annotation.bizCode(), signature.getMethod(), args);
        String bizId = expressions.resolve(annotation.bizId(), signature.getMethod(), args);
        TccAnnotationTransaction transaction = manager.begin(new TccTransactionOptions(
                requestId, bizCode, bizId, Duration.ofSeconds(annotation.tryTimeoutSeconds())));
        TccContext.bindTransaction(transaction.xid());
        try {
            Object result = joinPoint.proceed();
            GlobalStatus status = transaction.commit();
            if (TccResult.class.isAssignableFrom(signature.getReturnType())) {
                return new TccResult(transaction.xid(), status, List.of(), "");
            }
            return result;
        } catch (TccBranchFailedException branchFailure) {
            GlobalStatus status = transaction.rollback();
            if (TccResult.class.isAssignableFrom(signature.getReturnType())) {
                return new TccResult(transaction.xid(), status, List.of(), branchFailure.getMessage());
            }
            throw branchFailure;
        } catch (Throwable failure) {
            try {
                transaction.rollback();
            } catch (RuntimeException ignored) {
                // 保留原始异常
            }
            throw failure;
        } finally {
            manager.clear();
            TccContext.clear();
        }
    }
}
