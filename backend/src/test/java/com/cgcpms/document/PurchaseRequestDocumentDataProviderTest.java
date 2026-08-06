package com.cgcpms.document;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.PurchaseRequestDocumentDataProvider;
import com.cgcpms.purchase.service.MatPurchaseRequestService;
import com.cgcpms.purchase.vo.MatPurchaseRequestItemVO;
import com.cgcpms.purchase.vo.MatPurchaseRequestVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseRequestDocumentDataProviderTest {
    private final MatPurchaseRequestService service = mock(MatPurchaseRequestService.class);
    private final PurchaseRequestDocumentDataProvider provider = new PurchaseRequestDocumentDataProvider(service);

    @Test
    @SuppressWarnings("unchecked")
    void mapsApprovedRequestFromPublicService() {
        MatPurchaseRequestVO request = new MatPurchaseRequestVO();
        request.setRequestCode("PR-101"); request.setTotalAmount("35000");
        request.setApprovalStatus("APPROVED"); request.setStatus("CONVERTED"); request.setProjectName("示范项目");
        MatPurchaseRequestItemVO item = new MatPurchaseRequestItemVO();
        item.setMaterialName("钢筋"); item.setQuantity("10.000"); item.setApprovedQuantity("8.000");
        when(service.getById(101L)).thenReturn(request);
        when(service.getItems(101L)).thenReturn(List.of(item));

        DocumentDataSnapshot snapshot = provider.load(101L);

        assertEquals("purchase-request.v3", snapshot.schemaVersion());
        assertEquals("PR-101", ((Map<String, Object>) snapshot.values().get("purchaseRequest")).get("requestCode"));
        assertEquals("8", ((List<Map<String, Object>>) snapshot.values().get("items")).get(0).get("approvedQuantity"));
        assertFalse(snapshot.values().containsKey("approvalRecords"));
    }

    @Test
    void rejectsDraftFormalGeneration() {
        MatPurchaseRequestVO request = new MatPurchaseRequestVO();
        request.setApprovalStatus("DRAFT");
        when(service.getById(1L)).thenReturn(request);
        assertThrows(BusinessException.class, () -> provider.load(1L));
    }
}
