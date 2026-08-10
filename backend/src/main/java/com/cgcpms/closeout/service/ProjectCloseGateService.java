package com.cgcpms.closeout.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectCloseGateService {
    private final JdbcTemplate jdbc;

    public Map<String, Object> gates(Long closeoutId, Long projectId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("constructionCompletion", constructionCompletion(closeoutId, projectId));
        result.put("warrantyEntry", warrantyEntry(closeoutId, projectId));
        result.put("finalClose", finalClose(closeoutId, projectId));
        return result;
    }

    public List<GateBlocker> constructionCompletion(Long closeoutId, Long projectId) {
        List<GateBlocker> blockers = new ArrayList<>();
        addMissingActiveSchedule(blockers, projectId);
        addRows(blockers, "CONSTRUCTION_WBS_INCOMPLETE", "WBS", "仍有未完成WBS任务", """
                SELECT w.id biz_id FROM project_wbs_task w
                JOIN project_schedule_plan p ON p.tenant_id=w.tenant_id AND p.id=w.schedule_plan_id
                WHERE w.tenant_id=? AND w.project_id=? AND w.deleted_flag=0
                  AND p.status='ACTIVE' AND p.deleted_flag=0 AND w.status<>'COMPLETED'
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_QUALITY_ACCEPTANCE_MISSING", "QUALITY", "WBS缺少直接关联的合格质量验收", """
                SELECT w.id biz_id FROM project_wbs_task w
                JOIN project_schedule_plan p ON p.tenant_id=w.tenant_id AND p.id=w.schedule_plan_id
                WHERE w.tenant_id=? AND w.project_id=? AND w.deleted_flag=0
                  AND p.status='ACTIVE' AND p.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM qs_inspection_record q
                    WHERE q.tenant_id=w.tenant_id AND q.project_id=w.project_id AND q.wbs_task_id=w.id
                      AND q.status='SUBMITTED' AND q.conclusion='PASS' AND q.deleted_flag=0)
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_QUALITY_OPEN", "QUALITY", "仍有未关闭质量安全问题", """
                SELECT id biz_id FROM qs_issue WHERE tenant_id=? AND project_id=?
                  AND status<>'CLOSED' AND deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_SUB_TASK_OPEN", "SUBCONTRACT", "仍有未完成分包任务", """
                SELECT id biz_id FROM sub_task WHERE tenant_id=? AND project_id=?
                  AND status NOT IN ('COMPLETED','CANCELLED') AND deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_SUB_MEASURE_OPEN", "SUBCONTRACT", "仍有未定版分包计量", """
                SELECT id biz_id FROM sub_measure WHERE tenant_id=? AND project_id=?
                  AND approval_status IN ('DRAFT','APPROVING','PENDING') AND deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_PURCHASE_OPEN", "PROCUREMENT", "仍有执行中采购订单", """
                SELECT id biz_id FROM mat_purchase_order WHERE tenant_id=? AND project_id=?
                  AND order_status NOT IN ('COMPLETED','CANCELLED','CLOSED') AND deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_RECEIPT_OPEN", "RECEIPT", "仍有未完成收货验收", """
                SELECT id biz_id FROM mat_receipt WHERE tenant_id=? AND project_id=?
                  AND approval_status IN ('DRAFT','APPROVING','PENDING') AND deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_REQUISITION_OPEN", "REQUISITION", "仍有未完成领料出库", """
                SELECT id biz_id FROM mat_requisition WHERE tenant_id=? AND project_id=?
                  AND ((approval_status IN ('DRAFT','APPROVING','PENDING'))
                    OR (approval_status='APPROVED' AND stock_out_flag=0)) AND deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_STOCK_REMAINS", "INVENTORY", "项目仓库仍有库存未处置", """
                SELECT s.id biz_id FROM mat_stock s
                JOIN mat_warehouse w ON w.tenant_id=s.tenant_id AND w.id=s.warehouse_id
                WHERE s.tenant_id=? AND w.project_id=? AND s.available_qty>0
                  AND s.deleted_flag=0 AND w.deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_DRAWING_NOT_APPROVED", "TECHNICAL", "当前施工图版本尚未批准", """
                SELECT d.id biz_id FROM tech_drawing d
                WHERE d.tenant_id=? AND d.project_id=? AND d.status='ACTIVE' AND d.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM tech_drawing_version v
                    WHERE v.tenant_id=d.tenant_id AND v.id=d.current_version_id AND v.drawing_id=d.id
                      AND v.status='APPROVED' AND v.deleted_flag=0)
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_RFI_OPEN", "TECHNICAL", "当前施工图版本仍有未关闭RFI", """
                SELECT r.id biz_id FROM tech_rfi r JOIN tech_drawing d
                  ON d.tenant_id=r.tenant_id AND d.project_id=r.project_id
                 AND d.current_version_id=r.drawing_version_id AND d.status='ACTIVE' AND d.deleted_flag=0
                WHERE r.tenant_id=? AND r.project_id=? AND r.status NOT IN ('CLOSED','CANCELLED') AND r.deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_DISCLOSURE_UNCONFIRMED", "TECHNICAL", "当前批准施工图版本仍有未确认技术交底", """
                SELECT x.id biz_id FROM tech_disclosure x JOIN tech_drawing d
                  ON d.tenant_id=x.tenant_id AND d.project_id=x.project_id
                 AND d.current_version_id=x.drawing_version_id AND d.status='ACTIVE' AND d.deleted_flag=0
                JOIN tech_drawing_version v
                  ON v.tenant_id=d.tenant_id AND v.id=d.current_version_id AND v.status='APPROVED' AND v.deleted_flag=0
                WHERE x.tenant_id=? AND x.project_id=? AND x.status<>'CONFIRMED' AND x.deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_REFERENCE_NOT_ARCHIVED", "TECHNICAL", "施工引用尚未形成已归档验收记录", """
                SELECT r.id biz_id FROM tech_construction_reference r JOIN tech_drawing d
                  ON d.tenant_id=r.tenant_id AND d.project_id=r.project_id
                 AND d.current_version_id=r.drawing_version_id AND d.status='ACTIVE' AND d.deleted_flag=0
                WHERE r.tenant_id=? AND r.project_id=? AND r.status='RECORDED' AND r.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM tech_acceptance_archive a
                    WHERE a.tenant_id=r.tenant_id AND a.construction_reference_id=r.id
                      AND a.status='ARCHIVED' AND a.deleted_flag=0)
                """, tenant(), projectId);
        addRows(blockers, "CONSTRUCTION_WORKFLOW_RUNNING", "WORKFLOW", "仍有运行中审批流程", """
                SELECT id biz_id FROM wf_instance WHERE tenant_id=? AND project_id=? AND instance_status='RUNNING'
                """, tenant(), projectId);
        return List.copyOf(blockers);
    }

    public List<GateBlocker> warrantyEntry(Long closeoutId, Long projectId) {
        List<GateBlocker> blockers = new ArrayList<>();
        addRows(blockers, "WARRANTY_FINAL_ACCEPTANCE_REQUIRED", "ACCEPTANCE", "竣工验收尚未审批通过", """
                SELECT c.id biz_id FROM project_closeout c WHERE c.tenant_id=? AND c.id=?
                  AND NOT EXISTS (SELECT 1 FROM closeout_final_acceptance a
                    WHERE a.tenant_id=c.tenant_id AND a.closeout_id=c.id
                      AND a.status='APPROVED' AND a.deleted_flag=0)
                """, tenant(), closeoutId);
        addRows(blockers, "WARRANTY_FINAL_SETTLEMENT_REQUIRED", "SETTLEMENT", "竣工结算尚未完成", """
                SELECT c.id biz_id FROM project_closeout c LEFT JOIN owner_settlement s
                  ON s.tenant_id=c.tenant_id AND s.id=c.final_owner_settlement_id AND s.deleted_flag=0
                WHERE c.tenant_id=? AND c.id=? AND (s.id IS NULL OR s.status<>'RECEIVABLE_CREATED')
                """, tenant(), closeoutId);
        addRows(blockers, "WARRANTY_TAIL_RECEIVABLE_OPEN", "RECEIVABLE", "竣工尾款尚未全部回收", """
                SELECT r.id biz_id FROM account_receivable r JOIN project_closeout c
                  ON c.tenant_id=r.tenant_id AND c.final_owner_settlement_id=r.settlement_id
                WHERE r.tenant_id=? AND c.id=? AND r.receivable_type='PROGRESS'
                  AND r.outstanding_amount>0 AND r.deleted_flag=0
                """, tenant(), closeoutId);
        addRows(blockers, "WARRANTY_TAIL_CASH_NOT_ARCHIVED", "CASH_JOURNAL", "尾款收款缺少有效已归档现金日记账", """
                SELECT c.id biz_id FROM collection_record c JOIN collection_allocation a
                  ON a.tenant_id=c.tenant_id AND a.collection_id=c.id
                JOIN account_receivable r ON r.tenant_id=a.tenant_id AND r.id=a.receivable_id
                JOIN project_closeout p ON p.tenant_id=r.tenant_id AND p.final_owner_settlement_id=r.settlement_id
                WHERE c.tenant_id=? AND p.id=? AND r.receivable_type='PROGRESS'
                  AND c.status='SUCCESS' AND c.deleted_flag=0 AND r.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM cash_journal_entry j
                    WHERE j.tenant_id=c.tenant_id AND j.collection_record_id=c.id
                      AND j.direction='IN' AND j.status='ARCHIVED' AND j.deleted_flag=0)
                """, tenant(), closeoutId);
        Integer warrantyCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM closeout_warranty WHERE tenant_id=? AND closeout_id=? AND deleted_flag=0
                """, Integer.class, tenant(), closeoutId);
        if (warrantyCount == null || warrantyCount == 0) {
            blockers.add(new GateBlocker("WARRANTY_RESPONSIBILITY_REQUIRED", "WARRANTY", null, "缺少项目质保责任记录"));
        }
        return List.copyOf(blockers);
    }

    public List<GateBlocker> finalClose(Long closeoutId, Long projectId) {
        List<GateBlocker> blockers = new ArrayList<>(constructionCompletion(closeoutId, projectId));
        Integer warrantyCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM closeout_warranty WHERE tenant_id=? AND closeout_id=? AND deleted_flag=0
                """, Integer.class, tenant(), closeoutId);
        if (warrantyCount == null || warrantyCount == 0) {
            blockers.add(new GateBlocker("FINAL_WARRANTY_REQUIRED", "WARRANTY", null, "缺少项目质保责任记录"));
        }
        addRows(blockers, "FINAL_WARRANTY_OPEN", "WARRANTY", "质保责任尚未到期并释放", """
                SELECT id biz_id FROM closeout_warranty WHERE tenant_id=? AND closeout_id=?
                  AND (status<>'RELEASED' OR warranty_end_date>CURRENT_DATE) AND deleted_flag=0
                """, tenant(), closeoutId);
        addRows(blockers, "FINAL_DEFECT_OPEN", "DEFECT", "仍有未关闭质保缺陷", """
                SELECT id biz_id FROM closeout_defect WHERE tenant_id=? AND closeout_id=?
                  AND status<>'CLOSED' AND deleted_flag=0
                """, tenant(), closeoutId);
        addRows(blockers, "FINAL_CONTRACT_OPEN", "CONTRACT", "仍有未结清合同", """
                SELECT id biz_id FROM ct_contract WHERE tenant_id=? AND project_id=?
                  AND contract_status NOT IN ('SETTLED','TERMINATED') AND deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "FINAL_RECEIVABLE_OPEN", "RECEIVABLE", "仍有未清应收", """
                SELECT id biz_id FROM account_receivable WHERE tenant_id=? AND project_id=?
                  AND outstanding_amount>0 AND deleted_flag=0
                """, tenant(), projectId);
        addRows(blockers, "FINAL_CASH_NOT_ARCHIVED", "CASH_JOURNAL", "成功收款缺少有效已归档现金日记账", """
                SELECT c.id biz_id FROM collection_record c
                WHERE c.tenant_id=? AND c.project_id=? AND c.status='SUCCESS' AND c.deleted_flag=0
                  AND NOT EXISTS (SELECT 1 FROM cash_journal_entry j
                    WHERE j.tenant_id=c.tenant_id AND j.collection_record_id=c.id
                      AND j.direction='IN' AND j.status='ARCHIVED' AND j.deleted_flag=0)
                """, tenant(), projectId);
        addRows(blockers, "FINAL_ARCHIVE_NOT_ACCEPTED", "ARCHIVE", "竣工档案尚未签收", """
                SELECT c.id biz_id FROM project_closeout c WHERE c.tenant_id=? AND c.id=?
                  AND NOT EXISTS (SELECT 1 FROM closeout_archive_transfer a
                    WHERE a.tenant_id=c.tenant_id AND a.closeout_id=c.id
                      AND a.status='ACCEPTED' AND a.deleted_flag=0)
                """, tenant(), closeoutId);
        addRows(blockers, "FINAL_WORKFLOW_RUNNING", "WORKFLOW", "仍有运行中审批流程", """
                SELECT id biz_id FROM wf_instance WHERE tenant_id=? AND project_id=? AND instance_status='RUNNING'
                """, tenant(), projectId);
        return List.copyOf(blockers);
    }

    public void requireConstructionCompletion(Long closeoutId, Long projectId) {
        requireEmpty(constructionCompletion(closeoutId, projectId), "PROJECT_CONSTRUCTION_GATE_BLOCKED");
    }

    public void requireFinalClose(Long closeoutId, Long projectId) {
        requireEmpty(finalClose(closeoutId, projectId), "PROJECT_FINAL_CLOSE_GATE_BLOCKED");
    }

    public void requireWarrantyEntry(Long closeoutId, Long projectId) {
        requireEmpty(warrantyEntry(closeoutId, projectId), "PROJECT_WARRANTY_GATE_BLOCKED");
    }

    private void addMissingActiveSchedule(List<GateBlocker> blockers, Long projectId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_schedule_plan WHERE tenant_id=? AND project_id=?
                  AND status='ACTIVE' AND deleted_flag=0
                """, Integer.class, tenant(), projectId);
        if (count == null || count != 1) {
            blockers.add(new GateBlocker("CONSTRUCTION_ACTIVE_WBS_REQUIRED", "WBS", null,
                    "项目必须存在唯一生效WBS基线"));
        }
    }

    private void addRows(List<GateBlocker> blockers, String code, String domain, String reason,
                         String sql, Object... args) {
        jdbc.queryForList(sql, args).forEach(row -> blockers.add(new GateBlocker(
                code, domain, row.get("biz_id") == null ? null : String.valueOf(row.get("biz_id")), reason)));
    }

    private void requireEmpty(List<GateBlocker> blockers, String code) {
        if (!blockers.isEmpty()) {
            throw new BusinessException(code, blockers.getFirst().reason() + "（共" + blockers.size() + "项）");
        }
    }

    private Long tenant() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("TENANT_CONTEXT_REQUIRED", "缺少租户上下文");
        return tenantId;
    }

    public record GateBlocker(String gateCode, String domain, String bizId, String reason) {
    }
}
