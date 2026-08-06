package com.cgcpms.document;

import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.MaterialReceiptDocumentDataProvider;
import com.cgcpms.receipt.service.MatReceiptService;
import com.cgcpms.receipt.vo.MatReceiptItemVO;
import com.cgcpms.receipt.vo.MatReceiptVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialReceiptDocumentDataProviderTest {
    @Test
    @SuppressWarnings("unchecked")
    void previewMapsSafeReceiptDetailsFromPublicService() {
        MatReceiptService service = mock(MatReceiptService.class);
        MaterialReceiptDocumentDataProvider provider = new MaterialReceiptDocumentDataProvider(service);
        MatReceiptVO receipt = new MatReceiptVO();
        receipt.setReceiptCode("RC-201"); receipt.setOrderCode("PO-1"); receipt.setPartnerName("供应商");
        receipt.setTotalAmount("1234.5"); receipt.setApprovalStatus("DRAFT");
        MatReceiptItemVO item = new MatReceiptItemVO();
        item.setMaterialName("钢筋"); item.setAcceptedQuantity("2.000");
        receipt.setItems(List.of(item));
        when(service.getById(201L)).thenReturn(receipt);

        DocumentDataSnapshot snapshot = provider.loadPreview(201L);

        assertEquals("material-receipt.v2", snapshot.schemaVersion());
        assertEquals("PO-1", ((Map<String, Object>) snapshot.values().get("order")).get("code"));
        assertEquals("2", ((List<Map<String, Object>>) snapshot.values().get("items")).get(0).get("acceptedQuantity"));
        assertFalse(snapshot.values().containsKey("signatures"));
    }
}
