package com.wuba.crm.tx.tcc.starter.aspect;

import com.wuba.crm.tx.tcc.api.TccBranch;
import com.wuba.crm.tx.tcc.api.TccContext;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.client.TccAnnotationTransaction;
import com.wuba.crm.tx.tcc.client.TccTransactionManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

/**
 * @TccBranch AOP。
 */
@Aspect
@Order(20)
public class TccBranchAspect {

    private final TccTransactionManager manager;
    private final TccExpressionResolver expressions;

    /**
     * 创建 aspect。
     *
     * @param manager     transaction manager
     * @param expressions expression resolver
     */
    public TccBranchAspect(
            TccTransactionManager manager, TccExpressionResolver expressions) {
        this.manager = manager;
        this.expressions = expressions;
    }

    /**
     * 包裹分支方法。
     *
     * @param joinPoint  切点
     * @param annotation 注解
     * @return 分支结果
     * @throws Throwable 原异常
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, TccBranch annotation) throws Throwable {
        TccAnnotationTransaction transaction = manager.current();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        String resourceKey = expressions.resolve(annotation.resourceKey(), signature.getMethod(), args);
        String applicationData = expressions.resolve(annotation.applicationData(), signature.getMethod(), args);
        String callbackEndpoint = expressions.resolve(annotation.callbackEndpoint(), signature.getMethod(), args);
        long branchId = transaction.registerBranch(
                resourceKey, annotation.actionName(), callbackEndpoint, applicationData);
        TccContext.bindBranch(transaction.xid(), branchId);
        try {
            Object result = joinPoint.proceed();
            if (!(result instanceof TccTryResult tryResult)) {
                throw new IllegalStateException("@TccBranch method must return TccTryResult");
            }
            transaction.report(branchId, tryResult);
            if (tryResult != TccTryResult.SUCCESS) {
                throw new TccBranchFailedException("branch try failed: " + resourceKey);
            }
            return result;
        } catch (RuntimeException failure) {
            if (!(failure instanceof TccBranchFailedException)) {
                transaction.report(branchId, TccTryResult.FAILURE);
            }
            throw failure;
        } finally {
            TccContext.bindTransaction(transaction.xid());
        }
    }
}
