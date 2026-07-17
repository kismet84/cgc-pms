package com.cgcpms.accounting;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 会计凭证核心业务测试。
 * <p>
 * 覆盖 AccountingEntryService 的基本 CRUD 和状态流转：
 * getById、分页查询、过账 (DRAFT→POSTED)、冲销 (POSTED→REVERSED)。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("AccountingEntryService — 凭证 CRUD 与状态流转")
class AccountingEntryServiceTest {

    @Autowired
    private AccountingEntryService entryService;

    @Autowired
    private AccountingEntryMapper entryMapper;

    @Autowired
    private AccountingEntryLineMapper lineMapper;

    private Long draftEntryId;

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        draftEntryId = seedDraftEntry("ACC-TEST-" + System.nanoTime());
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    private Long seedDraftEntry(String entryCode) {
        AccountingEntry entry = new AccountingEntry();
        entry.setTenantId(TestUserContext.TENANT_0);
        entry.setEntryCode(entryCode);
        entry.setEntryDate(LocalDate.of(2026, 1, 15));
        entry.setEntryType("COST");
        entry.setSourceType("CONTRACT");
        entry.setSourceId(1L);
        entry.setEntryStatus("DRAFT");
        entry.setReviewStatus("PENDING");
        entry.setAdjustmentFlag(0);
        entry.setTotalDebit(new BigDecimal("10000.00"));
        entry.setTotalCredit(new BigDecimal("10000.00"));
        entryMapper.insert(entry);
        AccountingEntryLine debit = new AccountingEntryLine();
        debit.setTenantId(TestUserContext.TENANT_0); debit.setEntryId(entry.getId()); debit.setLineNo(1);
        debit.setDirection("DEBIT"); debit.setAccountCode("1001"); debit.setAccountName("测试借方");
        debit.setAmount(new BigDecimal("10000.00")); debit.setSummary("测试借方"); lineMapper.insert(debit);
        AccountingEntryLine credit = new AccountingEntryLine();
        credit.setTenantId(TestUserContext.TENANT_0); credit.setEntryId(entry.getId()); credit.setLineNo(2);
        credit.setDirection("CREDIT"); credit.setAccountCode("2001"); credit.setAccountName("测试贷方");
        credit.setAmount(new BigDecimal("10000.00")); credit.setSummary("测试贷方"); lineMapper.insert(credit);
        return entry.getId();
    }

    private void approveDraft() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 2L, "reviewer", java.util.List.of("FINANCE"));
        entryService.review(draftEntryId, true, "复核通过");
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    // ═══════════════════════════════════════════════════════════════
    // A-1: 按 ID 查询凭证
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("A-1: getById 返回完整凭证信息")
    void testGetById() {
        AccountingEntry entry = entryService.getById(draftEntryId);
        assertNotNull(entry);
        assertTrue(entry.getEntryCode().startsWith("ACC-TEST-"), "entryCode should start with ACC-TEST-");
        assertEquals("COST", entry.getEntryType());
        assertEquals("DRAFT", entry.getEntryStatus());
        assertEquals(0, new BigDecimal("10000.00").compareTo(entry.getTotalDebit()));
    }

    @Test
    @DisplayName("A-1b: getById 对不存在的 ID 抛出 ENTRY_NOT_FOUND")
    void testGetByIdNotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                entryService.getById(99999L));
        assertEquals("ENTRY_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // A-2: 分页查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("A-2: getPage 按租户过滤返回分页结果")
    void testGetPage() {
        IPage<AccountingEntry> page = entryService.getPage(1, 10, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1, "至少应包含一条种子凭证");
        assertFalse(page.getRecords().isEmpty());
    }

    @Test
    @DisplayName("A-2b: getPage 按 entryType 过滤")
    void testGetPageFilterByEntryType() {
        IPage<AccountingEntry> page = entryService.getPage(1, 10, "COST", null, null, null, null);
        assertTrue(page.getRecords().stream()
                .allMatch(e -> "COST".equals(e.getEntryType())));
    }

    // ═══════════════════════════════════════════════════════════════
    // A-3: 过账 — DRAFT → POSTED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("A-3: 复核通过后 post 将草稿凭证状态更新为 POSTED")
    void testPostDraftToPosted() {
        approveDraft();
        entryService.post(draftEntryId);
        AccountingEntry updated = entryService.getById(draftEntryId);
        assertEquals("POSTED", updated.getEntryStatus());
    }

    @Test
    @DisplayName("A-3b: 重复过账抛出 ENTRY_STATUS_INVALID")
    void testPostAlreadyPostedThrows() {
        approveDraft();
        entryService.post(draftEntryId); // 第一次过账

        BusinessException ex = assertThrows(BusinessException.class, () ->
                entryService.post(draftEntryId)); // 重复过账
        assertEquals("ENTRY_STATUS_INVALID", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // A-4: 冲销 — POSTED → REVERSED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("A-4: reverse 将已过账凭证状态更新为 REVERSED")
    void testReversePostedToReversed() {
        approveDraft();
        entryService.post(draftEntryId); // 先过账

        AccountingEntry reversal = entryService.reverse(draftEntryId); // 生成冲销草稿
        TestUserContext.setUser(TestUserContext.TENANT_0, 2L, "reviewer", java.util.List.of("FINANCE"));
        entryService.review(reversal.getId(), true, "冲销复核通过");
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        entryService.post(reversal.getId());
        AccountingEntry updated = entryService.getById(draftEntryId);
        assertEquals("REVERSED", updated.getEntryStatus());
        assertEquals(reversal.getId(), updated.getReversedEntryId());
    }

    @Test
    @DisplayName("A-4b: 冲销草稿状态凭证抛出 ENTRY_STATUS_INVALID")
    void testReverseDraftThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                entryService.reverse(draftEntryId)); // 草稿不可冲销
        assertEquals("ENTRY_STATUS_INVALID", ex.getCode());
    }

    @Test
    @DisplayName("A-5: 未复核凭证禁止过账")
    void testUnreviewedEntryCannotPost() {
        BusinessException ex = assertThrows(BusinessException.class, () -> entryService.post(draftEntryId));
        assertEquals("ENTRY_REVIEW_REQUIRED", ex.getCode());
    }

    @Test
    @DisplayName("A-6: 制单人禁止复核自己的凭证")
    void testMakerCannotReviewOwnEntry() {
        BusinessException ex = assertThrows(BusinessException.class, () -> entryService.review(draftEntryId, true, null));
        assertEquals("ENTRY_REVIEW_SEGREGATION_REQUIRED", ex.getCode());
    }

    @Test
    @DisplayName("A-7: 驳回必须填写原因且可重新提交")
    void testRejectAndResubmit() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 2L, "reviewer", java.util.List.of("FINANCE"));
        BusinessException missing = assertThrows(BusinessException.class, () -> entryService.review(draftEntryId, false, null));
        assertEquals("ENTRY_REJECT_COMMENT_REQUIRED", missing.getCode());
        entryService.review(draftEntryId, false, "科目不正确");
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        entryService.submitReview(draftEntryId);
        assertEquals("PENDING", entryService.getById(draftEntryId).getReviewStatus());
    }
}
