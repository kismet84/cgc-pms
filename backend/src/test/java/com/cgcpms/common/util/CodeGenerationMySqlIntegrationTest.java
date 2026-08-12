package com.cgcpms.common.util;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CGCPMS_M81_MYSQL_CODE_GENERATION", matches = "true")
class CodeGenerationMySqlIntegrationTest {

    private static final long TENANT = 0L;
    private static final long OTHER_TENANT = 810081L;
    private static final String PREFIX = "M81-RV-";
    private static final long FIRST_ID = 810081001L;
    private static final long PROJECT_ID = 810081101L;
    private static final long CONTRACT_ID = 810081102L;
    private static final long PARTY_A_ID = 810081103L;
    private static final long PARTY_B_ID = 810081104L;

    @Autowired private CodeGenerationService service;
    @Autowired private ContractRevenueMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-legacy");
    }

    @BeforeEach
    void prepareTaskOwnedFixture() {
        String url = environment.getRequiredProperty("spring.datasource.url");
        assertTrue(url.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/cgc_pms_test(?:[?].*)?$"),
                "M81 MySQL CI test requires loopback cgc_pms_test");
        assertEquals("cgc_pms_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        cleanupTaskOwnedFixture();
        jdbc.update("""
                INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type)
                VALUES(?,0,'M81-CI-PARTY-A','M81 CI甲方','OWNER'),
                      (?,0,'M81-CI-PARTY-B','M81 CI乙方','CONTRACTOR')
                """, PARTY_A_ID, PARTY_B_ID);
        jdbc.update("""
                INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,initiation_basis)
                VALUES(?,0,'M81-CI-PROJECT','M81 CodeGeneration CI项目','DRAFT','DIRECT_APPROVAL')
                """, PROJECT_ID);
        jdbc.update("""
                INSERT INTO ct_contract(
                    id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id)
                VALUES(?,0,?,'M81-CI-CONTRACT','M81 CodeGeneration CI合同','MAIN',?,?)
                """, CONTRACT_ID, PROJECT_ID, PARTY_A_ID, PARTY_B_ID);
    }

    @AfterEach
    void cleanup() {
        cleanupTaskOwnedFixture();
    }

    @Test
    void softDeletedHistoryConsumesCapacityWithoutCrossTenantLeakage() {
        String fullPrefix = fullPrefix(LocalDate.now());
        insert(FIRST_ID, fullPrefix + "998", 1);

        assertEquals(fullPrefix + "999",
                service.nextCode(mapper, ContractRevenue::getRevenueCode, PREFIX, TENANT, true));
        assertEquals(fullPrefix + "001",
                service.nextCode(mapper, ContractRevenue::getRevenueCode, PREFIX, OTHER_TENANT, true));

        insert(FIRST_ID + 1, fullPrefix + "999", 0);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.nextCode(mapper, ContractRevenue::getRevenueCode, PREFIX, TENANT, true));
        assertEquals("BUSINESS_CODE_SEQUENCE_EXHAUSTED", exception.getCode());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM contract_revenue WHERE tenant_id=? AND revenue_code=?",
                Integer.class, TENANT, fullPrefix + "1000"));
    }

    @Test
    void previousDaySequenceDoesNotConsumeTodayCapacity() {
        insert(FIRST_ID, fullPrefix(LocalDate.now().minusDays(1)) + "999", 0);

        assertEquals(fullPrefix(LocalDate.now()) + "001",
                service.nextCode(mapper, ContractRevenue::getRevenueCode, PREFIX, TENANT, true));
    }

    @Test
    void legacy1000OutranksShorterValidHistoryAndFailsClosed() {
        String fullPrefix = fullPrefix(LocalDate.now());
        insert(FIRST_ID, fullPrefix + "1000", 0);
        insert(FIRST_ID + 1, fullPrefix + "998", 0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.nextCode(mapper, ContractRevenue::getRevenueCode, PREFIX, TENANT, true));
        assertEquals("BUSINESS_CODE_SEQUENCE_EXHAUSTED", exception.getCode());
    }

    @Test
    void allM85CodeColumnsHaveTenantScopedUniqueConstraint() {
        for (CodeColumn mapperCase : m85CodeColumns()) {
            List<String> uniqueIndexes = jdbc.queryForList("""
                    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
                    FROM information_schema.statistics
                    WHERE table_schema=DATABASE() AND table_name=? AND non_unique=0
                    GROUP BY index_name
                    """, String.class, mapperCase.table());
            String requiredPrefix = "tenant_id," + mapperCase.codeColumn();
            assertTrue(uniqueIndexes.stream().anyMatch(columns ->
                            columns.equals(requiredPrefix) || columns.startsWith(requiredPrefix + ",")),
                    mapperCase.table() + " requires tenant-scoped code uniqueness");
        }
    }

    private List<CodeColumn> m85CodeColumns() {
        return List.of(
                new CodeColumn("ct_contract_change", "change_code"),
                new CodeColumn("expense_application", "expense_code"),
                new CodeColumn("md_partner", "partner_code"),
                new CodeColumn("pay_application", "apply_code"),
                new CodeColumn("pm_project", "project_code"),
                new CodeColumn("mat_purchase_order", "order_code"),
                new CodeColumn("mat_purchase_request", "request_code"),
                new CodeColumn("mat_receipt", "receipt_code"),
                new CodeColumn("mat_requisition", "requisition_code"),
                new CodeColumn("stl_settlement", "settlement_code"),
                new CodeColumn("sub_measure", "measure_code"),
                new CodeColumn("sub_task", "task_code"),
                new CodeColumn("var_order", "var_code"));
    }

    @Test
    void uniqueConstraintRejectsOneOfTwoConcurrentActiveRows() throws Exception {
        String code = service.nextCode(
                mapper, ContractRevenue::getRevenueCode, PREFIX, TENANT, true);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> insertConcurrently(FIRST_ID, code, ready, start)),
                    executor.submit(() -> insertConcurrently(FIRST_ID + 1, code, ready, start)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            long successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) successes++;
            }
            assertEquals(1, successes);
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM contract_revenue WHERE tenant_id=? AND revenue_code=? AND deleted_flag=0",
                Integer.class, TENANT, code));
    }

    private boolean insertConcurrently(long id, String code, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            insert(id, code, 0);
            return true;
        } catch (DuplicateKeyException expected) {
            return false;
        }
    }

    private void insert(long id, String code, int deletedFlag) {
        jdbc.update("""
                INSERT INTO contract_revenue(
                    id,tenant_id,project_id,contract_id,revenue_code,revenue_date,
                    approval_status,deleted_flag,created_at,updated_at)
                VALUES(?,?,?,?,?,CURRENT_DATE,'DRAFT',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, TENANT, PROJECT_ID, CONTRACT_ID, code, deletedFlag);
    }

    private String fullPrefix(LocalDate date) {
        return PREFIX + date.format(DateTimeUtils.DATE_COMPACT) + "-";
    }

    private void cleanupTaskOwnedFixture() {
        jdbc.update("""
                DELETE FROM contract_revenue
                WHERE tenant_id=? AND (id IN (?,?) OR revenue_code LIKE ?)
                """, TENANT, FIRST_ID, FIRST_ID + 1, PREFIX + "%");
        jdbc.update("DELETE FROM ct_contract WHERE tenant_id=? AND id=? AND contract_code='M81-CI-CONTRACT'",
                TENANT, CONTRACT_ID);
        jdbc.update("DELETE FROM pm_project WHERE tenant_id=? AND id=? AND project_code='M81-CI-PROJECT'",
                TENANT, PROJECT_ID);
        jdbc.update("DELETE FROM md_partner WHERE tenant_id=? AND id IN (?,?) AND partner_code LIKE 'M81-CI-PARTY-%'",
                TENANT, PARTY_A_ID, PARTY_B_ID);
    }

    private record CodeColumn(String table, String codeColumn) {
    }
}
