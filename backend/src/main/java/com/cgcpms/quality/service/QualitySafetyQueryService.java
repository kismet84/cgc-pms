package com.cgcpms.quality.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.quality.dto.QualitySafetyModels.PlanRef;
import com.cgcpms.quality.dto.QualitySafetyModels.Workspace;
import com.cgcpms.quality.dto.QualitySafetyModels.WorkspaceCounts;
import com.cgcpms.quality.entity.QualityInspectionPlan;
import com.cgcpms.quality.entity.QualityInspectionRecord;
import com.cgcpms.quality.entity.QualitySafetyIssue;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class QualitySafetyQueryService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(readOnly = true)
    public Workspace workspace(String view, int pageNo, int pageSize, Long projectId, Long planId) {
        String activeView = normalizeView(view);
        ProjectAccessChecker.ProjectSqlScope scope = projectAccessChecker.sqlScope();
        String projectPredicate = scope.predicate();
        List<Object> scopeParameters = new ArrayList<>(scope.parameters());
        if (projectId != null) {
            projectPredicate += " AND p.id = ?";
            scopeParameters.add(projectId);
        }

        Metadata metadata = metadata(projectPredicate, scopeParameters, planId);
        long total = total(metadata.counts(), activeView);
        List<?> records = total == 0
                ? List.of()
                : page(activeView, projectPredicate, scopeParameters, metadata.selectedPlanRef(), pageNo, pageSize);
        return new Workspace(activeView,
                new PageResult<>(pageNo, pageSize, total, records),
                metadata.counts(), metadata.selectedPlanRef());
    }

    private Metadata metadata(String projectPredicate, List<Object> scopeParameters, Long planId) {
        String selectedPlanPredicate = planId == null ? "" : "WHERE vp.id = ?";
        List<Object> parameters = new ArrayList<>(scopeParameters);
        if (planId != null) parameters.add(planId);
        return jdbc.query("""
                WITH visible_project AS (
                    SELECT p.id, p.tenant_id
                    FROM pm_project p
                    WHERE %s
                ),
                visible_plan AS (
                    SELECT qp.*
                    FROM qs_inspection_plan qp
                    JOIN visible_project p ON p.id = qp.project_id AND p.tenant_id = qp.tenant_id
                    WHERE qp.deleted_flag = 0
                ),
                selected_plan AS (
                    SELECT vp.id, vp.tenant_id, vp.project_id, vp.plan_code, vp.plan_name, vp.status
                    FROM visible_plan vp
                    %s
                    ORDER BY vp.start_date DESC, vp.created_at DESC, vp.id DESC
                    LIMIT 1
                ),
                issue_counts AS (
                    SELECT
                        COALESCE(SUM(CASE WHEN qi.status <> 'PENDING_REINSPECTION'
                            AND NOT (qi.status = 'CLOSED' AND qi.responsible_partner_id IS NOT NULL)
                            THEN 1 ELSE 0 END), 0) rectification_count,
                        COALESCE(SUM(CASE WHEN qi.status = 'PENDING_REINSPECTION'
                            THEN 1 ELSE 0 END), 0) reinspection_count,
                        COALESCE(SUM(CASE WHEN qi.status = 'CLOSED'
                            AND qi.responsible_partner_id IS NOT NULL
                            THEN 1 ELSE 0 END), 0) consequence_count
                    FROM qs_issue qi
                    JOIN visible_project p ON p.id = qi.project_id AND p.tenant_id = qi.tenant_id
                    WHERE qi.deleted_flag = 0
                )
                SELECT
                    (SELECT COUNT(*) FROM visible_plan) plan_count,
                    (SELECT COUNT(*)
                     FROM qs_inspection_record qr
                     JOIN selected_plan sp ON sp.id = qr.plan_id
                        AND sp.project_id = qr.project_id AND sp.tenant_id = qr.tenant_id
                     WHERE qr.deleted_flag = 0) inspection_count,
                    ic.rectification_count, ic.reinspection_count, ic.consequence_count,
                    sp.id selected_plan_id, sp.project_id selected_project_id,
                    sp.plan_code selected_plan_code, sp.plan_name selected_plan_name,
                    sp.status selected_plan_status
                FROM issue_counts ic
                LEFT JOIN selected_plan sp ON 1 = 1
                """.formatted(projectPredicate, selectedPlanPredicate), (rs, rowNum) -> {
            String selectedPlanId = rs.getString("selected_plan_id");
            PlanRef selectedPlan = selectedPlanId == null ? null : new PlanRef(
                    selectedPlanId,
                    rs.getString("selected_project_id"),
                    rs.getString("selected_plan_code"),
                    rs.getString("selected_plan_name"),
                    rs.getString("selected_plan_status"));
            return new Metadata(new WorkspaceCounts(
                    rs.getLong("plan_count"),
                    rs.getLong("inspection_count"),
                    rs.getLong("rectification_count"),
                    rs.getLong("reinspection_count"),
                    rs.getLong("consequence_count")), selectedPlan);
        }, parameters.toArray()).getFirst();
    }

    private List<?> page(String view, String projectPredicate, List<Object> scopeParameters,
                         PlanRef selectedPlan, int pageNo, int pageSize) {
        List<Object> parameters = new ArrayList<>(scopeParameters);
        String sql;
        if ("plan".equals(view)) {
            sql = """
                    SELECT qp.*
                    FROM qs_inspection_plan qp
                    JOIN pm_project p ON p.id = qp.project_id AND p.tenant_id = qp.tenant_id
                    WHERE qp.deleted_flag = 0 AND %s
                    ORDER BY qp.start_date DESC, qp.created_at DESC, qp.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            addPage(parameters, pageNo, pageSize);
            return jdbc.query(sql, BeanPropertyRowMapper.newInstance(QualityInspectionPlan.class),
                    parameters.toArray());
        }
        if ("inspection".equals(view)) {
            if (selectedPlan == null) return List.of();
            sql = """
                    SELECT qr.*
                    FROM qs_inspection_record qr
                    JOIN pm_project p ON p.id = qr.project_id AND p.tenant_id = qr.tenant_id
                    WHERE qr.deleted_flag = 0 AND %s AND qr.plan_id = ?
                    ORDER BY qr.inspection_date DESC, qr.created_at DESC, qr.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            parameters.add(Long.valueOf(selectedPlan.id()));
            addPage(parameters, pageNo, pageSize);
            return jdbc.query(sql, BeanPropertyRowMapper.newInstance(QualityInspectionRecord.class),
                    parameters.toArray());
        }

        String issuePredicate = switch (view) {
            case "rectification" -> "qi.status <> 'PENDING_REINSPECTION' AND NOT (qi.status = 'CLOSED' AND qi.responsible_partner_id IS NOT NULL)";
            case "reinspection" -> "qi.status = 'PENDING_REINSPECTION'";
            case "consequence" -> "qi.status = 'CLOSED' AND qi.responsible_partner_id IS NOT NULL";
            default -> throw invalidView();
        };
        sql = """
                SELECT qi.*
                FROM qs_issue qi
                JOIN pm_project p ON p.id = qi.project_id AND p.tenant_id = qi.tenant_id
                WHERE qi.deleted_flag = 0 AND %s AND %s
                ORDER BY qi.due_date ASC, qi.created_at DESC, qi.id DESC
                LIMIT ? OFFSET ?
                """.formatted(projectPredicate, issuePredicate);
        addPage(parameters, pageNo, pageSize);
        return jdbc.query(sql, BeanPropertyRowMapper.newInstance(QualitySafetyIssue.class),
                parameters.toArray());
    }

    private void addPage(List<Object> parameters, int pageNo, int pageSize) {
        parameters.add(pageSize);
        parameters.add((long) (pageNo - 1) * pageSize);
    }

    private String normalizeView(String view) {
        String normalized = view == null ? "" : view.trim().toLowerCase(Locale.ROOT);
        if (List.of("plan", "inspection", "rectification", "reinspection", "consequence")
                .contains(normalized)) return normalized;
        throw invalidView();
    }

    private long total(WorkspaceCounts counts, String view) {
        return switch (view) {
            case "plan" -> counts.plan();
            case "inspection" -> counts.inspection();
            case "rectification" -> counts.rectification();
            case "reinspection" -> counts.reinspection();
            case "consequence" -> counts.consequence();
            default -> throw invalidView();
        };
    }

    private BusinessException invalidView() {
        return new BusinessException("QS_WORKSPACE_VIEW_INVALID", "质量安全工作台视图不合法");
    }

    private record Metadata(WorkspaceCounts counts, PlanRef selectedPlanRef) {}
}
