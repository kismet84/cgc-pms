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
public class PurchaseRequestDocumentDataProvider extends ProcurementDocumentDataSupport implements DocumentDataProvider {
    public static final String SCHEMA_VERSION = "purchase-request.v2";

    public PurchaseRequestDocumentDataProvider(JdbcTemplate jdbc) { super(jdbc); }

    @Override public String businessType() { return "PURCHASE_REQUEST"; }
    @Override public DocumentDataSnapshot load(Long businessId) { return snapshot(businessId, true); }
    @Override public DocumentDataSnapshot loadPreview(Long businessId) { return snapshot(businessId, false); }

    private DocumentDataSnapshot snapshot(Long businessId, boolean formal) {
        Long tenantId = tenantId();
        Map<String, Object> source = one("""
                SELECT r.id,r.request_code,r.approval_status,r.status,r.created_by,r.created_at,
                       r.remark AS technical_quality_brand_requirements,
                       p.project_code,p.project_name,u.real_name AS applicant_name,d.dept_name AS department_name,
                       (SELECT MIN(i.planned_date) FROM mat_purchase_request_item i
                         WHERE i.tenant_id=r.tenant_id AND i.request_id=r.id AND i.deleted_flag=0) AS plan_date
                FROM mat_purchase_request r
                JOIN pm_project p ON p.id=r.project_id AND p.tenant_id=r.tenant_id AND p.deleted_flag=0
                LEFT JOIN sys_user u ON u.id=r.created_by AND u.tenant_id=r.tenant_id AND u.deleted_flag=0
                LEFT JOIN org_department d ON d.id=u.org_id AND d.tenant_id=r.tenant_id AND d.deleted_flag=0
                WHERE r.tenant_id=? AND r.id=? AND r.deleted_flag=0
                """, tenantId, businessId);
        String approval = String.valueOf(value(source, "approval_status"));
        String status = String.valueOf(value(source, "status"));
        if (formal && !("APPROVED".equals(approval) && List.of("APPROVED", "CONVERTED").contains(status))) {
            throw new BusinessException("DOCUMENT_PURCHASE_REQUEST_NOT_APPROVED", "正式采购申请文档仅允许审批通过后生成");
        }
        if (!formal && !List.of("APPROVING", "APPROVED").contains(approval)) {
            throw new BusinessException("DOCUMENT_PURCHASE_REQUEST_PREVIEW_STATE_INVALID", "采购申请仅允许审批中或审批通过后预览");
        }

        Map<String, Object> request = row();
        for (String key : List.of("id", "request_code", "plan_date",
                "technical_quality_brand_requirements", "approval_status", "status", "created_at")) {
            put(request, camel(key), value(source, key));
        }
        Map<String, Object> project = row();
        put(project, "code", value(source, "project_code"));
        put(project, "name", value(source, "project_name"));
        Map<String, Object> applicant = row();
        put(applicant, "id", value(source, "created_by"));
        put(applicant, "name", value(source, "applicant_name"));
        put(applicant, "department", value(source, "department_name"));

        List<Map<String, Object>> records = approvalRecords(businessType(), businessId, tenantId);
        Map<String, Object> workflow = workflow(businessType(), businessId, tenantId);
        Map<String, Object> signatures = row();
        put(signatures, "applicant", "");
        put(signatures, "departmentManager", operator(records, "DEPARTMENT_MANAGER"));
        put(signatures, "projectManager", operator(records, "PROJECT_MANAGER"));
        put(signatures, "approvalDate", workflow.get("endedAt"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("purchaseRequest", request);
        root.put("project", project);
        root.put("applicant", applicant);
        root.put("items", items(businessId, tenantId));
        root.put("approval", workflow);
        root.put("approvalRecords", records);
        root.put("signatures", signatures);
        root.put("attachments", attachments(businessType(), businessId, tenantId));
        return new DocumentDataSnapshot(SCHEMA_VERSION, root);
    }

    private List<Map<String, Object>> items(Long businessId, Long tenantId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : query("""
                SELECT COALESCE(NULLIF(TRIM(i.material_name),''),m.material_name) AS material_name,
                       COALESCE(NULLIF(TRIM(i.specification),''),m.specification) AS specification,
                       COALESCE(NULLIF(TRIM(i.unit),''),m.unit) AS unit,
                       i.quantity,i.approved_quantity,i.use_location,i.planned_date,i.remark
                FROM mat_purchase_request_item i
                LEFT JOIN md_material m ON m.id=i.material_id AND m.tenant_id=i.tenant_id AND m.deleted_flag=0
                WHERE i.tenant_id=? AND i.request_id=? AND i.deleted_flag=0 ORDER BY i.id
                """, tenantId, businessId)) {
            Map<String, Object> row = row();
            put(row, "materialName", value(source, "material_name"));
            put(row, "specification", value(source, "specification"));
            put(row, "unit", value(source, "unit"));
            decimal(row, "quantity", value(source, "quantity"));
            decimal(row, "approvedQuantity", value(source, "approved_quantity"));
            put(row, "useLocation", value(source, "use_location"));
            put(row, "requiredArrivalDate", value(source, "planned_date"));
            put(row, "remark", value(source, "remark"));
            result.add(row);
        }
        return result;
    }

    private String operator(List<Map<String, Object>> records, String nodeCode) {
        return records.stream().filter(row -> nodeCode.equals(row.get("nodeCode")))
                .map(row -> String.valueOf(row.get("operator"))).reduce((first, second) -> second).orElse("");
    }

    private String camel(String key) {
        StringBuilder result = new StringBuilder(); boolean upper = false;
        for (char c : key.toCharArray()) { if (c == '_') { upper = true; } else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; } }
        return result.toString();
    }
}
