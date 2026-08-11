package com.cgcpms.material;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.service.CtContractItemService;
import com.cgcpms.material.service.MdMaterialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CGCPMS_MATERIAL_DELETE_MYSQL_CONCURRENCY", matches = "true")
class MdMaterialDeleteMySqlConcurrencyTest {

    private static final long TENANT = 0L;
    private static final long USER = 1L;
    private static final long PROJECT = 9_985_001L;
    private static final long CONTRACT = 9_985_002L;
    private static final long MATERIAL = 9_985_003L;
    private static final long PARTY_A = 9_985_004L;
    private static final long PARTY_B = 9_985_005L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MdMaterialService materialService;
    @Autowired private CtContractItemService contractItemService;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-legacy");
    }

    @BeforeEach
    void seed() {
        transactions = new TransactionTemplate(transactionManager);
        cleanupRows();
        jdbc.update("""
                INSERT INTO pm_project
                    (id,tenant_id,project_code,project_name,status,approval_status,deleted_flag)
                VALUES(?,?,'MATERIAL-DELETE-CONCURRENCY','材料删除并发测试','ACTIVE','APPROVED',0)
                """, PROJECT, TENANT);
        jdbc.update("""
                INSERT INTO md_partner
                    (id,tenant_id,partner_code,partner_name,partner_type,status,deleted_flag)
                VALUES(?,?,'MATERIAL-DELETE-PARTY-A','材料删除甲方','CUSTOMER','ENABLE',0),
                      (?,?,'MATERIAL-DELETE-PARTY-B','材料删除乙方','SUPPLIER','ENABLE',0)
                """, PARTY_A, TENANT, PARTY_B, TENANT);
        jdbc.update("""
                INSERT INTO ct_contract
                    (id,tenant_id,project_id,contract_code,contract_name,contract_type,
                     party_a_id,party_b_id,contract_amount,current_amount,paid_amount,tax_rate,
                     contract_status,approval_status,deleted_flag)
                VALUES(?,?,?,'MATERIAL-DELETE-CONTRACT','材料删除并发合同','SUB',
                       ?,?,100,100,0,0,'DRAFT','DRAFT',0)
                """, CONTRACT, TENANT, PROJECT, PARTY_A, PARTY_B);
        jdbc.update("""
                INSERT INTO md_material
                    (id,tenant_id,material_code,material_name,unit,status,deleted_flag)
                VALUES(?,?,'MATERIAL-DELETE-CONCURRENCY','材料删除并发测试','个','ENABLE',0)
                """, MATERIAL, TENANT);
    }

    @AfterEach
    void cleanup() {
        TestUserContext.clear();
        cleanupRows();
    }

    @Test
    void committedReferenceMakesWaitingDeleteReject() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attempting = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var reference = executor.submit(() -> holdReference(holding, release));
            assertTrue(holding.await(10, TimeUnit.SECONDS));
            var deletion = executor.submit(() -> deleteMaterial(attempting));
            assertTrue(attempting.await(10, TimeUnit.SECONDS));
            try {
                assertThrows(TimeoutException.class, () -> deletion.get(1, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertEquals("REFERENCE_OK", reference.get(15, TimeUnit.SECONDS));
            assertEquals("MATERIAL_REFERENCED", deletion.get(15, TimeUnit.SECONDS));
        }
        assertDatabaseState(0, 1);
    }

    @Test
    void committedDeleteMakesWaitingReferenceReject() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attempting = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> holdDelete(holding, release));
            assertTrue(holding.await(10, TimeUnit.SECONDS));
            var reference = executor.submit(() -> createReference(attempting));
            assertTrue(attempting.await(10, TimeUnit.SECONDS));
            try {
                assertThrows(TimeoutException.class, () -> reference.get(1, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertEquals("DELETE_OK", deletion.get(15, TimeUnit.SECONDS));
            assertEquals("MATERIAL_INVALID", reference.get(15, TimeUnit.SECONDS));
        }
        assertDatabaseState(1, 0);
    }

    private String holdReference(CountDownLatch holding, CountDownLatch release) {
        authenticateSuperAdmin();
        try {
            return transactions.execute(status -> {
                createReferenceItem();
                holding.countDown();
                await(release);
                return "REFERENCE_OK";
            });
        } finally {
            TestUserContext.clear();
        }
    }

    private String holdDelete(CountDownLatch holding, CountDownLatch release) {
        authenticateSuperAdmin();
        try {
            return transactions.execute(status -> {
                materialService.delete(MATERIAL);
                holding.countDown();
                await(release);
                return "DELETE_OK";
            });
        } finally {
            TestUserContext.clear();
        }
    }

    private String deleteMaterial(CountDownLatch attempting) {
        authenticateSuperAdmin();
        try {
            attempting.countDown();
            materialService.delete(MATERIAL);
            return "DELETE_OK";
        } catch (BusinessException error) {
            return error.getCode();
        } finally {
            TestUserContext.clear();
        }
    }

    private String createReference(CountDownLatch attempting) {
        authenticateSuperAdmin();
        try {
            attempting.countDown();
            createReferenceItem();
            return "REFERENCE_OK";
        } catch (BusinessException error) {
            return error.getCode();
        } finally {
            TestUserContext.clear();
        }
    }

    private void createReferenceItem() {
        CtContractItem item = new CtContractItem();
        item.setContractId(CONTRACT);
        item.setMaterialId(MATERIAL);
        item.setItemName("并发引用");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(BigDecimal.ONE);
        contractItemService.create(item);
    }

    private void authenticateSuperAdmin() {
        TestUserContext.setUser(TENANT, USER, "m89-super-admin", List.of("SUPER_ADMIN"));
    }

    private void assertDatabaseState(int deleted, long references) {
        assertEquals(deleted, jdbc.queryForObject(
                "SELECT deleted_flag FROM md_material WHERE id=? AND tenant_id=?", Integer.class, MATERIAL, TENANT));
        assertEquals(references, jdbc.queryForObject("""
                SELECT COUNT(*) FROM ct_contract_item
                WHERE tenant_id=? AND material_id=? AND deleted_flag=0
                """, Long.class, TENANT, MATERIAL));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("lock release timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("lock wait interrupted", error);
        }
    }

    private void cleanupRows() {
        jdbc.update("DELETE FROM ct_contract_item WHERE tenant_id=? AND contract_id=?", TENANT, CONTRACT);
        jdbc.update("DELETE FROM ct_contract WHERE tenant_id=? AND id=?", TENANT, CONTRACT);
        jdbc.update("DELETE FROM md_partner WHERE tenant_id=? AND id IN (?,?)", TENANT, PARTY_A, PARTY_B);
        jdbc.update("DELETE FROM pm_project WHERE tenant_id=? AND id=?", TENANT, PROJECT);
        jdbc.update("DELETE FROM md_material WHERE tenant_id=? AND id=?", TENANT, MATERIAL);
    }
}
