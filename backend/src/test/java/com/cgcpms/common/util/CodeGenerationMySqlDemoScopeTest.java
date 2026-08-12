package com.cgcpms.common.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.document.mapper.DocumentTemplateMapper;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CGCPMS_M81_MYSQL_CODE_GENERATION_DEMO_SCOPE", matches = "true")
class CodeGenerationMySqlDemoScopeTest {

    private static final long TENANT = 0L;
    private static final long OTHER_TENANT = 810081L;
    private static final String PREFIX = "M81-RV-";
    private static final long FIRST_ID = 810081001L;

    @Autowired private BidCostMapper bidCostMapper;
    @Autowired private ProjectBudgetMapper projectBudgetMapper;
    @Autowired private DocumentTemplateMapper documentTemplateMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private ContractRevenueMapper revenueMapper;
    @Autowired private CtContractChangeMapper contractChangeMapper;
    @Autowired private ExpenseApplicationMapper expenseApplicationMapper;
    @Autowired private MdPartnerMapper partnerMapper;
    @Autowired private PayApplicationMapper payApplicationMapper;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private MatPurchaseOrderMapper purchaseOrderMapper;
    @Autowired private MatPurchaseRequestMapper purchaseRequestMapper;
    @Autowired private MatReceiptMapper receiptMapper;
    @Autowired private MatRequisitionMapper requisitionMapper;
    @Autowired private StlSettlementMapper settlementMapper;
    @Autowired private SubMeasureMapper subMeasureMapper;
    @Autowired private SubTaskMapper subTaskMapper;
    @Autowired private VarOrderMapper varOrderMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;
    @Autowired private SqlSession sqlSession;

    private long projectId;
    private long contractId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @BeforeEach
    void requireDedicatedLocalDemoDatabase() {
        String url = environment.getRequiredProperty("spring.datasource.url");
        assertTrue(url.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/cgc_pms_demo_v2(?:[?].*)?$"),
                "M81 demo-scope test requires loopback cgc_pms_demo_v2");
        assertTrue(Files.isRegularFile(Path.of("..", ".codex-autopilot", "ALLOW_TEST_DATA_RESET")),
                "M81 demo-scope test requires reset marker");
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
    @Transactional
    void allDeletedCodeMappersKeepTenantDateDeletedAndLexicographicScopes() {
        List<MapperCase> cases = mapperCases();

        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        String previousDay = LocalDate.now().minusDays(1).format(DateTimeUtils.DATE_COMPACT) + "-";
        for (MapperCase mapperCase : cases) {
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM " + mapperCase.table() + " WHERE tenant_id=0 ORDER BY id LIMIT 2",
                    Long.class);
            if (ids.size() < 2 && mapperCase.table().equals("contract_revenue")) {
                insert(FIRST_ID, mapperCase.prefix() + "998", 0);
                ids = jdbc.queryForList(
                        "SELECT id FROM contract_revenue WHERE tenant_id=0 ORDER BY id LIMIT 2", Long.class);
            }
            if (ids.size() == 1) {
                insertSecondMapperFixture(mapperCase, ids.getFirst());
                ids = jdbc.queryForList(
                        "SELECT id FROM " + mapperCase.table() + " WHERE tenant_id=0 ORDER BY id LIMIT 2",
                        Long.class);
            }
            assertEquals(2, ids.size(), mapperCase.table() + " requires two controlled rows");
            String currentPrefix = mapperCase.prefix() + today;
            String previousPrefix = mapperCase.prefix() + previousDay;
            jdbc.update("UPDATE " + mapperCase.table() + " SET " + mapperCase.codeColumn()
                    + "=?, deleted_flag=1 WHERE id=?", previousPrefix + "999", ids.get(0));
            assertNull(mapperCase.source().selectLastCodeByPrefix(currentPrefix, TENANT));

            jdbc.update("UPDATE " + mapperCase.table() + " SET " + mapperCase.codeColumn()
                    + "=?, deleted_flag=1 WHERE id=?", currentPrefix + "1000", ids.get(0));
            jdbc.update("UPDATE " + mapperCase.table() + " SET " + mapperCase.codeColumn()
                    + "=?, deleted_flag=0 WHERE id=?", currentPrefix + "998", ids.get(1));
            sqlSession.clearCache();

            assertEquals(currentPrefix + "1000",
                    mapperCase.source().selectLastCodeByPrefix(currentPrefix, TENANT));
            assertNull(mapperCase.source().selectLastCodeByPrefix(currentPrefix, OTHER_TENANT));
        }
    }

    private void insertSecondMapperFixture(MapperCase mapperCase, long sourceId) {
        long id = IdWorker.getId();
        switch (mapperCase.table()) {
            case "ct_contract_change" -> jdbc.update("""
                    INSERT INTO ct_contract_change(
                        id,tenant_id,project_id,contract_id,change_code,change_name,change_type)
                    SELECT ?,tenant_id,project_id,contract_id,?,change_name,change_type
                    FROM ct_contract_change WHERE id=?
                    """, id, mapperCase.prefix() + "FIXTURE", sourceId);
            case "expense_application" -> jdbc.update("""
                    INSERT INTO expense_application(
                        id,tenant_id,project_id,contract_id,cost_subject_id,budget_line_id,payee_partner_id,
                        expense_code,expense_category,expense_date,amount,description)
                    SELECT ?,tenant_id,project_id,contract_id,cost_subject_id,budget_line_id,payee_partner_id,
                           ?,expense_category,expense_date,amount,description
                    FROM expense_application WHERE id=?
                    """, id, mapperCase.prefix() + "FIXTURE", sourceId);
            default -> throw new AssertionError(mapperCase.table() + " requires two dedicated demo rows");
        }
    }

    private List<MapperCase> mapperCases() {
        return List.of(
                new MapperCase("bid_cost", "bid_code", "M81MAP-BID-", bidCostMapper),
                new MapperCase("project_budget", "budget_code", "M81MAP-BUD-", projectBudgetMapper),
                new MapperCase("biz_document_template", "template_code", "M81MAP-TPL-", documentTemplateMapper),
                new MapperCase("ct_contract", "contract_code", "M81MAP-CT-", contractMapper),
                new MapperCase("pay_record", "record_code", "M81MAP-PMT-", payRecordMapper),
                new MapperCase("contract_revenue", "revenue_code", "M81MAP-RV-", revenueMapper),
                new MapperCase("ct_contract_change", "change_code", "M85MAP-CC-", contractChangeMapper),
                new MapperCase("expense_application", "expense_code", "M85MAP-EXP-", expenseApplicationMapper),
                new MapperCase("md_partner", "partner_code", "M85MAP-PTN-", partnerMapper),
                new MapperCase("pay_application", "apply_code", "M85MAP-PAY-", payApplicationMapper),
                new MapperCase("pm_project", "project_code", "M85MAP-XM-", projectMapper),
                new MapperCase("mat_purchase_order", "order_code", "M85MAP-PO-", purchaseOrderMapper),
                new MapperCase("mat_purchase_request", "request_code", "M85MAP-PR-", purchaseRequestMapper),
                new MapperCase("mat_receipt", "receipt_code", "M85MAP-MR-", receiptMapper),
                new MapperCase("mat_requisition", "requisition_code", "M85MAP-REQ-", requisitionMapper),
                new MapperCase("stl_settlement", "settlement_code", "M85MAP-STL-", settlementMapper),
                new MapperCase("sub_measure", "measure_code", "M85MAP-SM-", subMeasureMapper),
                new MapperCase("sub_task", "task_code", "M85MAP-SUB-", subTaskMapper),
                new MapperCase("var_order", "var_code", "M85MAP-VO-", varOrderMapper));
    }

    private void insert(long id, String code, int deletedFlag) {
        jdbc.update("""
                INSERT INTO contract_revenue(
                    id,tenant_id,project_id,contract_id,revenue_code,revenue_date,
                    approval_status,deleted_flag,created_at,updated_at)
                VALUES(?,?,?,?,?,CURRENT_DATE,'DRAFT',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, TENANT, projectId, contractId, code, deletedFlag);
    }

    private record MapperCase(String table, String codeColumn, String prefix, DeletedCodeSource source) {
    }
}
