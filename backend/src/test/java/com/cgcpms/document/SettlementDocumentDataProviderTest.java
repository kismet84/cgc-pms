package com.cgcpms.document;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.SettlementDocumentDataProvider;
import com.cgcpms.settlement.service.StlSettlementQueryService;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementDocumentDataProviderTest {
    private final StlSettlementQueryService service = mock(StlSettlementQueryService.class);
    private final SettlementDocumentDataProvider provider = new SettlementDocumentDataProvider(service);

    @Test
    void formalDocumentRequiresApprovedAndFinalizedState() {
        when(service.getById(1L)).thenReturn(settlement("APPROVED", "CALCULATED"));
        BusinessException error = assertThrows(BusinessException.class, () -> provider.load(1L));
        assertEquals("DOCUMENT_SETTLEMENT_NOT_FINALIZED", error.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsFinalizedSettlementWithoutInternalEvidenceCollections() {
        StlSettlementVO settlement = settlement("APPROVED", "FINALIZED");
        StlSettlementItemVO item = new StlSettlementItemVO();
        item.setItemName("结算明细"); item.setQuantity("2"); item.setUnitPrice("50"); item.setAmount("100");
        settlement.setItems(List.of(item));
        when(service.getById(1L)).thenReturn(settlement);
        when(service.getVariations(1L)).thenReturn(List.of());
        when(service.getPayments(1L)).thenReturn(List.of());
        when(service.getCosts(1L)).thenReturn(List.of());

        DocumentDataSnapshot snapshot = provider.load(1L);

        assertEquals("settlement.v2", snapshot.schemaVersion());
        Map<String, Object> header = (Map<String, Object>) snapshot.values().get("settlement");
        assertEquals("112000.00", header.get("finalAmount"));
        assertEquals("结算明细", ((List<Map<String, Object>>) snapshot.values().get("items")).get(0).get("name"));
        assertFalse(snapshot.values().containsKey("attachments"));
        assertFalse(snapshot.values().containsKey("approvalRecords"));
    }

    private StlSettlementVO settlement(String approvalStatus, String settlementStatus) {
        StlSettlementVO value = new StlSettlementVO();
        value.setSettlementCode("STL-001"); value.setApprovalStatus(approvalStatus); value.setSettlementStatus(settlementStatus);
        value.setStatus(approvalStatus); value.setProjectName("示范项目"); value.setFinalAmount("112000");
        value.setContractAmount("100000"); value.setChangeAmount("5000"); value.setMeasuredAmount("108000");
        value.setDeductionAmount("1000"); value.setPaidAmount("20000"); value.setUnpaidAmount("86400"); value.setWarrantyAmount("5600");
        return value;
    }
}
