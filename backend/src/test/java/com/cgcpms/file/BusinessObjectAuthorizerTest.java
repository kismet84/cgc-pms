package com.cgcpms.file;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.expense.entity.ExpenseApplication;
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
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock BidCostMapper bidCostMapper;
    @Mock MdPartnerMapper partnerMapper;
    @Mock MdMaterialMapper materialMapper;
    @Mock CashJournalEntryMapper cashJournalEntryMapper;
    @Mock SiteDailyLogMapper siteDailyLogMapper;
    @Mock ExpenseApplicationMapper expenseApplicationMapper;
    @Mock PmProjectMapper projectMapper;
    @Mock JdbcTemplate jdbcTemplate;

    private BusinessObjectAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setAuthentication("ROLE_ADMIN");
        authorizer = new BusinessObjectAuthorizer(projectAccessChecker, contractMapper, invoiceMapper,
                receiptMapper, paymentMapper, payRecordMapper, subcontractMapper, settlementMapper,
                variationMapper, bidCostMapper, partnerMapper, materialMapper, cashJournalEntryMapper,
                siteDailyLogMapper, expenseApplicationMapper, projectMapper, jdbcTemplate);
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
    void projectFileBusinessSourceRequiresItsDomainReadAuthority() {
        setAuthentication("project:file:query", "file:query");
        when(jdbcTemplate.queryForList(anyString(), eq(90001L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                        "project_id", 10001L,
                        "source_kind", "BUSINESS",
                        "source_business_type", "CONTRACT",
                        "source_business_id", 30001L,
                        "maintain_mode", "READ_ONLY")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> authorizer.checkReadAccess("PROJECT_FILE", 90001L));

        assertEquals("FILE_ACCESS_DENIED", error.getCode());
        verify(contractMapper, never()).selectById(anyLong());
    }

    @Test
    void projectFileBusinessSourceChecksCenterProjectAndOriginalObject() {
        setAuthentication("project:file:query", "file:query", "contract:query");
        when(jdbcTemplate.queryForList(anyString(), eq(90001L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                        "project_id", 10001L,
                        "source_kind", "BUSINESS",
                        "source_business_type", "CONTRACT",
                        "source_business_id", 30001L,
                        "maintain_mode", "READ_ONLY")));
        CtContract contract = new CtContract();
        contract.setTenantId(TestUserContext.TENANT_0);
        contract.setProjectId(10001L);
        when(contractMapper.selectById(30001L)).thenReturn(contract);

        authorizer.checkReadAccess("PROJECT_FILE", 90001L);

        verify(projectAccessChecker, times(2)).checkAccess(eq(10001L), anyString());
    }

    @Test
    void paymentFileAccessChecksRealProject() {
        PayApplication payment = new PayApplication();
        payment.setTenantId(TestUserContext.TENANT_0);
        payment.setProjectId(10002L);
        payment.setApprovalStatus("DRAFT");
        when(paymentMapper.selectByIdForUpdate(40001L, TestUserContext.TENANT_0)).thenReturn(payment);

        authorizer.checkUploadAccess("PAYMENT", 40001L);

        verify(projectAccessChecker).checkAccess(10002L, "写入付款申请文件");
    }

    @Test
    void projectPartnerAndVariationMutationsLockTheirParentRows() {
        PmProject project = new PmProject();
        project.setTenantId(TestUserContext.TENANT_0);
        when(projectMapper.selectByIdForUpdate(10040L, TestUserContext.TENANT_0)).thenReturn(project);
        authorizer.checkUploadAccess("PROJECT", 10040L);

        MdPartner partner = new MdPartner();
        partner.setTenantId(TestUserContext.TENANT_0);
        when(partnerMapper.selectByIdForUpdate(20040L, TestUserContext.TENANT_0)).thenReturn(partner);
        authorizer.checkUploadAccess("PARTNER", 20040L);

        VarOrder variation = new VarOrder();
        variation.setTenantId(TestUserContext.TENANT_0);
        variation.setProjectId(10040L);
        when(variationMapper.selectByIdForUpdate(30040L, TestUserContext.TENANT_0)).thenReturn(variation);
        authorizer.checkUploadAccess("VARIATION", 30040L);

        verify(projectMapper).selectByIdForUpdate(10040L, TestUserContext.TENANT_0);
        verify(partnerMapper).selectByIdForUpdate(20040L, TestUserContext.TENANT_0);
        verify(variationMapper).selectByIdForUpdate(30040L, TestUserContext.TENANT_0);
    }

    @Test
    void ordinaryAdminCannotBypassBidFileAuthority() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> authorizer.checkReadAccess("BID_COST", 42L));

        assertEquals("FILE_ACCESS_DENIED", error.getCode());
        verify(bidCostMapper, never()).selectById(anyLong());
    }

    @Test
    void bidAuthorityCanReadUnboundBidFile() {
        BidCost bid = new BidCost();
        bid.setTenantId(TestUserContext.TENANT_0);
        when(bidCostMapper.selectById(42L)).thenReturn(bid);
        setAuthentication("bid:query");

        authorizer.checkReadAccess("BID_COST", 42L);

        verify(projectAccessChecker, never()).checkAccess(anyLong(), anyString());
    }

    @Test
    void paymentClosedLoopAuthoritiesCanUploadTheirBusinessEvidence() {
        PayApplication payment = new PayApplication();
        payment.setTenantId(TestUserContext.TENANT_0);
        payment.setProjectId(10020L);
        payment.setApprovalStatus("DRAFT");
        when(paymentMapper.selectByIdForUpdate(40020L, TestUserContext.TENANT_0)).thenReturn(payment);
        setAuthentication("payment:app:edit");
        authorizer.checkUploadAccess("PAYMENT", 40020L);

        ExpenseApplication expense = new ExpenseApplication();
        expense.setTenantId(TestUserContext.TENANT_0);
        expense.setProjectId(10021L);
        expense.setApprovalStatus("DRAFT");
        when(expenseApplicationMapper.selectByIdForUpdate(40021L, TestUserContext.TENANT_0))
                .thenReturn(expense);
        setAuthentication("expense:edit");
        authorizer.checkUploadAccess("EXPENSE", 40021L);

        PayInvoice invoice = new PayInvoice();
        invoice.setTenantId(TestUserContext.TENANT_0);
        invoice.setPayRecordId(50020L);
        invoice.setVerifyStatus("PENDING");
        PayRecord record = new PayRecord();
        record.setTenantId(TestUserContext.TENANT_0);
        record.setProjectId(10022L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class),
                eq(51020L), eq(TestUserContext.TENANT_0))).thenReturn(51020L);
        when(invoiceMapper.selectById(51020L)).thenReturn(invoice);
        when(payRecordMapper.selectById(50020L)).thenReturn(record);
        setAuthentication("invoice:edit");
        authorizer.checkUploadAccess("INVOICE", 51020L, "ELECTRONIC_INVOICE");
    }

    @Test
    void paymentEvidenceIsImmutableWhileApprovingOrApproved() {
        setAuthentication("payment:app:edit");
        for (String status : List.of("APPROVING", "APPROVED")) {
            PayApplication payment = new PayApplication();
            payment.setTenantId(TestUserContext.TENANT_0);
            payment.setProjectId(10030L);
            payment.setApprovalStatus(status);
            when(paymentMapper.selectByIdForUpdate(40030L, TestUserContext.TENANT_0)).thenReturn(payment);

            BusinessException upload = assertThrows(BusinessException.class,
                    () -> authorizer.checkUploadAccess("PAYMENT", 40030L));
            assertEquals("PAYMENT_DOCUMENT_IMMUTABLE", upload.getCode());
            BusinessException delete = assertThrows(BusinessException.class,
                    () -> authorizer.checkDeleteAccess("PAYMENT", 40030L));
            assertEquals("PAYMENT_DOCUMENT_IMMUTABLE", delete.getCode());
        }
    }

    @Test
    void commencementContractAndExpenseEvidenceLockAfterSubmission() {
        setAuthentication("project:commencement:edit");
        when(jdbcTemplate.queryForList(anyString(), eq(43001L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                        "project_id", 13001L,
                        "commencement_status", "APPROVED",
                        "project_status", "PREPARING",
                        "project_approval_status", "APPROVED",
                        "initiation_basis", "BID_AWARD")));
        BusinessException commencement = assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess(
                        "PROJECT_COMMENCEMENT", 43001L, "COMMENCEMENT_BASIS"));
        assertEquals("PROJECT_COMMENCEMENT_DOCUMENT_IMMUTABLE", commencement.getCode());
        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains("project_commencement") && sql.contains("FOR UPDATE")),
                eq(43001L), eq(TestUserContext.TENANT_0));

        CtContract contract = new CtContract();
        contract.setTenantId(TestUserContext.TENANT_0);
        contract.setProjectId(13002L);
        contract.setApprovalStatus("APPROVING");
        when(contractMapper.selectByIdForUpdate(43002L, TestUserContext.TENANT_0))
                .thenReturn(contract);
        setAuthentication("file:delete");
        BusinessException contractError = assertThrows(BusinessException.class,
                () -> authorizer.checkDeleteAccess("CONTRACT", 43002L, "CONTRACT_ATTACHMENT"));
        assertEquals("CONTRACT_DOCUMENT_IMMUTABLE", contractError.getCode());

        ExpenseApplication expense = new ExpenseApplication();
        expense.setTenantId(TestUserContext.TENANT_0);
        expense.setProjectId(13003L);
        expense.setApprovalStatus("APPROVED");
        when(expenseApplicationMapper.selectByIdForUpdate(43003L, TestUserContext.TENANT_0))
                .thenReturn(expense);
        setAuthentication("expense:edit");
        BusinessException expenseError = assertThrows(BusinessException.class,
                () -> authorizer.checkDeleteAccess("EXPENSE", 43003L, "EXPENSE_ATTACHMENT"));
        assertEquals("EXPENSE_DOCUMENT_IMMUTABLE", expenseError.getCode());
    }

    @Test
    void commencementEvidenceRequiresPreparingApprovedProject() {
        setAuthentication("project:commencement:edit");
        when(jdbcTemplate.queryForList(anyString(), eq(43004L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                        "project_id", 13004L,
                        "commencement_status", "DRAFT",
                        "project_status", "ACTIVE",
                        "project_approval_status", "APPROVED",
                        "initiation_basis", "BID_AWARD")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess(
                        "PROJECT_COMMENCEMENT", 43004L, "COMMENCEMENT_BASIS"));

        assertEquals("PROJECT_COMMENCEMENT_PROJECT_NOT_READY", error.getCode());
    }

    @Test
    void receiptEvidenceUsesTenantLockAndRejectsSubmittedStatus() {
        com.cgcpms.receipt.entity.MatReceipt receipt = new com.cgcpms.receipt.entity.MatReceipt();
        receipt.setTenantId(TestUserContext.TENANT_0);
        receipt.setProjectId(13005L);
        receipt.setApprovalStatus("APPROVING");
        when(receiptMapper.selectByIdForUpdate(43005L, TestUserContext.TENANT_0))
                .thenReturn(receipt);
        setAuthentication("receipt:edit");

        BusinessException error = assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess("MATERIAL_RECEIPT", 43005L));

        assertEquals("PROCUREMENT_DOCUMENT_IMMUTABLE", error.getCode());
        verify(receiptMapper).selectByIdForUpdate(43005L, TestUserContext.TENANT_0);
    }

    @Test
    void procurementEvidenceUsesBusinessAuthorityProjectScopeAndDraftImmutability() {
        setAuthentication("purchase:request:edit");
        when(jdbcTemplate.queryForList(anyString(), eq(61001L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                        "project_id", 11001L,
                        "approval_status", "DRAFT")));

        authorizer.checkUploadAccess("PURCHASE_REQUEST", 61001L);

        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains("mat_purchase_request") && sql.contains("FOR UPDATE")),
                eq(61001L), eq(TestUserContext.TENANT_0));
        verify(projectAccessChecker).checkAccess(11001L, "写入采购单据文件");

        when(jdbcTemplate.queryForList(anyString(), eq(61002L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                        "project_id", 11001L,
                        "approval_status", "APPROVING")));
        BusinessException immutable = assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess("PURCHASE_REQUEST", 61002L));
        assertEquals("PROCUREMENT_DOCUMENT_IMMUTABLE", immutable.getCode());
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
    void invoiceMutationLocksInvoiceAndAllowsOnlyPendingInvoiceEvidenceTypes() {
        PayInvoice invoice = new PayInvoice();
        invoice.setTenantId(TestUserContext.TENANT_0);
        invoice.setPayRecordId(50003L);
        invoice.setVerifyStatus("PENDING");
        PayRecord record = new PayRecord();
        record.setTenantId(TestUserContext.TENANT_0);
        record.setProjectId(10013L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class),
                eq(51003L), eq(TestUserContext.TENANT_0))).thenReturn(51003L);
        when(invoiceMapper.selectById(51003L)).thenReturn(invoice);
        when(payRecordMapper.selectById(50003L)).thenReturn(record);

        authorizer.checkUploadAccess("INVOICE", 51003L, "ELECTRONIC_INVOICE");
        authorizer.checkDeleteAccess("INVOICE", 51003L, "SCANNED_INVOICE");

        verify(jdbcTemplate, times(2)).queryForObject(
                argThat(sql -> sql.contains("pay_invoice") && sql.contains("FOR UPDATE")),
                eq(Long.class), eq(51003L), eq(TestUserContext.TENANT_0));
        verify(projectAccessChecker, times(2)).checkAccess(eq(10013L), anyString());

        BusinessException invalidType = assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess("INVOICE", 51003L, "OTHER"));
        assertEquals("INVOICE_DOCUMENT_TYPE_INVALID", invalidType.getCode());

        invoice.setVerifyStatus("VERIFIED");
        BusinessException immutable = assertThrows(BusinessException.class,
                () -> authorizer.checkDeleteAccess("INVOICE", 51003L, "ELECTRONIC_INVOICE"));
        assertEquals("INVOICE_DOCUMENT_IMMUTABLE", immutable.getCode());
    }

    @Test
    void salesInvoiceMutationUsesRowLockAndFreezesVerifiedEvidence() {
        when(jdbcTemplate.queryForList(anyString(), eq(52001L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                "tenant_id", TestUserContext.TENANT_0,
                "project_id", 10014L,
                "status", "FULLY_ALLOCATED",
                "verification_status", "UNVERIFIED")));
        authorizer.checkUploadAccess("SALES_INVOICE", 52001L, "ELECTRONIC_INVOICE");
        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains("sales_invoice") && sql.contains("FOR UPDATE")
                        && sql.contains("verification_status")),
                eq(52001L), eq(TestUserContext.TENANT_0));
        verify(projectAccessChecker).checkAccess(10014L, "写入收入回款业务文件");

        when(jdbcTemplate.queryForList(anyString(), eq(52002L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                "tenant_id", TestUserContext.TENANT_0,
                "project_id", 10014L,
                "status", "FULLY_ALLOCATED",
                "verification_status", "VERIFIED")));
        BusinessException immutable = assertThrows(BusinessException.class,
                () -> authorizer.checkDeleteAccess("SALES_INVOICE", 52002L, "SCANNED_INVOICE"));
        assertEquals("REVENUE_DOCUMENT_IMMUTABLE", immutable.getCode());
    }

    @Test
    void revenueAndOwnerSettlementEvidenceMutationLocksBusinessRows() {
        when(jdbcTemplate.queryForList(anyString(), eq(52003L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                "tenant_id", TestUserContext.TENANT_0,
                "project_id", 10015L,
                "approval_status", "DRAFT")));
        when(jdbcTemplate.queryForList(anyString(), eq(52004L), eq(TestUserContext.TENANT_0)))
                .thenReturn(List.of(Map.of(
                "tenant_id", TestUserContext.TENANT_0,
                "project_id", 10015L,
                "status", "DRAFT")));

        authorizer.checkUploadAccess("CONTRACT_REVENUE", 52003L, "OTHER");
        authorizer.checkUploadAccess("OWNER_SETTLEMENT", 52004L, "OTHER");

        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains("contract_revenue") && sql.contains("FOR UPDATE")),
                eq(52003L), eq(TestUserContext.TENANT_0));
        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains("owner_settlement") && sql.contains("FOR UPDATE")),
                eq(52004L), eq(TestUserContext.TENANT_0));
        verify(projectAccessChecker, times(2)).checkAccess(eq(10015L), anyString());
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
    void variationTraceAuthorityCanReadButCannotMutateAttachments() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 9L, "variation-trace", List.of("USER"));
        setAuthentication("variation:trace");
        VarOrder variation = variation("DRAFT", "NOT_SUBMITTED", 10010L);
        when(variationMapper.selectById(60010L)).thenReturn(variation);
        when(variationMapper.selectByIdForUpdate(60010L, TestUserContext.TENANT_0)).thenReturn(variation);

        authorizer.checkReadAccess("VARIATION", 60010L);
        BusinessException denied = assertThrows(BusinessException.class,
                () -> authorizer.checkVariationDocumentStage("VARIATION", 60010L, "SITE_EVIDENCE"));

        assertEquals("FILE_ACCESS_DENIED", denied.getCode());
        verify(projectAccessChecker).checkAccess(10010L, "读取变更单文件");
    }

    @Test
    void variationDocumentStagesRequireExactActionAuthorityAndState() {
        VarOrder draft = variation("DRAFT", "NOT_SUBMITTED", 10011L);
        when(variationMapper.selectByIdForUpdate(60011L, TestUserContext.TENANT_0)).thenReturn(draft);
        setAuthentication("variation:order:edit");
        authorizer.checkVariationDocumentStage("VARIATION", 60011L, "SITE_EVIDENCE");

        setAuthentication("variation:owner:submit");
        BusinessException wrongStage = assertThrows(BusinessException.class,
                () -> authorizer.checkVariationDocumentStage("VARIATION", 60011L, "OWNER_SUBMISSION"));
        assertEquals("VARIATION_DOCUMENT_STAGE_INVALID", wrongStage.getCode());

        VarOrder ownerApproved = variation("APPROVED", "INTERNAL_APPROVED", 10011L);
        when(variationMapper.selectByIdForUpdate(60011L, TestUserContext.TENANT_0)).thenReturn(ownerApproved);
        authorizer.checkVariationDocumentStage("VARIATION", 60011L, "OWNER_SUBMISSION");

        BusinessException unsupported = assertThrows(BusinessException.class,
                () -> authorizer.checkVariationDocumentStage("VARIATION", 60011L, "OTHER"));
        assertEquals("VARIATION_DOCUMENT_STAGE_INVALID", unsupported.getCode());
    }

    @Test
    void variationOwnerConfirmationRequiresSubmittedState() {
        setAuthentication("variation:owner:review");
        when(variationMapper.selectByIdForUpdate(60012L, TestUserContext.TENANT_0))
                .thenReturn(variation("APPROVED", "OWNER_SUBMITTED", 10012L));
        authorizer.checkVariationDocumentStage("VARIATION", 60012L, "OWNER_CONFIRMATION");

        when(variationMapper.selectByIdForUpdate(60012L, TestUserContext.TENANT_0))
                .thenReturn(variation("APPROVED", "OWNER_RETURNED", 10012L));
        BusinessException wrongStage = assertThrows(BusinessException.class,
                () -> authorizer.checkVariationDocumentStage("VARIATION", 60012L, "OWNER_CONFIRMATION"));
        assertEquals("VARIATION_DOCUMENT_STAGE_INVALID", wrongStage.getCode());
    }

    @Test
    void settlementFileAccessChecksRealProject() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 9L, "settlement-user", List.of("USER"));
        StlSettlement settlement = new StlSettlement();
        settlement.setTenantId(TestUserContext.TENANT_0);
        settlement.setProjectId(10006L);
        settlement.setApprovalStatus("DRAFT");
        when(settlementMapper.selectById(70001L)).thenReturn(settlement);
        when(settlementMapper.selectByIdForUpdate(70001L, TestUserContext.TENANT_0))
                .thenReturn(settlement);

        setAuthentication("settlement:query");
        authorizer.checkReadAccess("SETTLEMENT", 70001L);
        setAuthentication("settlement:edit");
        authorizer.checkUploadAccess("SETTLEMENT", 70001L);
        authorizer.checkDeleteAccess("SETTLEMENT", 70001L);

        verify(projectAccessChecker).checkAccess(10006L, "读取结算单文件");
        verify(projectAccessChecker).checkAccess(10006L, "写入结算单文件");
        verify(projectAccessChecker).checkAccess(10006L, "删除结算单文件");
        verify(settlementMapper, times(2))
                .selectByIdForUpdate(70001L, TestUserContext.TENANT_0);
    }

    @Test
    void settlementMutationRequiresEditAndEditableStatus() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 9L, "settlement-user", List.of("USER"));
        StlSettlement settlement = new StlSettlement();
        settlement.setTenantId(TestUserContext.TENANT_0);
        settlement.setProjectId(10006L);
        settlement.setApprovalStatus("APPROVING");
        when(settlementMapper.selectByIdForUpdate(70002L, TestUserContext.TENANT_0))
                .thenReturn(settlement);

        setAuthentication("settlement:query");
        assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess("SETTLEMENT", 70002L));
        setAuthentication("settlement:edit");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess("SETTLEMENT", 70002L));
        assertEquals("SETTLEMENT_DOCUMENT_IMMUTABLE", exception.getCode());
    }

    @Test
    void subcontractMeasureUsesQueryForReadAndEditForMutation() {
        TestUserContext.setUser(TestUserContext.TENANT_0, 9L, "measure-user", List.of("USER"));
        SubMeasure measure = new SubMeasure();
        measure.setTenantId(TestUserContext.TENANT_0);
        measure.setProjectId(10016L);
        measure.setApprovalStatus("DRAFT");
        when(subcontractMapper.selectById(71001L)).thenReturn(measure);
        when(subcontractMapper.selectByIdForUpdate(71001L, TestUserContext.TENANT_0))
                .thenReturn(measure);

        setAuthentication("subcontract:measure:query");
        authorizer.checkReadAccess("SUBCONTRACT", 71001L);
        BusinessException denied = assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess("SUBCONTRACT", 71001L));
        assertEquals("FILE_ACCESS_DENIED", denied.getCode());

        setAuthentication("subcontract:measure:edit");
        authorizer.checkUploadAccess("SUBCONTRACT", 71001L);
        verify(projectAccessChecker, times(2)).checkAccess(eq(10016L), anyString());
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
        when(partnerMapper.selectByIdForUpdate(84002L, TestUserContext.TENANT_0)).thenReturn(partner);

        authorizer.checkDeleteAccess("PARTNER", 84002L);
        verify(partnerMapper).selectByIdForUpdate(84002L, TestUserContext.TENANT_0);
    }

    @Test
    void communicationAttachmentsRequireMembershipOwnDraftAndFiveFileLimit() {
        long messageId = 99001L;
        setAuthentication("communication:view", "communication:send");
        Map<String, Object> row = new HashMap<>();
        row.put("sender_id", TestUserContext.USER_ADMIN);
        row.put("message_status", "DRAFT");
        row.put("seq", null);
        row.put("conversation_status", "ACTIVE");
        row.put("member_status", "ACTIVE");
        row.put("join_seq", 0L);
        row.put("leave_seq", null);
        row.put("attachment_count", 0);
        when(jdbcTemplate.queryForList(anyString(), eq(TestUserContext.USER_ADMIN),
                eq(messageId), eq(TestUserContext.TENANT_0))).thenReturn(List.of(row));

        authorizer.checkUploadAccess("COMMUNICATION_MESSAGE", messageId, "CHAT_ATTACHMENT");

        row.put("attachment_count", 5);
        assertEquals("COMMUNICATION_ATTACHMENT_LIMIT", assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess(
                        "COMMUNICATION_MESSAGE", messageId, "CHAT_ATTACHMENT")).getCode());
        row.put("attachment_count", 0);
        row.put("message_status", "SENT");
        row.put("seq", 1L);
        authorizer.checkReadAccess("COMMUNICATION_MESSAGE", messageId);
        assertEquals("COMMUNICATION_MESSAGE_IMMUTABLE", assertThrows(BusinessException.class,
                () -> authorizer.checkUploadAccess(
                        "COMMUNICATION_MESSAGE", messageId, "CHAT_ATTACHMENT")).getCode());
    }

    @Test
    void communicationAttachmentsHideCrossTenantNonMemberAndPreJoinMessages() {
        long messageId = 99002L;
        setAuthentication("communication:view");
        when(jdbcTemplate.queryForList(anyString(), eq(TestUserContext.USER_ADMIN),
                eq(messageId), eq(TestUserContext.TENANT_0))).thenReturn(List.of());
        assertEquals("FILE_BIZ_OBJ_NOT_FOUND", assertThrows(BusinessException.class,
                () -> authorizer.checkReadAccess("COMMUNICATION_MESSAGE", messageId)).getCode());

        Map<String, Object> preJoin = new HashMap<>();
        preJoin.put("sender_id", 99L);
        preJoin.put("message_status", "SENT");
        preJoin.put("seq", 1L);
        preJoin.put("conversation_status", "ACTIVE");
        preJoin.put("member_status", "ACTIVE");
        preJoin.put("join_seq", 1L);
        preJoin.put("leave_seq", null);
        preJoin.put("attachment_count", 1);
        when(jdbcTemplate.queryForList(anyString(), eq(TestUserContext.USER_ADMIN),
                eq(messageId), eq(TestUserContext.TENANT_0))).thenReturn(List.of(preJoin));
        assertEquals("FILE_BIZ_OBJ_NOT_FOUND", assertThrows(BusinessException.class,
                () -> authorizer.checkReadAccess("COMMUNICATION_MESSAGE", messageId)).getCode());
    }

    @Test
    void communicationAttachmentsHideMessagesFromBeforeLatestRejoin() {
        long messageId = 99003L;
        setAuthentication("communication:view");
        Map<String, Object> oldAttachment = new HashMap<>();
        oldAttachment.put("sender_id", 99L);
        oldAttachment.put("message_status", "SENT");
        oldAttachment.put("seq", 3L);
        oldAttachment.put("conversation_status", "ACTIVE");
        oldAttachment.put("member_status", "ACTIVE");
        oldAttachment.put("join_seq", 5L);
        oldAttachment.put("leave_seq", null);
        oldAttachment.put("attachment_count", 1);
        when(jdbcTemplate.queryForList(anyString(), eq(TestUserContext.USER_ADMIN),
                eq(messageId), eq(TestUserContext.TENANT_0))).thenReturn(List.of(oldAttachment));

        assertEquals("FILE_BIZ_OBJ_NOT_FOUND", assertThrows(BusinessException.class,
                () -> authorizer.checkReadAccess("COMMUNICATION_MESSAGE", messageId)).getCode());
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

    private VarOrder variation(String approvalStatus, String ownerStatus, Long projectId) {
        VarOrder variation = new VarOrder();
        variation.setTenantId(TestUserContext.TENANT_0);
        variation.setProjectId(projectId);
        variation.setApprovalStatus(approvalStatus);
        variation.setOwnerStatus(ownerStatus);
        return variation;
    }
}
