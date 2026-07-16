package com.cgcpms.file;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class BusinessObjectAuthorizerTest {

    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock CtContractMapper contractMapper;
    @Mock PayInvoiceMapper invoiceMapper;
    @Mock MatReceiptMapper receiptMapper;
    @Mock PayApplicationMapper paymentMapper;
    @Mock PayRecordMapper payRecordMapper;
    @Mock SubMeasureMapper subcontractMapper;
    @Mock StlSettlementMapper settlementMapper;
    @Mock VarOrderMapper variationMapper;
    @Mock CostTargetMapper bidCostMapper;
    @Mock MdPartnerMapper partnerMapper;
    @Mock MdMaterialMapper materialMapper;
    @Mock CashJournalEntryMapper cashJournalEntryMapper;
    @Mock SiteDailyLogMapper siteDailyLogMapper;
    @Mock ExpenseApplicationMapper expenseApplicationMapper;
    @Mock JdbcTemplate jdbcTemplate;

    private BusinessObjectAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setAuthentication("ROLE_ADMIN");
        authorizer = new BusinessObjectAuthorizer(projectAccessChecker, contractMapper, invoiceMapper,
                receiptMapper, paymentMapper, payRecordMapper, subcontractMapper, settlementMapper,
                variationMapper, bidCostMapper, partnerMapper, materialMapper, cashJournalEntryMapper,
                siteDailyLogMapper, expenseApplicationMapper, jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void contractFileAccessChecksRealProject() {
        CtContract contract = new CtContract();
        contract.setTenantId(TestUserContext.TENANT_0);
        contract.setProjectId(10001L);
        when(contractMapper.selectById(30001L)).thenReturn(contract);

        authorizer.checkReadAccess("CONTRACT", 30001L);

        verify(projectAccessChecker).checkAccess(10001L, "读取合同文件");
    }

    @Test
    void paymentFileAccessChecksRealProject() {
        PayApplication payment = new PayApplication();
        payment.setTenantId(TestUserContext.TENANT_0);
        payment.setProjectId(10002L);
        when(paymentMapper.selectById(40001L)).thenReturn(payment);

        authorizer.checkUploadAccess("PAYMENT", 40001L);

        verify(projectAccessChecker).checkAccess(10002L, "写入付款申请文件");
    }

    @Test
    void invoiceFileAccessChecksProjectFromPayRecord() {
        PayInvoice invoice = new PayInvoice();
        invoice.setTenantId(TestUserContext.TENANT_0);
        invoice.setPayRecordId(50001L);
        PayRecord record = new PayRecord();
        record.setTenantId(TestUserContext.TENANT_0);
        record.setProjectId(10003L);
        when(invoiceMapper.selectById(51001L)).thenReturn(invoice);
        when(payRecordMapper.selectById(50001L)).thenReturn(record);

        authorizer.checkReadAccess("INVOICE", 51001L);

        verify(projectAccessChecker).checkAccess(10003L, "读取发票文件");
    }

    @Test
    void invoiceFileAccessChecksProjectFromPayApplicationWhenRecordHasNoProject() {
        PayInvoice invoice = new PayInvoice();
        invoice.setTenantId(TestUserContext.TENANT_0);
        invoice.setPayRecordId(50002L);
        PayRecord record = new PayRecord();
        record.setTenantId(TestUserContext.TENANT_0);
        record.setPayApplicationId(40002L);
        PayApplication payment = new PayApplication();
        payment.setTenantId(TestUserContext.TENANT_0);
        payment.setProjectId(10004L);
        when(invoiceMapper.selectById(51002L)).thenReturn(invoice);
        when(payRecordMapper.selectById(50002L)).thenReturn(record);
        when(paymentMapper.selectById(40002L)).thenReturn(payment);

        authorizer.checkReadAccess("INVOICE", 51002L);

        verify(projectAccessChecker).checkAccess(10004L, "读取发票文件");
    }

    @Test
    void variationFileAccessChecksRealProject() {
        VarOrder variation = new VarOrder();
        variation.setTenantId(TestUserContext.TENANT_0);
        variation.setProjectId(10005L);
        when(variationMapper.selectById(60001L)).thenReturn(variation);

        authorizer.checkReadAccess("VARIATION", 60001L);

        verify(projectAccessChecker).checkAccess(10005L, "读取变更单文件");
    }

    @Test
    void settlementFileAccessChecksRealProject() {
        StlSettlement settlement = new StlSettlement();
        settlement.setTenantId(TestUserContext.TENANT_0);
        settlement.setProjectId(10006L);
        when(settlementMapper.selectById(70001L)).thenReturn(settlement);

        authorizer.checkUploadAccess("SETTLEMENT", 70001L);

        verify(projectAccessChecker).checkAccess(10006L, "写入结算单文件");
    }

    @Test
    void contractFileAccessRejectsCrossTenantObjectBeforeProjectCheck() {
        CtContract contract = new CtContract();
        contract.setTenantId(9999L);
        contract.setProjectId(10007L);
        when(contractMapper.selectById(30007L)).thenReturn(contract);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authorizer.checkReadAccess("CONTRACT", 30007L));

        assertEquals("FILE_ACCESS_DENIED", ex.getCode());
        verify(projectAccessChecker, never()).checkAccess(anyLong(), anyString());
    }

    @Test
    void cashJournalDraftAllowsWriteAndChecksProjectWhenPresent() {
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TestUserContext.TENANT_0);
        entry.setProjectId(10008L);
        entry.setStatus(CashbookConstants.Status.DRAFT);
        when(cashJournalEntryMapper.selectByIdForUpdate(80001L, TestUserContext.TENANT_0)).thenReturn(entry);

        authorizer.checkUploadAccess("CASH_JOURNAL", 80001L);

        verify(projectAccessChecker).checkAccess(10008L, "写入资金流水文件");
    }

    @Test
    void archivedCashJournalRejectsAttachmentMutation() {
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TestUserContext.TENANT_0);
        entry.setStatus(CashbookConstants.Status.ARCHIVED);
        when(cashJournalEntryMapper.selectByIdForUpdate(80002L, TestUserContext.TENANT_0)).thenReturn(entry);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authorizer.checkDeleteAccess("CASH_JOURNAL", 80002L));

        assertEquals("CASH_JOURNAL_ARCHIVED_IMMUTABLE", error.getCode());
    }

    @Test
    void cashbookOnlyFinanceCannotAccessPartnerOrMaterialFiles() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 6L, "finance", List.of("FINANCE"));
        setAuthentication("cashbook:journal:query", "cashbook:journal:maintain");

        BusinessException read = assertThrows(BusinessException.class,
                () -> authorizer.checkReadAccess("PARTNER", 81001L));
        BusinessException upload = assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess("MATERIAL", 81002L));
        BusinessException delete = assertThrows(BusinessException.class,
                () -> authorizer.checkDeleteAccess("MATERIAL", 81002L));

        assertEquals("FILE_ACCESS_DENIED", read.getCode());
        assertEquals("FILE_ACCESS_DENIED", upload.getCode());
        assertEquals("FILE_ACCESS_DENIED", delete.getCode());
        verify(partnerMapper, never()).selectById(anyLong());
        verify(materialMapper, never()).selectById(anyLong());
    }

    @Test
    void cashbookOnlyFinanceCanReadAndMutateDraftCashJournalFiles() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 6L, "finance", List.of("FINANCE"));
        setAuthentication("cashbook:journal:query", "cashbook:journal:maintain");
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TestUserContext.TENANT_0);
        entry.setStatus(CashbookConstants.Status.DRAFT);
        when(cashJournalEntryMapper.selectById(82001L)).thenReturn(entry);
        when(cashJournalEntryMapper.selectByIdForUpdate(82001L, TestUserContext.TENANT_0)).thenReturn(entry);

        authorizer.checkReadAccess("CASH_JOURNAL", 82001L);
        authorizer.checkUploadAccess("CASH_JOURNAL", 82001L);
        authorizer.checkDeleteAccess("CASH_JOURNAL", 82001L);
    }

    @Test
    void genericFileAuthoritiesKeepExistingPartnerAndMaterialPaths() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 7L, "file-user", List.of("USER"));
        setAuthentication("file:query", "file:upload");
        MdPartner partner = new MdPartner();
        partner.setTenantId(TestUserContext.TENANT_0);
        MdMaterial material = new MdMaterial();
        material.setTenantId(TestUserContext.TENANT_0);
        when(partnerMapper.selectById(83001L)).thenReturn(partner);
        when(materialMapper.selectById(83002L)).thenReturn(material);

        authorizer.checkReadAccess("PARTNER", 83001L);
        authorizer.checkUploadAccess("MATERIAL", 83002L);
    }

    @Test
    void genericUploadAuthorityDoesNotAuthorizeDelete() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 7L, "file-uploader", List.of("USER"));
        setAuthentication("file:upload");

        BusinessException error = assertThrows(BusinessException.class,
                () -> authorizer.checkDeleteAccess("PARTNER", 84001L));

        assertEquals("FILE_ACCESS_DENIED", error.getCode());
        verify(partnerMapper, never()).selectById(anyLong());
    }

    @Test
    void genericDeleteAuthorityKeepsExistingDeletePath() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 8L, "file-deleter", List.of("USER"));
        setAuthentication("file:delete");
        MdPartner partner = new MdPartner();
        partner.setTenantId(TestUserContext.TENANT_0);
        when(partnerMapper.selectById(84002L)).thenReturn(partner);

        authorizer.checkDeleteAccess("PARTNER", 84002L);
    }

    @Test
    void siteDailyUsesDedicatedAuthoritiesAndSubmittedFilesAreReadOnly() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 8L, "production", List.of("PRODUCTION_MANAGER"));
        SiteDailyLog draft = new SiteDailyLog();
        draft.setTenantId(TestUserContext.TENANT_0);
        draft.setProjectId(10009L);
        draft.setStatus("DRAFT");
        when(siteDailyLogMapper.selectById(85001L)).thenReturn(draft);
        when(siteDailyLogMapper.selectByIdForUpdate(85001L, TestUserContext.TENANT_0)).thenReturn(draft);

        setAuthentication("site:daily:query", "site:daily:edit");
        authorizer.checkReadAccess("SITE_DAILY_LOG", 85001L);
        authorizer.checkUploadAccess("SITE_DAILY_LOG", 85001L);
        verify(projectAccessChecker, times(2)).checkAccess(eq(10009L), anyString());

        SiteDailyLog submitted = new SiteDailyLog();
        submitted.setTenantId(TestUserContext.TENANT_0);
        submitted.setProjectId(10009L);
        submitted.setStatus("SUBMITTED");
        when(siteDailyLogMapper.selectById(85002L)).thenReturn(submitted);
        when(siteDailyLogMapper.selectByIdForUpdate(85002L, TestUserContext.TENANT_0)).thenReturn(submitted);
        authorizer.checkReadAccess("SITE_DAILY_LOG", 85002L);
        BusinessException error = assertThrows(BusinessException.class,
                () -> authorizer.checkDeleteAccess("SITE_DAILY_LOG", 85002L));
        assertEquals("SITE_DAILY_LOG_SUBMITTED_IMMUTABLE", error.getCode());
    }

    private void setAuthentication(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", null, granted));
    }
}
