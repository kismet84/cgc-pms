package com.cgcpms.common.util;

import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.document.mapper.DocumentTemplateMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Autowired private CodeGenerationService service;
    @Autowired private ContractRevenueMapper mapper;
    @Autowired private BidCostMapper bidCostMapper;
    @Autowired private ProjectBudgetMapper projectBudgetMapper;
    @Autowired private DocumentTemplateMapper documentTemplateMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;

    private long projectId;
    private long contractId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @BeforeEach
    void requireDedicatedLocalDatabase() {
        String url = environment.getRequiredProperty("spring.datasource.url");
        assertTrue(url.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/cgc_pms_demo_v2(?:[?].*)?$"),
                "M81 MySQL test requires loopback cgc_pms_demo_v2");
        assertTrue(Files.isRegularFile(Path.of("..", ".codex-autopilot", "ALLOW_TEST_DATA_RESET")),
                "M81 MySQL test requires reset marker");
        assertEquals("cgc_pms_demo_v2", jdbc.queryForObject("SELECT DATABASE()", String.class));
        long[] scope = jdbc.queryForObject("""
                SELECT project_id,id FROM ct_contract
                WHERE tenant_id=0 AND deleted_flag=0 AND project_id IS NOT NULL
                ORDER BY id LIMIT 1
                """, (resultSet, rowNum) -> new long[]{resultSet.getLong(1), resultSet.getLong(2)});
        projectId = scope[0];
        contractId = scope[1];
        cleanup();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM contract_revenue WHERE tenant_id=? AND revenue_code LIKE ?", TENANT, PREFIX + "%");
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
    @Transactional
    void allSixDeletedCodeMappersPreferLegacy1000AndKeepTenantIsolation() {
        List<MapperCase> cases = List.of(
                new MapperCase("bid_cost", "bid_code", "M81MAP-BID-", bidCostMapper),
                new MapperCase("project_budget", "budget_code", "M81MAP-BUD-", projectBudgetMapper),
                new MapperCase("biz_document_template", "template_code", "M81MAP-TPL-", documentTemplateMapper),
                new MapperCase("ct_contract", "contract_code", "M81MAP-CT-", contractMapper),
                new MapperCase("pay_record", "record_code", "M81MAP-PMT-", payRecordMapper),
                new MapperCase("contract_revenue", "revenue_code", "M81MAP-RV-", mapper));

        for (MapperCase mapperCase : cases) {
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM " + mapperCase.table() + " WHERE tenant_id=0 ORDER BY id LIMIT 2",
                    Long.class);
            if (ids.size() < 2 && mapperCase.table().equals("contract_revenue")) {
                insert(FIRST_ID, mapperCase.prefix() + "998", 0);
                ids = jdbc.queryForList(
                        "SELECT id FROM contract_revenue WHERE tenant_id=0 ORDER BY id LIMIT 2", Long.class);
            }
            assertEquals(2, ids.size(), mapperCase.table() + " requires two dedicated demo rows");
            jdbc.update("UPDATE " + mapperCase.table() + " SET " + mapperCase.codeColumn()
                    + "=?, deleted_flag=1 WHERE id=?", mapperCase.prefix() + "1000", ids.get(0));
            jdbc.update("UPDATE " + mapperCase.table() + " SET " + mapperCase.codeColumn()
                    + "=?, deleted_flag=0 WHERE id=?", mapperCase.prefix() + "998", ids.get(1));

            assertEquals(mapperCase.prefix() + "1000",
                    mapperCase.source().selectLastCodeByPrefix(mapperCase.prefix(), TENANT));
            assertNull(mapperCase.source().selectLastCodeByPrefix(mapperCase.prefix(), OTHER_TENANT));
        }
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
                """, id, TENANT, projectId, contractId, code, deletedFlag);
    }

    private String fullPrefix(LocalDate date) {
        return PREFIX + date.format(DateTimeUtils.DATE_COMPACT) + "-";
    }

    private record MapperCase(String table, String codeColumn, String prefix, DeletedCodeSource source) {
    }
}
