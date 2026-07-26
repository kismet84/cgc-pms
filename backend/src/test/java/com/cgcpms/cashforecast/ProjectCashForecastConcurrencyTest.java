package com.cgcpms.cashforecast;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashforecast.dto.CashForecastModels.CycleRequest;
import com.cgcpms.cashforecast.service.ProjectCashForecastService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
class ProjectCashForecastConcurrencyTest {
    private static final long TENANT = 99194L;
    private static final long PROJECT = 99194001L;
    private static final LocalDate DATE = LocalDate.of(2099, 4, 1);

    @Autowired ProjectCashForecastService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,?,'CF-CONCURRENT','预测并发测试','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",
                PROJECT, TENANT);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM finance_audit_event WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM cash_forecast_line WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM cash_forecast_cycle WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM pm_project WHERE tenant_id=? AND id=?", TENANT, PROJECT);
        UserContext.clear();
    }

    @Test
    void concurrentCycleCreationSerializesVersionNumbers() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> create(start, 1L));
            var second = pool.submit(() -> create(start, 2L));
            start.countDown();

            Set<Integer> versions = Set.of(version(first.get()), version(second.get()));
            assertEquals(Set.of(1, 2), versions);
        }
    }

    private Map<String,Object> create(CountDownLatch start, long userId) throws Exception {
        asAdmin(userId);
        try {
            start.await();
            return service.createCycle(new CycleRequest(PROJECT, "并发预测-" + userId,
                    DATE, DATE, DATE, "BASE", BigDecimal.ZERO, null));
        } finally {
            UserContext.clear();
        }
    }

    private static int version(Map<String,Object> result) {
        return ((Number)((Map<?,?>)result.get("cycle")).get("version_no")).intValue();
    }

    private static void asAdmin(long userId) {
        UserContext.set(Jwts.claims().subject("user-" + userId).add("userId", userId)
                .add("username", "user-" + userId).add("tenantId", TENANT)
                .add("roleCodes", List.of("ADMIN")).build());
    }
}
