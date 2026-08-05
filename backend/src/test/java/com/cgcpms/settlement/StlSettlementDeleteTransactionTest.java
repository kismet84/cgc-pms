package com.cgcpms.settlement;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.service.StlSettlementWriteService;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.cgcpms.projectfile.ProjectFileService;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "minio.enabled=true"
})
@ActiveProfiles("local")
@DisplayName("结算删除级联事务故障注入回归")
class StlSettlementDeleteTransactionTest {

    @MockitoBean
    private ProjectFileService projectFileService;

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long SETTLEMENT_ID = 947201L;
    private static final long ITEM_ID = 947202L;
    private static final long MEASURE_LINK_ID = 947203L;
    private static final long FILE_ID = 947204L;
    private static final long MEASURE_ID = 947205L;
    private static final String SETTLEMENT_CODE = "STL-TXN-ROLLBACK-947201";

    @Autowired private StlSettlementWriteService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private StlSettlementMapper settlementMapper;
    @MockitoBean private MinioClient minioClient;

    @BeforeEach
    void setUp() {
        reset(settlementMapper);
        cleanup();
        TestUserContext.setAdmin(TENANT_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO stl_settlement
                    (id, tenant_id, project_id, contract_id, partner_id, settlement_code,
                     settlement_type, approval_status, settlement_status, created_by, updated_by,
                     created_at, updated_at, deleted_flag)
                VALUES (?, ?, ?, ?, 20002, ?, 'FINAL', 'DRAFT', 'DRAFT', ?, ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, SETTLEMENT_ID, TENANT_ID, PROJECT_ID, CONTRACT_ID,
                SETTLEMENT_CODE, USER_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO stl_settlement_item
                    (id, tenant_id, settlement_id, item_name, quantity, unit_price, amount,
                     created_by, updated_by, created_at, updated_at, deleted_flag)
                VALUES (?, ?, ?, '事务回滚明细', 1, 1, 1, ?, ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, ITEM_ID, TENANT_ID, SETTLEMENT_ID, USER_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO sub_measure
                    (id, tenant_id, project_id, contract_id, partner_id, measure_code,
                     approval_status, status, cost_generated_flag, created_at, updated_at,
                     created_by, updated_by, deleted_flag)
                VALUES (?, ?, ?, ?, 20002, 'SM-TXN-ROLLBACK-947205',
                        'APPROVED', 'CONFIRMED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        ?, ?, 0)
                """, MEASURE_ID, TENANT_ID, PROJECT_ID, CONTRACT_ID, USER_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO settlement_sub_measure
                    (id, tenant_id, settlement_id, sub_measure_id, reported_amount_snapshot,
                     approved_amount_snapshot, deduction_amount_snapshot, net_amount_snapshot,
                     created_by, created_at)
                VALUES (?, ?, ?, ?, 1, 1, 0, 1, ?, CURRENT_TIMESTAMP)
                """, MEASURE_LINK_ID, TENANT_ID, SETTLEMENT_ID, MEASURE_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_file
                    (id, tenant_id, business_type, business_id, document_type, file_name,
                     original_name, file_size, content_type, storage_path, bucket_name,
                     virus_scan_status, created_by, updated_by, created_at, updated_at, deleted_flag)
                VALUES (?, ?, 'SETTLEMENT', ?, 'OTHER', 'rollback.pdf', 'rollback.pdf', 1,
                        'application/pdf', 'SETTLEMENT/947201/rollback.pdf', 'test', 'CLEAN',
                        ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, FILE_ID, TENANT_ID, SETTLEMENT_ID, USER_ID, USER_ID);
    }

    @AfterEach
    void tearDown() {
        reset(settlementMapper);
        cleanup();
        TestUserContext.clear();
    }

    @Test
    @DisplayName("主记录删除返回0时回滚附件、关联和唯一键墓碑")
    void deleteZeroRollsBackEveryPriorMutation() {
        doReturn(0).when(settlementMapper).deleteById(SETTLEMENT_ID);

        BusinessException failure = assertThrows(
                BusinessException.class, () -> service.delete(SETTLEMENT_ID));
        assertEquals("STL_SETTLEMENT_CONCURRENT_MODIFICATION", failure.getCode());
        reset(settlementMapper);

        var settlement = jdbcTemplate.queryForMap("""
                SELECT contract_id, settlement_code, deleted_flag
                FROM stl_settlement WHERE id = ?
                """, SETTLEMENT_ID);
        assertEquals(CONTRACT_ID, ((Number) settlement.get("CONTRACT_ID")).longValue());
        assertEquals(SETTLEMENT_CODE, settlement.get("SETTLEMENT_CODE"));
        assertEquals(0, ((Number) settlement.get("DELETED_FLAG")).intValue());
        assertEquals(1, countActive("sys_file", "id", FILE_ID));
        assertEquals(1, countActive("stl_settlement_item", "id", ITEM_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_sub_measure WHERE id = ?",
                Integer.class, MEASURE_LINK_ID));
    }

    private int countActive(String table, String column, long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ? AND deleted_flag = 0",
                Integer.class, id);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_file WHERE id = ?", FILE_ID);
        jdbcTemplate.update("DELETE FROM settlement_sub_measure WHERE id = ?", MEASURE_LINK_ID);
        jdbcTemplate.update("DELETE FROM sub_measure WHERE id = ?", MEASURE_ID);
        jdbcTemplate.update("DELETE FROM stl_settlement_item WHERE id = ?", ITEM_ID);
        jdbcTemplate.update("DELETE FROM stl_settlement WHERE id = ?", SETTLEMENT_ID);
    }
}
