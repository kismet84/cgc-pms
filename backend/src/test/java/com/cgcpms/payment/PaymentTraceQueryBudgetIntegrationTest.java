package com.cgcpms.payment;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.service.PaymentTraceService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
@Import(PaymentTraceQueryBudgetIntegrationTest.QueryCountConfiguration.class)
class PaymentTraceQueryBudgetIntegrationTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;
    private static final int EXPECTED_MYBATIS_READS = 30;

    @Autowired private PaymentTraceService traceService;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private PayApplicationMapper applicationMapper;

    @AfterEach
    void clearContext() {
        QueryCountInterceptor.disable();
        TestUserContext.clear();
    }

    @ParameterizedTest(name = "byProject({0} applications) keeps 30 MyBatis reads")
    @ValueSource(ints = {1, 100})
    @Transactional
    void oneAndOneHundredApplicationsHaveSameExactMyBatisReadBudget(int applicationCount) {
        TestUserContext.setUser(TENANT_ID, USER_ID, "payment-trace-budget", List.of("SUPER_ADMIN"));
        long projectId = 994_910_000L + applicationCount;
        long contractId = 994_920_000L + applicationCount;
        seedProjectAndContract(projectId, contractId, applicationCount);
        seedDraftApplications(projectId, contractId, applicationCount);

        QueryCountInterceptor.enable();
        List<com.cgcpms.payment.vo.PaymentTraceVO> traces;
        try {
            traces = traceService.byProject(projectId);
        } finally {
            QueryCountInterceptor.disable();
        }

        assertEquals(applicationCount, traces.size());
        assertEquals(LongStream.rangeClosed(1, applicationCount)
                        .map(index -> 994_930_000L + applicationCount * 1_000L + index).boxed().toList(),
                traces.stream().map(trace -> trace.getPaymentApplication().getId()).toList());
        assertEquals(EXPECTED_MYBATIS_READS, QueryCountInterceptor.count(),
                "byProject must use 4 anchor/access reads plus 26 batch reads including one access read; "
                        + "JdbcTemplate payment-document read is verified separately by Mockito");
    }

    private void seedProjectAndContract(long projectId, long contractId, int suffix) {
        PmProject project = new PmProject();
        project.setId(projectId);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("PAY-TRACE-Q-" + suffix);
        project.setProjectName("Payment trace query budget " + suffix);
        project.setStatus("ACTIVE");
        projectMapper.insert(project);

        CtContract contract = new CtContract();
        contract.setId(contractId);
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(projectId);
        contract.setContractCode("PAY-TRACE-Q-C-" + suffix);
        contract.setContractName("Payment trace query budget contract " + suffix);
        contract.setContractType("SUBCONTRACT");
        contract.setContractAmount(new BigDecimal("1000.00"));
        contract.setCurrentAmount(new BigDecimal("1000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setContractStatus("PERFORMING");
        contract.setApprovalStatus("APPROVED");
        contract.setVersion(0);
        contractMapper.insert(contract);
    }

    private void seedDraftApplications(long projectId, long contractId, int applicationCount) {
        for (int index = 1; index <= applicationCount; index++) {
            PayApplication application = new PayApplication();
            application.setId(994_930_000L + applicationCount * 1_000L + index);
            application.setTenantId(TENANT_ID);
            application.setProjectId(projectId);
            application.setContractId(contractId);
            application.setApplyCode("PAY-TRACE-Q-" + applicationCount + "-" + index);
            application.setApplyAmount(BigDecimal.ZERO);
            application.setApprovedAmount(BigDecimal.ZERO);
            application.setActualPayAmount(BigDecimal.ZERO);
            application.setPayType("OTHER");
            application.setPayStatus("PENDING");
            application.setApprovalStatus("DRAFT");
            application.setIntegrityVersion("LEGACY_UNVERIFIED");
            application.setVersion(0);
            applicationMapper.insert(application);
        }
    }

    @TestConfiguration
    static class QueryCountConfiguration {
        @Bean
        Interceptor paymentTraceQueryCountInterceptor() {
            return new QueryCountInterceptor();
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class,
                    org.apache.ibatis.session.ResultHandler.class}))
    static final class QueryCountInterceptor implements Interceptor {
        private static final AtomicInteger QUERY_COUNT = new AtomicInteger();
        private static final ThreadLocal<Boolean> ENABLED = ThreadLocal.withInitial(() -> false);

        static void enable() {
            QUERY_COUNT.set(0);
            ENABLED.set(true);
        }

        static void disable() {
            ENABLED.remove();
        }

        static int count() {
            return QUERY_COUNT.get();
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            if (ENABLED.get()) QUERY_COUNT.incrementAndGet();
            return invocation.proceed();
        }

        @Override
        public Object plugin(Object target) {
            return Plugin.wrap(target, this);
        }

        @Override
        public void setProperties(Properties properties) {
            // no-op
        }
    }
}
