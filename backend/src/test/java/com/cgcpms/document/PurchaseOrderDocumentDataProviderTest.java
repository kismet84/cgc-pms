package com.cgcpms.document;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.PurchaseOrderDocumentDataProvider;
import com.cgcpms.purchase.service.MatPurchaseOrderService;
import com.cgcpms.purchase.vo.MatPurchaseOrderItemVO;
import com.cgcpms.purchase.vo.MatPurchaseOrderVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderDocumentDataProviderTest {
    private final MatPurchaseOrderService service = mock(MatPurchaseOrderService.class);
    private final PurchaseOrderDocumentDataProvider provider = new PurchaseOrderDocumentDataProvider(service);

    @Test
    @SuppressWarnings("unchecked")
    void mapsApprovedOrderFromPublicService() {
        MatPurchaseOrderVO order = new MatPurchaseOrderVO();
        order.setOrderCode("PO-301"); order.setTotalAmount("35000");
        order.setApprovalStatus("APPROVED"); order.setOrderStatus("PERFORMING"); order.setProjectName("示范项目");
        MatPurchaseOrderItemVO item = new MatPurchaseOrderItemVO();
        item.setMaterialName("钢筋"); item.setQuantity("10"); item.setUnitPrice("3500"); item.setAmount("35000");
        order.setItems(List.of(item));
        when(service.getById(301L)).thenReturn(order);

        DocumentDataSnapshot snapshot = provider.load(301L);

        assertEquals("purchase-order.v2", snapshot.schemaVersion());
        assertEquals("35000.00", ((Map<String, Object>) snapshot.values().get("purchaseOrder")).get("totalAmount"));
        assertEquals("3500.00", ((List<Map<String, Object>>) snapshot.values().get("items")).get(0).get("unitPrice"));
        assertFalse(snapshot.values().containsKey("approvalRecords"));
    }

    @Test
    void draftCanPreviewButCannotGenerateFormalDocument() {
        MatPurchaseOrderVO order = new MatPurchaseOrderVO();
        order.setOrderCode("PO-302"); order.setApprovalStatus("DRAFT");
        when(service.getById(302L)).thenReturn(order);
        assertEquals("purchase-order.v2", provider.loadPreview(302L).schemaVersion());
        assertThrows(BusinessException.class, () -> provider.load(302L));
    }
}
