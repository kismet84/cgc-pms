package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.SettlementDocumentDataProvider;
import com.cgcpms.settlement.service.StlSettlementQueryService;
import com.cgcpms.settlement.vo.SettlementApprovalRecordVO;
import com.cgcpms.settlement.vo.SettlementAttachmentVO;
import com.cgcpms.settlement.vo.SettlementCostItemVO;
import com.cgcpms.settlement.vo.SettlementPaymentItemVO;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.variation.vo.VarOrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementDocumentDataProviderTest {
    @Mock private StlSettlementQueryService settlementQueryService;

    private SettlementDocumentDataProvider provider;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        provider = new SettlementDocumentDataProvider(settlementQueryService);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void formalDocumentRequiresApprovedAndFinalizedState() {
        StlSettlementVO settlement = settlement("APPROVED", "CALCULATED");
        when(settlementQueryService.getById(1L)).thenReturn(settlement);

        BusinessException error = assertThrows(BusinessException.class, () -> provider.load(1L));

        assertEquals("DOCUMENT_SETTLEMENT_NOT_FINALIZED", error.getCode());
    }

    @Test
    void previewAllowsApprovingWithoutRelaxingFormalGeneration() {
        StlSettlementVO settlement = settlement("APPROVING", "CALCULATED");
        stubRelatedData();
        when(settlementQueryService.getById(1L)).thenReturn(settlement);

        assertEquals("settlement.v1", provider.loadPreview(1L).schemaVersion());
        BusinessException formal = assertThrows(BusinessException.class, () -> provider.load(1L));
        assertEquals("DOCUMENT_SETTLEMENT_NOT_FINALIZED", formal.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerUsesAuthoritativeSettlementViewAndStableRelatedRows() {
        StlSettlementVO settlement = settlement("APPROVED", "FINALIZED");
        StlSettlementItemVO later = new StlSettlementItemVO();
        later.setId("20"); later.setItemName("明细-20"); later.setUnit("项");
        later.setQuantity("2"); later.setUnitPrice("50"); later.setAmount("100");
        StlSettlementItemVO first = new StlSettlementItemVO();
        first.setId("10"); first.setItemName("明细-10"); first.setUnit("项");
        first.setQuantity("1"); first.setUnitPrice("60"); first.setAmount("60");
        settlement.setItems(List.of(later, first));

        VarOrderVO variation = new VarOrderVO();
        variation.setId("2"); variation.setVarCode("VAR-001"); variation.setVarName("现场变更");
        variation.setConfirmedAmount("800.00"); variation.setApprovalStatus("APPROVED");
        SettlementPaymentItemVO payment = new SettlementPaymentItemVO();
        payment.setId("3"); payment.setApplyCode("PAY-001"); payment.setApplyAmount("1200");
        payment.setApprovedAmount("1100"); payment.setActualPayAmount("1000"); payment.setPayStatus("PAID");
        SettlementCostItemVO cost = new SettlementCostItemVO();
        cost.setId("4"); cost.setCostSubjectName("人工费"); cost.setAmount("300");
        cost.setTaxAmount("10"); cost.setAmountWithoutTax("290");
        SettlementAttachmentVO attachment = new SettlementAttachmentVO();
        attachment.setId("5"); attachment.setOriginalName("结算依据.pdf"); attachment.setFileSize(1024L);
        SettlementApprovalRecordVO approval = new SettlementApprovalRecordVO();
        approval.setId("6"); approval.setNodeName("商务审核"); approval.setActionName("同意");
        approval.setOperatorName("测试管理员"); approval.setCreatedAt("2026-07-17 12:00:00");
        when(settlementQueryService.getById(1L)).thenReturn(settlement);
        when(settlementQueryService.getVariations(1L)).thenReturn(List.of(variation));
        when(settlementQueryService.getPayments(1L)).thenReturn(List.of(payment));
        when(settlementQueryService.getCosts(1L)).thenReturn(List.of(cost));
        when(settlementQueryService.getAttachments(1L)).thenReturn(List.of(attachment));
        when(settlementQueryService.getApprovalRecords(1L)).thenReturn(List.of(approval));

        DocumentDataSnapshot snapshot = provider.load(1L);

        Map<String, Object> root = snapshot.values();
        Map<String, Object> document = (Map<String, Object>) root.get("settlement");
        Map<String, Object> amounts = (Map<String, Object>) document.get("amount");
        List<Map<String, Object>> items = (List<Map<String, Object>>) document.get("items");
        assertEquals("112000.00", amounts.get("final"));
        assertEquals("86400.00", amounts.get("unpaid"));
        assertEquals("明细-10", items.get(0).get("name"));
        assertEquals("800.00", ((List<Map<String, Object>>) document.get("variations")).get(0).get("confirmedAmount"));
        assertEquals("1000.00", ((List<Map<String, Object>>) document.get("payments")).get(0).get("actualPayAmount"));
        assertEquals("结算依据.pdf", ((List<Map<String, Object>>) document.get("attachments")).get(0).get("name"));
        assertEquals("示范项目", ((Map<String, Object>) root.get("project")).get("name"));
        assertTrue(root.containsKey("audit"));
    }

    private StlSettlementVO settlement(String approvalStatus, String settlementStatus) {
        StlSettlementVO value = new StlSettlementVO();
        value.setId("1"); value.setSettlementCode("STL-001"); value.setSettlementType("FINAL");
        value.setStatus(approvalStatus); value.setApprovalStatus(approvalStatus); value.setSettlementStatus(settlementStatus);
        value.setAmountFormulaVersion("settlement.v1"); value.setProjectId("100"); value.setProjectName("示范项目");
        value.setContractId("200"); value.setContractName("示范合同"); value.setPartnerId("300"); value.setPartnerName("示范单位");
        value.setContractAmount("100000"); value.setChangeAmount("5000"); value.setMeasuredAmount("108000");
        value.setDeductionAmount("1000"); value.setPaidAmount("20000"); value.setFinalAmount("112000");
        value.setUnpaidAmount("86400"); value.setWarrantyAmount("5600"); value.setFinalizedAt("2026-07-17 11:00:00");
        value.setCreatedBy("1"); value.setCreatedAt("2026-07-17 09:00:00"); value.setUpdatedAt("2026-07-17 11:00:00");
        return value;
    }

    private void stubRelatedData() {
        when(settlementQueryService.getVariations(1L)).thenReturn(List.of());
        when(settlementQueryService.getPayments(1L)).thenReturn(List.of());
        when(settlementQueryService.getCosts(1L)).thenReturn(List.of());
        when(settlementQueryService.getAttachments(1L)).thenReturn(List.of());
        when(settlementQueryService.getApprovalRecords(1L)).thenReturn(List.of());
    }
}
