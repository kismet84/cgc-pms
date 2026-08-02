package com.cgcpms.document.provider;

import com.cgcpms.common.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("!document-test-provider")
public class MaterialReceiptDocumentDataProvider extends ProcurementDocumentDataSupport implements DocumentDataProvider {
    public static final String SCHEMA_VERSION = "material-receipt.v1";

    public MaterialReceiptDocumentDataProvider(JdbcTemplate jdbc) { super(jdbc); }

    @Override public String businessType() { return "MATERIAL_RECEIPT"; }
    @Override public DocumentDataSnapshot load(Long businessId) { return snapshot(businessId, true); }
    @Override public DocumentDataSnapshot loadPreview(Long businessId) { return snapshot(businessId, false); }

    private DocumentDataSnapshot snapshot(Long businessId, boolean formal) {
        Long tenantId = tenantId();
        Map<String, Object> source = one("""
                SELECT r.id,r.receipt_code,r.system_batch_no,r.delivery_note_no,r.receipt_date,r.receipt_mode,
                       r.total_amount,r.approval_status,r.remark,r.receiver_id,
                       p.project_code,p.project_name,o.order_code,c.contract_code,c.contract_name,
                       s.partner_code,s.partner_name,w.warehouse_code,w.warehouse_name,u.real_name AS receiver_name,
                       pm.real_name AS project_manager_name
                FROM mat_receipt r
                JOIN pm_project p ON p.id=r.project_id AND p.tenant_id=r.tenant_id AND p.deleted_flag=0
                JOIN mat_purchase_order o ON o.id=r.order_id AND o.tenant_id=r.tenant_id AND o.deleted_flag=0
                JOIN ct_contract c ON c.id=r.contract_id AND c.tenant_id=r.tenant_id AND c.deleted_flag=0
                JOIN md_partner s ON s.id=r.partner_id AND s.tenant_id=r.tenant_id AND s.deleted_flag=0
                LEFT JOIN mat_warehouse w ON w.id=r.warehouse_id AND w.tenant_id=r.tenant_id AND w.deleted_flag=0
                LEFT JOIN sys_user u ON u.id=r.receiver_id AND u.tenant_id=r.tenant_id AND u.deleted_flag=0
                LEFT JOIN sys_user pm ON pm.id=p.project_manager_id AND pm.tenant_id=r.tenant_id AND pm.deleted_flag=0
                WHERE r.tenant_id=? AND r.id=? AND r.deleted_flag=0
                """, tenantId, businessId);
        String approval = String.valueOf(value(source, "approval_status"));
        if (formal && !"APPROVED".equals(approval)) {
            throw new BusinessException("DOCUMENT_MATERIAL_RECEIPT_NOT_APPROVED", "正式材料验收文档仅允许审批通过后生成");
        }
        if (!formal && !List.of("DRAFT", "REJECTED").contains(approval)) {
            throw new BusinessException("DOCUMENT_MATERIAL_RECEIPT_PREVIEW_STATE_INVALID", "材料验收仅允许草稿或驳回状态预览签字件");
        }

        Map<String, Object> receipt = row();
        put(receipt, "id", value(source, "id"));
        put(receipt, "receiptCode", value(source, "receipt_code"));
        put(receipt, "systemBatchNo", value(source, "system_batch_no"));
        put(receipt, "deliveryNoteNo", value(source, "delivery_note_no"));
        put(receipt, "receiptDate", value(source, "receipt_date"));
        put(receipt, "receiptMode", value(source, "receipt_mode"));
        put(receipt, "approvalStatus", value(source, "approval_status"));
        money(receipt, "totalAmount", value(source, "total_amount"));
        put(receipt, "totalAmountChinese", chineseMoney(value(source, "total_amount")));
        put(receipt, "remark", value(source, "remark"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("receipt", receipt);
        root.put("project", named(source, "project_code", "project_name"));
        root.put("order", named(source, "order_code", "order_code"));
        root.put("contract", named(source, "contract_code", "contract_name"));
        root.put("supplier", named(source, "partner_code", "partner_name"));
        root.put("warehouse", named(source, "warehouse_code", "warehouse_name"));
        root.put("items", items(businessId, tenantId));
        root.put("approvalRecords", approvalRecords(businessType(), businessId, tenantId));
        root.put("attachments", attachments(businessType(), businessId, tenantId));
        Map<String, Object> signatures = row();
        put(signatures, "supplierRepresentative", "");
        put(signatures, "receiver", value(source, "receiver_name"));
        put(signatures, "projectManager", value(source, "project_manager_name"));
        put(signatures, "warehouseKeeperOrUser", "");
        root.put("signatures", signatures);
        return new DocumentDataSnapshot(SCHEMA_VERSION, root);
    }

    private Map<String, Object> named(Map<String, Object> source, String codeKey, String nameKey) {
        Map<String, Object> row = row();
        put(row, "code", value(source, codeKey));
        put(row, "name", value(source, nameKey));
        return row;
    }

    private List<Map<String, Object>> items(Long businessId, Long tenantId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : query("""
                SELECT m.material_name,m.specification,m.unit,oi.quantity AS order_quantity,
                       oi.received_quantity,i.qualified_quantity,i.unit_price,i.amount,i.use_location,i.remark
                FROM mat_receipt_item i
                JOIN mat_purchase_order_item oi ON oi.id=i.order_item_id AND oi.tenant_id=i.tenant_id AND oi.deleted_flag=0
                JOIN md_material m ON m.id=i.material_id AND m.tenant_id=i.tenant_id AND m.deleted_flag=0
                WHERE i.tenant_id=? AND i.receipt_id=? AND i.deleted_flag=0 ORDER BY i.id
                """, tenantId, businessId)) {
            Map<String, Object> row = row();
            put(row, "materialName", value(source, "material_name"));
            put(row, "specification", value(source, "specification"));
            put(row, "unit", value(source, "unit"));
            decimal(row, "orderQuantity", value(source, "order_quantity"));
            decimal(row, "cumulativeReceivedQuantity", value(source, "received_quantity"));
            decimal(row, "acceptedQuantity", value(source, "qualified_quantity"));
            money(row, "unitPrice", value(source, "unit_price"));
            money(row, "amount", value(source, "amount"));
            put(row, "useLocation", value(source, "use_location"));
            put(row, "remark", value(source, "remark"));
            result.add(row);
        }
        return result;
    }
}
