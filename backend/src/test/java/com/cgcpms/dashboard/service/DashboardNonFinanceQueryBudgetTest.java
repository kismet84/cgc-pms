package com.cgcpms.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.tech.entity.TechItem;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
@Import(DashboardNonFinanceQueryBudgetTest.QueryCountConfig.class)
@DisplayName("M91 F08 dashboard non-finance query budgets")
class DashboardNonFinanceQueryBudgetTest extends DashboardServiceTestSupport {

    private static final ThreadLocal<AtomicInteger> SQL_COUNT = new ThreadLocal<>();

    @Autowired private SqlSessionTemplate sqlSessionTemplate;
    @Autowired private CtContractChangeMapper contractChangeMapper;

    @AfterEach
    void clearContext() {
        SQL_COUNT.remove();
        com.cgcpms.auth.context.UserContext.clear();
    }

    @Test
    @Transactional
    void locksNonFinanceBudgetsAndKeepsAllProjectQueriesConstant() {
        SeedResult first = seed("M91_NON_FIN_Q_001");
        insertContractChange(first.projectId, "M91_NON_FIN_Q_001");
        insertTechItem(first);
        String selectedMonth = YearMonth.now().toString();

        Counts singleProject = measureSingleModes(first.projectId, selectedMonth);
        Counts oneProject = measureAllModes(selectedMonth);

        for (int index = 2; index <= 50; index++) {
            seed("M91_NON_FIN_Q_" + String.format("%03d", index));
        }
        Counts fiftyProjects = measureAllModes(selectedMonth);

        assertEquals(new Counts(9, 9, 9, 9, 14, 14, 12, 12, 5, 5), singleProject,
                "非财务单项目精确 SQL 预算漂移");
        assertEquals(new Counts(8, 8, 9, 9, 14, 14, 12, 12, 5, 5), oneProject,
                "非财务全项目精确 SQL 预算漂移");
        assertEquals(oneProject, fiftyProjects, "非财务查询数不得随项目数增长");
    }

    private Counts measureSingleModes(Long projectId, String selectedMonth) {
        return new Counts(
                measure(() -> dashboardService.getProjectManagerView(projectId)),
                measure(() -> dashboardService.getProjectManagerView(projectId, selectedMonth)),
                measure(() -> dashboardService.getBusinessManagerView(projectId)),
                measure(() -> dashboardService.getBusinessManagerView(projectId, selectedMonth)),
                measure(() -> dashboardService.getPurchaseManagerView(projectId)),
                measure(() -> dashboardService.getPurchaseManagerView(projectId, selectedMonth)),
                measure(() -> dashboardService.getProductionManagerView(projectId)),
                measure(() -> dashboardService.getProductionManagerView(projectId, selectedMonth)),
                measure(() -> dashboardService.getChiefEngineerView(projectId)),
                measure(() -> dashboardService.getChiefEngineerView(projectId, selectedMonth)));
    }

    private Counts measureAllModes(String selectedMonth) {
        return new Counts(
                measure(() -> dashboardService.getProjectManagerView(null)),
                measure(() -> dashboardService.getProjectManagerView(null, selectedMonth)),
                measure(() -> dashboardService.getBusinessManagerView(null)),
                measure(() -> dashboardService.getBusinessManagerView(null, selectedMonth)),
                measure(() -> dashboardService.getPurchaseManagerView(null)),
                measure(() -> dashboardService.getPurchaseManagerView(null, selectedMonth)),
                measure(() -> dashboardService.getProductionManagerView(null)),
                measure(() -> dashboardService.getProductionManagerView(null, selectedMonth)),
                measure(() -> dashboardService.getChiefEngineerView(null)),
                measure(() -> dashboardService.getChiefEngineerView(null, selectedMonth)));
    }

    private void insertContractChange(Long projectId, String suffix) {
        Long contractId = ctContractMapper.selectOne(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTenantId, TENANT_ID)
                .eq(CtContract::getProjectId, projectId)).getId();
        CtContractChange change = new CtContractChange();
        change.setTenantId(TENANT_ID);
        change.setProjectId(projectId);
        change.setContractId(contractId);
        change.setChangeCode("Q-CHANGE-" + suffix);
        change.setChangeName("Query budget change");
        change.setChangeType("AMOUNT");
        change.setBeforeAmount(BigDecimal.ZERO);
        change.setChangeAmount(BigDecimal.ONE);
        change.setAfterAmount(BigDecimal.ONE);
        change.setApprovalStatus("APPROVED");
        change.setEffectiveFlag(1);
        change.setCreatedTime(LocalDateTime.now());
        change.setUpdatedTime(LocalDateTime.now());
        contractChangeMapper.insert(change);
    }

    private void insertTechItem(SeedResult seed) {
        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(seed.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("Q-TECH-" + seed.projectId);
        item.setItemTitle("Query budget tech item");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now());
        item.setDueDate(LocalDateTime.now().plusDays(1));
        item.setResponsibleUserId(seed.signalUserId);
        techItemMapper.insert(item);
    }

    private int measure(Runnable query) {
        sqlSessionTemplate.clearCache();
        AtomicInteger count = new AtomicInteger();
        SQL_COUNT.set(count);
        try {
            query.run();
            return count.get();
        } finally {
            SQL_COUNT.remove();
        }
    }

    private record Counts(int projectCurrent, int projectSelected,
                          int businessCurrent, int businessSelected,
                          int purchaseCurrent, int purchaseSelected,
                          int productionCurrent, int productionSelected,
                          int chiefCurrent, int chiefSelected) {
    }

    @TestConfiguration
    static class QueryCountConfig {
        @Bean
        Interceptor dashboardNonFinanceSqlCountInterceptor() {
            return new SqlCountInterceptor();
        }
    }

    @Intercepts(@Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class,
                    org.apache.ibatis.session.RowBounds.class,
                    org.apache.ibatis.session.ResultHandler.class}))
    static class SqlCountInterceptor implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            AtomicInteger count = SQL_COUNT.get();
            if (count != null) {
                count.incrementAndGet();
            }
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
