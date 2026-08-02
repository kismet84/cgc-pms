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
public class PurchaseOrderDocumentDataProvider extends ProcurementDocumentDataSupport implements DocumentDataProvider {
    public static final String SCHEMA_VERSION = "purchase-order.v1";

    public PurchaseOrderDocumentDataProvider(JdbcTemplate jdbc) { super(jdbc); }

    @Override public String businessType() { return "PURCHASE_ORDER"; }
    @Override public DocumentDataSnapshot load(Long businessId) { return snapshot(businessId, true); }
    @Override public DocumentDataSnapshot loadPreview(Long businessId) { return snapshot(businessId, false); }

    private DocumentDataSnapshot snapshot(Long businessId, boolean formal) {
        Long tenantId = tenantId();
        Map<String, Object> source = one("""
                SELECT o.id,o.order_code,o.order_type,o.order_date,o.delivery_date,o.delivery_terms,
                       o.total_amount,o.approval_status,o.order_status,o.pricing_mode,o.budget_revision,o.remark,
                       p.project_code,p.project_name,r.request_code,c.contract_code,c.contract_name,
                       s.partner_code,s.partner_name
                FROM mat_purchase_order o
                JOIN pm_project p ON p.id=o.project_id AND p.tenant_id=o.tenant_id AND p.deleted_flag=0
                JOIN ct_contract c ON c.id=o.contract_id AND c.tenant_id=o.tenant_id AND c.deleted_flag=0
                JOIN md_partner s ON s.id=o.partner_id AND s.tenant_id=o.tenant_id AND s.deleted_flag=0
                LEFT JOIN mat_purchase_request r ON r.id=o.request_id AND r.tenant_id=o.tenant_id AND r.deleted_flag=0
                WHERE o.tenant_id=? AND o.id=? AND o.deleted_flag=0
                """, tenantId, businessId);
        String approval = String.valueOf(value(source, "approval_status"));
        if (formal && !"APPROVED".equals(approval)) {
            throw new BusinessException("DOCUMENT_PURCHASE_ORDER_NOT_APPROVED", "正式采购订单文档仅允许审批通过后生成");
        }
        if (!formal && !List.of("DRAFT", "REJECTED").contains(approval)) {
            throw new BusinessException("DOCUMENT_PURCHASE_ORDER_PREVIEW_STATE_INVALID", "采购订单仅允许草稿或驳回状态预览");
        }
        Map<String, Object> order = row();
        for (String key : List.of("id", "order_code", "order_type", "order_date", "delivery_date",
                "delivery_terms", "approval_status", "order_status", "pricing_mode", "budget_revision", "remark")) {
            put(order, camel(key), value(source, key));
        }
        money(order, "totalAmount", value(source, "total_amount"));
        put(order, "totalAmountChinese", chineseMoney(value(source, "total_amount")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("purchaseOrder", order);
        root.put("project", named(source, "project_code", "project_name"));
        root.put("request", named(source, "request_code", "request_code"));
        root.put("contract", named(source, "contract_code", "contract_name"));
        root.put("supplier", named(source, "partner_code", "partner_name"));
        root.put("items", items(businessId, tenantId));
        root.put("approval", workflow(businessType(), businessId, tenantId));
        root.put("approvalRecords", approvalRecords(businessType(), businessId, tenantId));
        root.put("attachments", attachments(businessType(), businessId, tenantId));
        return new DocumentDataSnapshot(SCHEMA_VERSION, root);
    }

    private List<Map<String, Object>> items(Long businessId, Long tenantId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : query("""
                SELECT COALESCE(i.material_name,material.material_name) AS material_name,
                       COALESCE(i.specification,material.specification) AS specification,
                       COALESCE(i.unit,material.unit) AS unit,i.quantity,i.unit_price,i.tax_rate,i.amount,
                       i.tax_amount,i.amount_without_tax,i.quantity_adjust_reason,i.contract_item_id,i.price_source,
                       i.price_source_receipt_item_id,receipt.receipt_code AS price_source_receipt_code,
                       purchase_order.pricing_mode,
                       budget.id AS budget_line_id,subject.subject_code AS budget_subject_code,
                       subject.subject_name AS budget_subject_name,wbs.task_code AS wbs_task_code,wbs.task_name AS wbs_task_name,
                       i.remark
                FROM mat_purchase_order_item i
                JOIN mat_purchase_order purchase_order ON purchase_order.id=i.order_id
                  AND purchase_order.tenant_id=i.tenant_id AND purchase_order.deleted_flag=0
                JOIN md_material material ON material.id=i.material_id
                  AND material.tenant_id=i.tenant_id AND material.deleted_flag=0
                LEFT JOIN mat_receipt_item source_item ON source_item.id=i.price_source_receipt_item_id
                  AND source_item.tenant_id=i.tenant_id AND source_item.deleted_flag=0
                LEFT JOIN mat_receipt receipt ON receipt.id=source_item.receipt_id
                  AND receipt.tenant_id=i.tenant_id AND receipt.deleted_flag=0
                LEFT JOIN project_budget_line budget ON budget.id=i.budget_line_id
                  AND budget.tenant_id=i.tenant_id AND budget.deleted_flag=0
                LEFT JOIN cost_subject subject ON subject.id=budget.cost_subject_id
                  AND subject.tenant_id=i.tenant_id AND subject.deleted_flag=0
                LEFT JOIN project_wbs_task wbs ON wbs.id=i.wbs_task_id
                  AND wbs.tenant_id=i.tenant_id AND wbs.deleted_flag=0
                WHERE i.tenant_id=? AND i.order_id=? AND i.deleted_flag=0 ORDER BY i.id
                """, tenantId, businessId)) {
            Map<String, Object> row = row();
            put(row, "materialName", value(source, "material_name"));
            put(row, "specification", value(source, "specification"));
            put(row, "unit", value(source, "unit"));
            decimal(row, "quantity", value(source, "quantity"));
            money(row, "unitPrice", value(source, "unit_price"));
            decimal(row, "taxRate", value(source, "tax_rate"));
            money(row, "amount", value(source, "amount"));
            money(row, "taxAmount", value(source, "tax_amount"));
            money(row, "amountWithoutTax", value(source, "amount_without_tax"));
            put(row, "quantityAdjustReason", value(source, "quantity_adjust_reason"));
            Map<String, Object> priceSource = row();
            Object receiptItemId = value(source, "price_source_receipt_item_id");
            String sourceType = String.valueOf(value(source, "price_source"));
            put(priceSource, "type", sourceType == null || sourceType.isBlank() || "null".equals(sourceType)
                    ? "UNKNOWN" : sourceType);
            put(priceSource, "referenceId", "RECENT_RECEIPT".equals(sourceType) ? receiptItemId
                    : "CONTRACT_ITEM".equals(sourceType) ? value(source, "contract_item_id") : null);
            put(priceSource, "receiptCode", value(source, "price_source_receipt_code"));
            row.put("priceSource", priceSource);
            Map<String, Object> budget = row();
            put(budget, "lineId", value(source, "budget_line_id"));
            put(budget, "subjectCode", value(source, "budget_subject_code"));
            put(budget, "subjectName", value(source, "budget_subject_name"));
            row.put("budget", budget);
            Map<String, Object> wbs = row();
            put(wbs, "code", value(source, "wbs_task_code"));
            put(wbs, "name", value(source, "wbs_task_name"));
            row.put("wbs", wbs);
            put(row, "remark", value(source, "remark"));
            result.add(row);
        }
        return result;
    }

    private Map<String, Object> named(Map<String, Object> source, String codeKey, String nameKey) {
        Map<String, Object> row = row();
        put(row, "code", value(source, codeKey));
        put(row, "name", value(source, nameKey));
        return row;
    }

    private String camel(String key) {
        StringBuilder result = new StringBuilder(); boolean upper = false;
        for (char c : key.toCharArray()) { if (c == '_') upper = true; else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; } }
        return result.toString();
    }
}
