package com.wuba.crm.tx.tcc.starter;

import com.wuba.crm.tx.tcc.api.BranchStatus;
import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccBeginRequest;
import com.wuba.crm.tx.tcc.api.TccBeginResult;
import com.wuba.crm.tx.tcc.api.TccBranch;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterRequest;
import com.wuba.crm.tx.tcc.api.TccBranchRegisterResult;
import com.wuba.crm.tx.tcc.api.TccBranchReportRequest;
import com.wuba.crm.tx.tcc.api.TccCommitResult;
import com.wuba.crm.tx.tcc.api.TccResult;
import com.wuba.crm.tx.tcc.api.TccRollbackResult;
import com.wuba.crm.tx.tcc.api.TccTransactional;
import com.wuba.crm.tx.tcc.api.TccTryResult;
import com.wuba.crm.tx.tcc.client.TccTransactionManager;
import com.wuba.crm.tx.tcc.client.internal.TccCoordinatorOperations;
import com.wuba.crm.tx.tcc.starter.aspect.TccBranchAspect;
import com.wuba.crm.tx.tcc.starter.aspect.TccExpressionResolver;
import com.wuba.crm.tx.tcc.starter.aspect.TccTransactionalAspect;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 @TccTransactional + @TccBranch 的 AOP 语义。
 */
class TccAnnotationAspectTest {

    @Test
    void annotationSuccess_commits() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TestConfig.class);
        TccAnnotationService service = context.getBean(TccAnnotationService.class);
        FakeCoordinator coordinator = context.getBean(FakeCoordinator.class);

        TccResult result = service.assign("req-1", "lead", "1");

        assertEquals(GlobalStatus.CONFIRMED, result.status());
        assertEquals(List.of(
                "begin",
                "register:quota:1",
                "report:1:TRY_SUCCESS",
                "commit"), coordinator.calls);
        context.close();
    }

    @Test
    void branchFailure_rollsBack() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TestConfig.class);
        TccAnnotationService service = context.getBean(TccAnnotationService.class);
        FakeCoordinator coordinator = context.getBean(FakeCoordinator.class);

        TccResult result = service.failingAssign("req-2", "lead", "2");
        assertEquals(GlobalStatus.CANCELLED, result.status());

        assertEquals(List.of(
                "begin",
                "register:quota:2",
                "report:1:TRY_FAIL",
                "rollback"), coordinator.calls);
        context.close();
    }

    /**
     * 测试业务服务。
     */
    static class TccAnnotationService {

        private final QuotaBranchGateway gateway;

        TccAnnotationService(QuotaBranchGateway gateway) {
            this.gateway = gateway;
        }

        @TccTransactional(requestId = "#a0", bizCode = "lead", bizId = "#a2")
        public TccResult assign(String requestId, String bizCode, String bizId) {
            gateway.reserve("1");
            return null;
        }

        @TccTransactional(requestId = "#a0", bizCode = "lead", bizId = "#a2")
        public TccResult failingAssign(String requestId, String bizCode, String bizId) {
            gateway.fail("2");
            return null;
        }
    }

    /**
     * 分支网关。
     */
    static class QuotaBranchGateway {

        @TccBranch(
                actionName = "reserveQuota",
                resourceKey = "'quota:' + #a0",
                callbackEndpoint = "127.0.0.1:9092",
                applicationData = "#a0")
        public TccTryResult reserve(String userId) {
            return TccTryResult.SUCCESS;
        }

        @TccBranch(
                actionName = "reserveQuota",
                resourceKey = "'quota:' + #a0",
                callbackEndpoint = "127.0.0.1:9092",
                applicationData = "#a0")
        public TccTryResult fail(String userId) {
            return TccTryResult.FAILURE;
        }
    }

    /**
     * 测试装配。
     */
    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        FakeCoordinator fakeCoordinator() {
            return new FakeCoordinator();
        }

        @Bean
        TccTransactionManager tccTransactionManager(FakeCoordinator coordinator) {
            return new TccTransactionManager(coordinator);
        }

        @Bean
        TccExpressionResolver tccExpressionResolver() {
            return new TccExpressionResolver();
        }

        @Bean
        TccTransactionalAspect tccTransactionalAspect(
                TccTransactionManager manager, TccExpressionResolver resolver) {
            return new TccTransactionalAspect(manager, resolver);
        }

        @Bean
        TccBranchAspect tccBranchAspect(
                TccTransactionManager manager, TccExpressionResolver resolver) {
            return new TccBranchAspect(manager, resolver);
        }

        @Bean
        QuotaBranchGateway quotaBranchGateway() {
            return new QuotaBranchGateway();
        }

        @Bean
        TccAnnotationService tccAnnotationService(QuotaBranchGateway gateway) {
            return new TccAnnotationService(gateway);
        }
    }

    /**
     * 记录协议调用的假 Coordinator。
     */
    static class FakeCoordinator implements TccCoordinatorOperations {

        private final List<String> calls = new ArrayList<>();

        @Override
        public TccBeginResult begin(TccBeginRequest request) {
            calls.add("begin");
            return new TccBeginResult(10L, GlobalStatus.TRYING);
        }

        @Override
        public TccBranchRegisterResult registerBranch(TccBranchRegisterRequest request) {
            calls.add("register:" + request.resourceKey());
            return new TccBranchRegisterResult(1L, BranchStatus.TRYING);
        }

        @Override
        public void reportBranch(TccBranchReportRequest request) {
            calls.add("report:" + request.branchId() + ":" + request.status());
        }

        @Override
        public TccCommitResult commit(long xid) {
            calls.add("commit");
            return new TccCommitResult(GlobalStatus.CONFIRMED);
        }

        @Override
        public TccRollbackResult rollback(long xid) {
            calls.add("rollback");
            return new TccRollbackResult(GlobalStatus.CANCELLED);
        }
    }
}
