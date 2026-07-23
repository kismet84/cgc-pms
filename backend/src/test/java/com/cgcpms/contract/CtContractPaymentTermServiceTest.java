package com.cgcpms.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.contract.service.CtContractPaymentTermService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CtContractPaymentTermService 单元测试。
 * <p>
 * 覆盖: 按合同查询、创建、批量保存（全量替换）、更新、删除等核心操作。
 * 使用 H2 内存数据库，以 contract 30001 (tenant_id=0, project_id=10001) 作为关联合同。
 * update/delete/batchSave 需要 DRAFT 状态的合同，会在 @BeforeEach 中创建。
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@Transactional
@DisplayName("CtContractPaymentTermService — 合同付款条款 CRUD 测试")
class CtContractPaymentTermServiceTest {

    /** Approved, performing contract from V90 seed — requiresParentContract only */
    private static final long CONTRACT_ID = 30001L;
    private static final long TENANT_0 = 0L;
    private static final long USER_ADMIN = 1L;

    /** DRAFT contract created in @BeforeEach for update/delete/batchSave operations */
    private Long draftContractId;

    @Autowired
    private CtContractPaymentTermService termService;

    @Autowired
    private CtContractPaymentTermMapper termMapper;

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupContext() {
        setAdmin(TENANT_0, USER_ADMIN);
        draftContractId = seedDraftContract();
    }

    @AfterEach
    void clearContext() {
        // 物理删除本测试创建的 payment term 记录，避免后续测试受影响
        jdbcTemplate.update("DELETE FROM ct_contract_payment_term WHERE contract_id = ?", draftContractId);
        UserContext.clear();
    }

    /** Create a DRAFT contract for testing write operations that require draft status. */
    private Long seedDraftContract() {
        CtContract c = new CtContract();
        c.setProjectId(10001L);
        c.setContractCode("CT-TEST-DRAFT-TERM");
        c.setContractName("DRAFT合同-付款条款测试");
        c.setContractType("SUB");
        c.setPartyAId(20001L);
        c.setPartyBId(20002L);
        c.setContractAmount(new BigDecimal("1000000.00"));
        c.setCurrentAmount(new BigDecimal("1000000.00"));
        c.setPaidAmount(BigDecimal.ZERO);
        c.setTaxRate(new BigDecimal("13.00"));
        c.setContractStatus(ContractStatusConstants.STATUS_DRAFT);
        c.setApprovalStatus(ContractStatusConstants.APPROVAL_DRAFT);
        c.setTenantId(TENANT_0);
        c.setCostGeneratedFlag(0);
        contractMapper.insert(c);
        return c.getId();
    }

    // ═══════════════════════════════════════════════════════════════
    // 按合同查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("按合同查询 — 返回属于该合同的付款条款列表")
    void testGetByContractId() {
        CtContractPaymentTerm term = buildDraftTerm("查询测试条款");
        termService.create(term);

        List<CtContractPaymentTerm> terms = termService.getByContractId(draftContractId);
        assertNotNull(terms);
        assertTrue(terms.size() >= 1, "应至少返回一条付款条款");
        for (CtContractPaymentTerm t : terms) {
            assertEquals(draftContractId, t.getContractId());
        }
    }

    @Test
    @DisplayName("按合同查询 — 合同不存在时抛 CONTRACT_NOT_FOUND")
    void testGetByContractIdNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.getByContractId(-999L));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("按合同查询 — 租户隔离: 跨租户查询抛 CONTRACT_NOT_FOUND")
    void testGetByContractIdTenantIsolation() {
        CtContractPaymentTerm term = buildDraftTerm("租户隔离条款");
        termService.create(term);

        setAdmin(999L, USER_ADMIN);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.getByContractId(draftContractId));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("按合同查询 — 无项目权限时拒绝访问")
    void testGetByContractIdProjectAccessDenied() {
        jdbcTemplate.update("UPDATE pm_project SET created_by = ?, project_manager_id = NULL WHERE id = ?",
                2L, 10001L);

        UserContext.set(Jwts.claims()
                .add("userId", 3L)
                .add("username", "no-project-access")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of())
                .build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.getByContractId(draftContractId));
        assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
    }

    @Test
    @DisplayName("按合同查询 — 按 sortOrder 升序排列")
    void testGetByContractIdOrderBySortOrder() {
        CtContractPaymentTerm t1 = buildDraftTerm("排序条款1");
        t1.setSortOrder(3);
        termService.create(t1);

        CtContractPaymentTerm t2 = buildDraftTerm("排序条款2");
        t2.setSortOrder(1);
        termService.create(t2);

        CtContractPaymentTerm t3 = buildDraftTerm("排序条款3");
        t3.setSortOrder(2);
        termService.create(t3);

        List<CtContractPaymentTerm> terms = termService.getByContractId(draftContractId);
        List<CtContractPaymentTerm> ourTerms = terms.stream()
                .filter(t -> t.getTermName() != null
                        && (t.getTermName().equals("排序条款1")
                        || t.getTermName().equals("排序条款2")
                        || t.getTermName().equals("排序条款3")))
                .toList();
        if (ourTerms.size() == 3) {
            assertEquals("排序条款2", ourTerms.get(0).getTermName(), "sortOrder=1 应排第一");
            assertEquals("排序条款3", ourTerms.get(1).getTermName(), "sortOrder=2 应排第二");
            assertEquals("排序条款1", ourTerms.get(2).getTermName(), "sortOrder=3 应排第三");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 创建
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("创建 — 成功后返回 ID 并持久化")
    void testCreateSuccess() {
        CtContractPaymentTerm term = buildDraftTerm("创建测试条款");
        Long id = termService.create(term);

        assertNotNull(id, "创建应返回 ID");
        CtContractPaymentTerm saved = termMapper.selectById(id);
        assertNotNull(saved);
        assertEquals("创建测试条款", saved.getTermName());
        assertEquals(draftContractId, saved.getContractId());
        assertEquals(TENANT_0, saved.getTenantId());
    }

    @Test
    @DisplayName("创建 — 合同不存在时抛 CONTRACT_NOT_FOUND")
    void testCreateContractNotFound() {
        CtContractPaymentTerm term = buildDraftTerm("无合同条款");
        term.setContractId(-999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.create(term));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("创建 — 非草稿合同拒绝新增")
    void testCreateRejectedForNonDraftContract() {
        CtContractPaymentTerm term = buildDraftTerm("非草稿合同条款");
        term.setContractId(CONTRACT_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.create(term));
        assertEquals("CONTRACT_NOT_EDITABLE", ex.getCode());
    }

    @Test
    @DisplayName("创建 — 所有字段正确持久化")
    void testCreateAllFieldsPersisted() {
        CtContractPaymentTerm term = new CtContractPaymentTerm();
        term.setContractId(draftContractId);
        term.setTermName("全字段条款");
        term.setPaymentRatio(new BigDecimal("30.00"));
        term.setPaymentAmount(new BigDecimal("150000.00"));
        term.setPaymentCondition("主体结构封顶后支付");
        term.setPlannedDate(LocalDate.of(2026, 6, 30));
        term.setActualDate(LocalDate.of(2026, 7, 5));
        term.setTermStatus("PENDING");
        term.setSortOrder(2);

        Long id = termService.create(term);
        CtContractPaymentTerm saved = termMapper.selectById(id);

        assertNotNull(saved);
        assertEquals(0, new BigDecimal("30.00").compareTo(saved.getPaymentRatio()));
        assertEquals(0, new BigDecimal("150000.00").compareTo(saved.getPaymentAmount()));
        assertEquals("主体结构封顶后支付", saved.getPaymentCondition());
        assertEquals(LocalDate.of(2026, 6, 30), saved.getPlannedDate());
        assertEquals(LocalDate.of(2026, 7, 5), saved.getActualDate());
        assertEquals("PENDING", saved.getTermStatus());
        assertEquals(2, saved.getSortOrder());
    }

    // ═══════════════════════════════════════════════════════════════
    // 批量保存（全量替换）—— 需要 DRAFT 合同
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("批量保存 — 清空旧数据后插入新数据（全量替换）")
    void testBatchSaveReplaceAll() {
        CtContractPaymentTerm oldTerm = buildDraftTerm("旧条款");
        termService.create(oldTerm);

        CtContractPaymentTerm newTerm1 = buildDraftTerm("新条款1");
        newTerm1.setSortOrder(1);
        CtContractPaymentTerm newTerm2 = buildDraftTerm("新条款2");
        newTerm2.setSortOrder(2);
        CtContractPaymentTerm newTerm3 = buildDraftTerm("新条款3");
        newTerm3.setSortOrder(3);
        List<CtContractPaymentTerm> newTerms = List.of(newTerm1, newTerm2, newTerm3);

        termService.batchSave(draftContractId, newTerms);

        List<CtContractPaymentTerm> after = termMapper.selectList(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getContractId, draftContractId));
        List<CtContractPaymentTerm> ourNewTerms = after.stream()
                .filter(t -> t.getTermName() != null && t.getTermName().startsWith("新条款"))
                .toList();
        assertEquals(3, ourNewTerms.size(), "批量保存后应有3条新条款");
    }

    @Test
    @DisplayName("批量保存 — 传入空列表时清空所有付款条款")
    void testBatchSaveEmptyListClearsAll() {
        CtContractPaymentTerm term = buildDraftTerm("待清空条款");
        termService.create(term);

        termService.batchSave(draftContractId, List.of());

        CtContractPaymentTerm deleted = termMapper.selectOne(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getTermName, "待清空条款"));
        assertNull(deleted, "空列表批量保存后，所有条款应被删除");
    }

    @Test
    @DisplayName("批量保存 — 传入 null 时清空所有条款而不报错")
    void testBatchSaveNullClearsAll() {
        CtContractPaymentTerm term = buildDraftTerm("null测试条款");
        termService.create(term);

        termService.batchSave(draftContractId, null);

        CtContractPaymentTerm deleted = termMapper.selectOne(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getTermName, "null测试条款"));
        assertNull(deleted, "null 批量保存后，所有条款应被删除");
    }

    @Test
    @DisplayName("批量保存 — 合同不存在时抛 CONTRACT_NOT_FOUND")
    void testBatchSaveContractNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.batchSave(-999L, List.of(buildDraftTerm("无合同条款"))));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 更新 — 需要 DRAFT 合同
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("更新 — 成功后字段正确更新")
    void testUpdateSuccess() {
        CtContractPaymentTerm term = buildDraftTerm("更新前条款");
        Long id = termService.create(term);

        CtContractPaymentTerm toUpdate = new CtContractPaymentTerm();
        toUpdate.setId(id);
        toUpdate.setContractId(draftContractId);
        toUpdate.setTermName("更新后条款");
        toUpdate.setPaymentRatio(new BigDecimal("50.00"));
        toUpdate.setPaymentAmount(new BigDecimal("250000.00"));
        toUpdate.setPaymentCondition("主体结构验收合格后支付");

        termService.update(toUpdate);

        CtContractPaymentTerm updated = termMapper.selectById(id);
        assertEquals("更新后条款", updated.getTermName());
        assertEquals(0, new BigDecimal("50.00").compareTo(updated.getPaymentRatio()));
        assertEquals(0, new BigDecimal("250000.00").compareTo(updated.getPaymentAmount()));
        assertEquals("主体结构验收合格后支付", updated.getPaymentCondition());
    }

    @Test
    @DisplayName("更新 — 条款不存在时抛 PAYMENT_TERM_NOT_FOUND")
    void testUpdateTermNotFound() {
        CtContractPaymentTerm toUpdate = new CtContractPaymentTerm();
        toUpdate.setId(-999L);
        toUpdate.setContractId(draftContractId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.update(toUpdate));
        assertEquals("PAYMENT_TERM_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("更新 — contractId 不存在的合同抛 CONTRACT_NOT_FOUND")
    void testUpdateContractMismatch() {
        CtContractPaymentTerm term = buildDraftTerm("contractId不匹配条款");
        Long id = termService.create(term);

        CtContractPaymentTerm toUpdate = new CtContractPaymentTerm();
        toUpdate.setId(id);
        toUpdate.setContractId(-999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.update(toUpdate));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 删除 — 需要 DRAFT 合同
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("删除 — 成功后软删除记录")
    void testDeleteSuccess() {
        CtContractPaymentTerm term = buildDraftTerm("待删除条款");
        Long id = termService.create(term);

        termService.delete(draftContractId, id);

        CtContractPaymentTerm deleted = termMapper.selectById(id);
        assertNull(deleted, "软删除后应查不到记录");
    }

    @Test
    @DisplayName("删除 — 条款不存在时抛 PAYMENT_TERM_NOT_FOUND")
    void testDeleteTermNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.delete(draftContractId, -999L));
        assertEquals("PAYMENT_TERM_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("删除 — contractId 不存在的合同抛 CONTRACT_NOT_FOUND")
    void testDeleteContractMismatch() {
        CtContractPaymentTerm term = buildDraftTerm("删除contractId不匹配");
        Long id = termService.create(term);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> termService.delete(-999L, id));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    private CtContractPaymentTerm buildDraftTerm(String termName) {
        CtContractPaymentTerm term = new CtContractPaymentTerm();
        term.setContractId(draftContractId);
        term.setTermName(termName);
        term.setPaymentRatio(new BigDecimal("30.00"));
        term.setPaymentAmount(new BigDecimal("150000.00"));
        term.setPaymentCondition("主体结构封顶后支付");
        term.setPlannedDate(LocalDate.of(2026, 6, 30));
        term.setTermStatus("PENDING");
        term.setSortOrder(1);
        return term;
    }

    /** Inline UserContext setup (avoids NoClassDefFoundError on TestUserContext in full suite). */
    private static void setAdmin(long tenantId, long userId) {
        UserContext.set(Jwts.claims()
                .add("userId", userId)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }
}
