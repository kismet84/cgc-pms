package com.cgcpms.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.cost.entity.CostSubject;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
@Import(DashboardFinanceQueryBudgetTest.QueryCountConfig.class)
@DisplayName("M91 F08 dashboard finance query budgets")
class DashboardFinanceQueryBudgetTest extends DashboardServiceTestSupport {

    private static final ThreadLocal<AtomicInteger> SQL_COUNT = new ThreadLocal<>();

    @Autowired private SqlSessionTemplate sqlSessionTemplate;
    @Autowired private ProjectBudgetMapper projectBudgetMapper;
    @Autowired private ProjectBudgetLineMapper projectBudgetLineMapper;

    @AfterEach
    void clearUser() {
        SQL_COUNT.remove();
        com.cgcpms.auth.context.UserContext.clear();
    }

    @Test
    @Transactional
    void locksFourModeBudgetsAndKeepsAllProjectQueriesConstant() {
        SeedResult first = seed("M91_FIN_Q_001");
        seedActiveBudget(first, "M91_FIN_Q_001");
        String historyMonth = YearMonth.now().minusMonths(1).toString();

        int singleCurrent = measure(() -> dashboardService.getFinanceView(first.projectId));
        int singleHistory = measure(() -> dashboardService.getFinanceView(first.projectId, historyMonth));
        int allCurrentOne = measure(() -> dashboardService.getFinanceView(null));
        int allHistoryOne = measure(() -> dashboardService.getFinanceView(null, historyMonth));

        for (int index = 2; index <= 50; index++) {
            seed("M91_FIN_Q_" + String.format("%03d", index));
        }
        int allCurrentFifty = measure(() -> dashboardService.getFinanceView(null));
        int allHistoryFifty = measure(() -> dashboardService.getFinanceView(null, historyMonth));

        System.out.printf("M91_F08_QUERY_BUDGET singleCurrent=%d singleHistory=%d allCurrent=%d allHistory=%d%n",
                singleCurrent, singleHistory, allCurrentOne, allHistoryOne);
        assertEquals(17, singleCurrent, "单项目实时模式 SQL 预算漂移");
        assertEquals(6, singleHistory, "单项目历史模式 SQL 预算漂移");
        assertEquals(18, allCurrentOne, "全项目实时模式 SQL 预算漂移");
        assertEquals(3, allHistoryOne, "全项目历史模式 SQL 预算漂移");
        assertEquals(allCurrentOne, allCurrentFifty, "当前全项目查询数不得随项目数增长");
        assertEquals(allHistoryOne, allHistoryFifty, "历史全项目查询数不得随项目数增长");
        assertTrue(singleCurrent > 0 && singleHistory > 0 && allCurrentOne > 0 && allHistoryOne > 0);
    }

    private void seedActiveBudget(SeedResult seed, String suffix) {
        Long subjectId = costSubjectMapper.selectOne(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getTenantId, TENANT_ID)
                .eq(CostSubject::getSubjectCode, "SUBJ-" + suffix)).getId();
        ProjectBudget budget = new ProjectBudget();
        budget.setTenantId(TENANT_ID);
        budget.setProjectId(seed.projectId);
        budget.setBudgetCode("BUD-" + suffix);
        budget.setVersionNo("V1");
        budget.setBudgetName("Dashboard budget " + suffix);
        budget.setTotalAmount(new BigDecimal("1000.00"));
        budget.setApprovalStatus("APPROVED");
        budget.setStatus("ACTIVE");
        budget.setActiveFlag(1);
        budget.setActiveToken(seed.projectId);
        budget.setEffectiveAt(LocalDateTime.now());
        budget.setVersion(0);
        projectBudgetMapper.insert(budget);

        ProjectBudgetLine line = new ProjectBudgetLine();
        line.setTenantId(TENANT_ID);
        line.setBudgetId(budget.getId());
        line.setProjectId(seed.projectId);
        line.setCostSubjectId(subjectId);
        line.setBudgetAmount(new BigDecimal("1000.00"));
        line.setReservedAmount(new BigDecimal("200.00"));
        line.setConsumedAmount(new BigDecimal("300.00"));
        projectBudgetLineMapper.insert(line);
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

    @TestConfiguration
    static class QueryCountConfig {
        @Bean
        Interceptor dashboardFinanceSqlCountInterceptor() {
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
            if (count != null) count.incrementAndGet();
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
