package com.cgcpms.accounting;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.accounting.service.AccountingCostCarryoverService;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@DisplayName("AccountingCostCarryoverService — 项目成本正式结转")
class AccountingCostCarryoverServiceTest {

    private static final long TENANT = 0L;
    private static final long USER = 30597001L;

    @Autowired AccountingCostCarryoverService service;
    @Autowired AccountingEntryService entryService;
    @Autowired AccountingEntryMapper entryMapper;
    @Autowired AccountingEntryLineMapper lineMapper;
    @Autowired JdbcTemplate jdbc;

    private Long projectId;
    private Long contractId;
    private Long financeRoleId;
    private LocalDate carryoverDate;

    @BeforeEach
    void setup() {
        TestUserContext.setAdmin(TENANT, USER);
        jdbc.update("""
                INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                VALUES(?,0,'accounting-carryover-test','test-only','成本结转测试','ENABLE',0,0)
                """, USER);
        financeRoleId = jdbc.queryForObject("""
                SELECT id FROM sys_role
                WHERE tenant_id=0 AND role_code='COMPANY_FINANCE' AND status='ENABLE' AND deleted_flag=0
                """, Long.class);
        jdbc.update("""
                INSERT INTO sys_user_role(id,tenant_id,user_id,role_id)
                SELECT ?,0,?,? WHERE NOT EXISTS (
                  SELECT 1 FROM sys_user_role WHERE tenant_id=0 AND user_id=? AND role_id=?)
                """, 30597002L, USER, financeRoleId, USER, financeRoleId);

        List<Long[]> contract = jdbc.query("""
                SELECT c.project_id,c.id FROM ct_contract c
                JOIN pm_project p ON p.tenant_id=c.tenant_id AND p.id=c.project_id
                WHERE c.tenant_id=0 AND c.deleted_flag=0 AND p.deleted_flag=0
                ORDER BY c.id LIMIT 1
                """, (rs, rowNum) -> new Long[]{rs.getLong(1), rs.getLong(2)});
        projectId = contract.getFirst()[0];
        contractId = contract.getFirst()[1];
        jdbc.update("UPDATE pm_project SET owner_contract_id=?,status='INITIATED' WHERE tenant_id=0 AND id=?",
                contractId, projectId);
        jdbc.update("""
                UPDATE ct_contract SET contract_type='MAIN',approval_status='APPROVED',contract_status='PERFORMING'
                WHERE tenant_id=0 AND id=?
                """, contractId);

        carryoverDate = LocalDate.of(2026, 8, 15);
        jdbc.update("""
                INSERT INTO finance_period
                  (id,tenant_id,period_code,fiscal_year,fiscal_month,start_date,end_date,status,issue_count,version,created_by)
                VALUES(30597003,0,'2026-08',2026,8,'2026-08-01','2026-08-31','OPEN',0,0,?)
                """, USER);
        seedPostedFulfillmentCost(new BigDecimal("125.40"));
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    @Test
    @DisplayName("按固定1451到6401映射生成平衡凭证并按余额快照幂等")
    void createsBalancedIdempotentCarryover() {
        AccountingEntry first = service.create(projectId, contractId, carryoverDate);
        AccountingEntry second = service.create(projectId, contractId, carryoverDate);

        assertNotNull(first.getId());
        assertEquals(first.getId(), second.getId());
        assertEquals(0, new BigDecimal("125.40").compareTo(first.getTotalDebit()));
        assertEquals(List.of("6401.01", "1451.01"), jdbc.queryForList("""
                SELECT account_code FROM accounting_entry_line
                WHERE tenant_id=0 AND entry_id=? ORDER BY line_no
                """, String.class, first.getId()));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM accounting_entry_line
                WHERE tenant_id=0 AND entry_id=? AND accounting_subject_id IS NOT NULL
                """, Integer.class, first.getId()));
    }

    @Test
    @DisplayName("非公司财务和关闭项目均失败关闭且不生成结转批次")
    void rejectsUnauthorizedAndClosedProject() {
        jdbc.update("DELETE FROM sys_user_role WHERE tenant_id=0 AND user_id=? AND role_id=?", USER, financeRoleId);
        BusinessException unauthorized = assertThrows(BusinessException.class,
                () -> service.create(projectId, contractId, carryoverDate));
        assertEquals("ACCOUNTING_COMPANY_FINANCE_REQUIRED", unauthorized.getCode());

        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES(?,0,?,?)",
                30597002L, USER, financeRoleId);
        jdbc.update("UPDATE pm_project SET status='CLOSED' WHERE tenant_id=0 AND id=?", projectId);
        BusinessException closed = assertThrows(BusinessException.class,
                () -> service.create(projectId, contractId, carryoverDate));
        assertEquals("ACCOUNTING_CARRYOVER_CONTRACT_INVALID", closed.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_cost_carryover WHERE tenant_id=0",
                Integer.class));
    }

    @Test
    @DisplayName("过账时重验余额快照并拒绝重复结转旧草稿")
    void rejectsStaleDraftAndPostsOnlyLatestBalance() {
        AccountingEntry stale = service.create(projectId, contractId, carryoverDate);
        seedPostedFulfillmentCost(new BigDecimal("20.00"));
        AccountingEntry latest = service.create(projectId, contractId, carryoverDate);
        approve(stale.getId());
        approve(latest.getId());

        BusinessException drift = assertThrows(BusinessException.class, () -> entryService.post(stale.getId()));
        assertEquals("ACCOUNTING_COST_CARRYOVER_SNAPSHOT_DRIFT", drift.getCode());
        assertEquals("DRAFT", entryMapper.selectById(stale.getId()).getEntryStatus());

        entryService.post(latest.getId());
        assertEquals("POSTED", entryMapper.selectById(latest.getId()).getEntryStatus());
        assertEquals(0, new BigDecimal("145.40").compareTo(entryMapper.selectById(latest.getId()).getTotalDebit()));
        BusinessException alreadyCovered = assertThrows(BusinessException.class,
                () -> entryService.post(stale.getId()));
        assertEquals("ACCOUNTING_COST_CARRYOVER_SNAPSHOT_DRIFT", alreadyCovered.getCode());
    }

    @Test
    @DisplayName("源成本冲销后旧结转草稿不得过账")
    void rejectsDraftAfterSourceReversal() {
        AccountingEntry draft = service.create(projectId, contractId, carryoverDate);
        Long sourceEntryId = jdbc.queryForObject("""
                SELECT id FROM accounting_entry
                WHERE tenant_id=0 AND project_id=? AND contract_id=? AND source_type='TEST_CARRYOVER_SOURCE'
                ORDER BY id LIMIT 1
                """, Long.class, projectId, contractId);
        AccountingEntry reversal = entryService.createReversal(sourceEntryId, "冲销结转来源");
        approve(reversal.getId());
        entryService.post(reversal.getId());
        approve(draft.getId());

        BusinessException drift = assertThrows(BusinessException.class, () -> entryService.post(draft.getId()));
        assertEquals("ACCOUNTING_COST_CARRYOVER_SNAPSHOT_DRIFT", drift.getCode());
        assertEquals("DRAFT", entryMapper.selectById(draft.getId()).getEntryStatus());
    }

    @Test
    @DisplayName("已结转的源成本必须先冲销结转凭证才能冲销")
    void blocksSourceReversalAfterCarryoverPosted() {
        AccountingEntry carryover = service.create(projectId, contractId, carryoverDate);
        approve(carryover.getId());
        entryService.post(carryover.getId());
        Long sourceEntryId = jdbc.queryForObject("""
                SELECT id FROM accounting_entry
                WHERE tenant_id=0 AND project_id=? AND contract_id=? AND source_type='TEST_CARRYOVER_SOURCE'
                ORDER BY id LIMIT 1
                """, Long.class, projectId, contractId);
        AccountingEntry reversal = entryService.createReversal(sourceEntryId, "冲销已结转来源");
        approve(reversal.getId());

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> entryService.post(reversal.getId()));
        assertEquals("ACCOUNTING_COST_CARRYOVER_REVERSE_BLOCKED", blocked.getCode());
        assertEquals("DRAFT", entryMapper.selectById(reversal.getId()).getEntryStatus());
        assertEquals("POSTED", entryMapper.selectById(sourceEntryId).getEntryStatus());
    }

    private void seedPostedFulfillmentCost(BigDecimal amount) {
        Long fulfillmentId = jdbc.queryForObject("""
                SELECT id FROM cost_subject
                WHERE tenant_id=0 AND subject_code='1451.01' AND ledger_flag=1 AND deleted_flag=0
                """, Long.class);
        Long bankId = jdbc.queryForObject("""
                SELECT id FROM cost_subject
                WHERE tenant_id=0 AND subject_code='1002.02' AND ledger_flag=1 AND deleted_flag=0
                """, Long.class);
        AccountingEntry entry = new AccountingEntry();
        entry.setTenantId(TENANT);
        entry.setEntryCode("CARRYOVER-SOURCE-" + System.nanoTime());
        entry.setEntryDate(carryoverDate);
        entry.setEntryType("COST_CONFIRMATION");
        entry.setSourceType("TEST_CARRYOVER_SOURCE");
        entry.setSourceId(System.nanoTime());
        entry.setProjectId(projectId);
        entry.setContractId(contractId);
        entry.setEntryStatus("POSTED");
        entry.setReviewStatus("APPROVED");
        entry.setAdjustmentFlag(0);
        entry.setTotalDebit(amount);
        entry.setTotalCredit(amount);
        entry.setPostedAt(LocalDateTime.now());
        entry.setVersion(0);
        entryMapper.insert(entry);
        lineMapper.insert(line(entry.getId(), 1, "DEBIT", fulfillmentId, "1451.01", amount));
        lineMapper.insert(line(entry.getId(), 2, "CREDIT", bankId, "1002.02", amount));
    }

    private void approve(Long entryId) {
        jdbc.update("UPDATE accounting_entry SET review_status='APPROVED' WHERE tenant_id=0 AND id=?", entryId);
    }

    private static AccountingEntryLine line(Long entryId, int lineNo, String direction, Long subjectId,
                                            String code, BigDecimal amount) {
        AccountingEntryLine line = new AccountingEntryLine();
        line.setTenantId(TENANT);
        line.setEntryId(entryId);
        line.setLineNo(lineNo);
        line.setDirection(direction);
        line.setAccountingSubjectId(subjectId);
        line.setAccountCode(code);
        line.setAccountName(code);
        line.setAmount(amount);
        line.setSummary("成本结转测试");
        return line;
    }
}
