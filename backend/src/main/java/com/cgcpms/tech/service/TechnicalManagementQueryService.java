package com.cgcpms.tech.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.tech.dto.TechnicalManagementModels.Workspace;
import com.cgcpms.tech.dto.TechnicalManagementModels.WorkspaceCounts;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TechnicalManagementQueryService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(readOnly = true)
    public Workspace workspace(String view, int pageNo, int pageSize, int secondaryPageNo, Long projectId) {
        String activeView = normalizeView(view);
        validatePage(pageNo, pageSize, secondaryPageNo);

        ProjectAccessChecker.ProjectSqlScope scope = projectAccessChecker.sqlScope();
        String projectPredicate = scope.predicate();
        List<Object> parameters = new ArrayList<>(scope.parameters());
        if (projectId != null) {
            projectPredicate += " AND p.id = ?";
            parameters.add(projectId);
        }

        Totals totals = totals(projectPredicate, parameters);
        PageResult<Map<String, Object>> primary = page(
                primaryKind(activeView), primaryTotal(activeView, totals),
                projectPredicate, parameters, pageNo, pageSize);
        PageResult<Map<String, Object>> secondary = secondaryKind(activeView) == null
                ? null
                : page(secondaryKind(activeView), secondaryTotal(activeView, totals),
                        projectPredicate, parameters, secondaryPageNo, pageSize);
        return new Workspace(activeView, totals.counts(), primary, secondary);
    }

    private Totals totals(String projectPredicate, List<Object> parameters) {
        return jdbc.queryForObject("""
                WITH visible_project AS (
                    SELECT p.id, p.tenant_id
                    FROM pm_project p
                    WHERE %s
                )
                SELECT
                    (SELECT COUNT(*) FROM technical_scheme s
                     JOIN visible_project vp ON vp.id = s.project_id AND vp.tenant_id = s.tenant_id
                     WHERE s.deleted_flag = 0) scheme_count,
                    (SELECT COUNT(*) FROM tech_drawing d
                     JOIN visible_project vp ON vp.id = d.project_id AND vp.tenant_id = d.tenant_id
                     WHERE d.deleted_flag = 0) drawing_count,
                    (SELECT COUNT(*) FROM tech_drawing_version v
                     JOIN visible_project vp ON vp.id = v.project_id AND vp.tenant_id = v.tenant_id
                     WHERE v.deleted_flag = 0) version_count,
                    (SELECT COUNT(*) FROM tech_drawing_review r
                     JOIN visible_project vp ON vp.id = r.project_id AND vp.tenant_id = r.tenant_id
                     WHERE r.deleted_flag = 0) review_count,
                    (SELECT COUNT(*) FROM tech_rfi r
                     JOIN visible_project vp ON vp.id = r.project_id AND vp.tenant_id = r.tenant_id
                     WHERE r.deleted_flag = 0) rfi_total,
                    (SELECT COUNT(*) FROM tech_rfi r
                     JOIN visible_project vp ON vp.id = r.project_id AND vp.tenant_id = r.tenant_id
                     WHERE r.deleted_flag = 0 AND r.status NOT IN ('CLOSED', 'CANCELLED')) rfi_count,
                    (SELECT COUNT(*) FROM tech_rfi_response rr
                     JOIN tech_rfi r ON r.id = rr.rfi_id AND r.tenant_id = rr.tenant_id
                     JOIN visible_project vp ON vp.id = r.project_id AND vp.tenant_id = r.tenant_id)
                        response_count,
                    (SELECT COUNT(*) FROM tech_disclosure d
                     JOIN visible_project vp ON vp.id = d.project_id AND vp.tenant_id = d.tenant_id
                     WHERE d.deleted_flag = 0) disclosure_count,
                    (SELECT COUNT(*) FROM tech_construction_reference cr
                     JOIN visible_project vp ON vp.id = cr.project_id AND vp.tenant_id = cr.tenant_id
                     WHERE cr.deleted_flag = 0 AND cr.status = 'RECORDED'
                       AND NOT EXISTS (
                           SELECT 1 FROM tech_acceptance_archive a
                           WHERE a.tenant_id = cr.tenant_id
                             AND a.construction_reference_id = cr.id
                             AND a.deleted_flag = 0
                       )) reference_count,
                    (SELECT COUNT(*) FROM tech_acceptance_archive a
                     JOIN visible_project vp ON vp.id = a.project_id AND vp.tenant_id = a.tenant_id
                     WHERE a.deleted_flag = 0) archive_count
                """.formatted(projectPredicate), (rs, rowNum) -> new Totals(
                rs.getLong("scheme_count"),
                rs.getLong("drawing_count"),
                rs.getLong("version_count"),
                rs.getLong("review_count"),
                rs.getLong("rfi_total"),
                rs.getLong("rfi_count"),
                rs.getLong("response_count"),
                rs.getLong("disclosure_count"),
                rs.getLong("reference_count"),
                rs.getLong("archive_count")), parameters.toArray());
    }

    private PageResult<Map<String, Object>> page(String kind, long total, String projectPredicate,
                                                  List<Object> scopeParameters, int pageNo, int pageSize) {
        String sql = switch (kind) {
            case "scheme" -> """
                    SELECT TRIM(CAST(s.id AS CHAR(32))) id,
                           TRIM(CAST(s.project_id AS CHAR(32))) projectId,
                           s.scheme_code schemeCode,
                           s.scheme_name schemeName, s.scheme_type schemeType,
                           TRIM(CAST(s.responsible_user_id AS CHAR(32))) responsibleUserId,
                           s.planned_effective_date plannedEffectiveDate, s.status,
                           TRIM(CAST(s.approval_instance_id AS CHAR(32))) approvalInstanceId,
                           s.approved_at approvedAt, s.remark
                    FROM technical_scheme s
                    JOIN pm_project p ON p.id = s.project_id AND p.tenant_id = s.tenant_id
                    WHERE s.deleted_flag = 0 AND %s
                    ORDER BY s.created_at DESC, s.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            case "drawing" -> """
                    SELECT TRIM(CAST(d.id AS CHAR(32))) id,
                           TRIM(CAST(d.project_id AS CHAR(32))) projectId,
                           d.drawing_code drawingCode,
                           d.drawing_name drawingName, d.specialty,
                           d.source_organization sourceOrganization,
                           TRIM(CAST(d.current_version_id AS CHAR(32))) currentVersionId,
                           d.status, v.version_no currentVersionNo, v.status currentVersionStatus, d.remark
                    FROM tech_drawing d
                    JOIN pm_project p ON p.id = d.project_id AND p.tenant_id = d.tenant_id
                    LEFT JOIN tech_drawing_version v ON v.id = d.current_version_id
                    WHERE d.deleted_flag = 0 AND %s
                    ORDER BY d.created_at DESC, d.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            case "version" -> """
                    SELECT TRIM(CAST(v.id AS CHAR(32))) id,
                           TRIM(CAST(v.drawing_id AS CHAR(32))) drawingId,
                           d.drawing_code drawingCode, v.version_no versionNo,
                           TRIM(CAST(v.previous_version_id AS CHAR(32))) previousVersionId,
                           TRIM(CAST(v.source_rfi_id AS CHAR(32))) sourceRfiId, v.received_at receivedAt,
                           v.change_summary changeSummary, v.status
                    FROM tech_drawing_version v
                    JOIN tech_drawing d ON d.id = v.drawing_id
                    JOIN pm_project p ON p.id = v.project_id AND p.tenant_id = v.tenant_id
                    WHERE v.deleted_flag = 0 AND %s
                    ORDER BY v.received_at DESC, v.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            case "review" -> """
                    SELECT TRIM(CAST(r.id AS CHAR(32))) id,
                           TRIM(CAST(r.drawing_version_id AS CHAR(32))) drawingVersionId,
                           r.review_code reviewCode, r.review_date reviewDate,
                           TRIM(CAST(r.chair_user_id AS CHAR(32))) chairUserId,
                           r.participant_summary participantSummary, r.conclusion,
                           r.review_summary reviewSummary, r.requires_rfi requiresRfi, r.status
                    FROM tech_drawing_review r
                    JOIN pm_project p ON p.id = r.project_id AND p.tenant_id = r.tenant_id
                    WHERE r.deleted_flag = 0 AND %s
                    ORDER BY r.review_date DESC, r.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            case "rfi" -> """
                    SELECT TRIM(CAST(r.id AS CHAR(32))) id,
                           TRIM(CAST(r.drawing_version_id AS CHAR(32))) drawingVersionId,
                           TRIM(CAST(r.review_id AS CHAR(32))) reviewId,
                           r.rfi_code rfiCode, r.subject, r.priority,
                           r.response_due_date responseDueDate, r.status,
                           r.raised_at raisedAt, r.closed_at closedAt
                    FROM tech_rfi r
                    JOIN pm_project p ON p.id = r.project_id AND p.tenant_id = r.tenant_id
                    WHERE r.deleted_flag = 0 AND %s
                    ORDER BY CASE r.status
                                 WHEN 'SUBMITTED' THEN 1
                                 WHEN 'RESPONDED' THEN 2
                                 WHEN 'CHANGE_PENDING' THEN 3
                                 ELSE 4
                             END,
                             r.response_due_date, r.created_at DESC, r.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            case "response" -> """
                    SELECT TRIM(CAST(rr.id AS CHAR(32))) id,
                           TRIM(CAST(rr.rfi_id AS CHAR(32))) rfiId,
                           rr.response_content responseContent,
                           rr.change_required changeRequired, rr.responder_name responderName,
                           TRIM(CAST(rr.responded_by AS CHAR(32))) respondedBy, rr.responded_at respondedAt,
                           rr.status reviewStatus, TRIM(CAST(rr.reviewed_by AS CHAR(32))) reviewedBy,
                           rr.reviewed_at reviewedAt, rr.review_comment reviewComment
                    FROM tech_rfi_response rr
                    JOIN tech_rfi r ON r.id = rr.rfi_id AND r.tenant_id = rr.tenant_id
                    JOIN pm_project p ON p.id = r.project_id AND p.tenant_id = r.tenant_id
                    WHERE %s
                    ORDER BY rr.responded_at DESC, rr.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            case "disclosure" -> """
                    SELECT TRIM(CAST(d.id AS CHAR(32))) id,
                           TRIM(CAST(d.drawing_version_id AS CHAR(32))) drawingVersionId,
                           TRIM(CAST(d.scheme_id AS CHAR(32))) schemeId,
                           d.disclosure_code disclosureCode, d.disclosure_title disclosureTitle,
                           d.disclosure_date disclosureDate,
                           TRIM(CAST(d.presenter_user_id AS CHAR(32))) presenterUserId,
                           d.recipient_summary recipientSummary,
                           d.disclosure_content disclosureContent, d.status
                    FROM tech_disclosure d
                    JOIN pm_project p ON p.id = d.project_id AND p.tenant_id = d.tenant_id
                    WHERE d.deleted_flag = 0 AND %s
                    ORDER BY d.disclosure_date DESC, d.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            case "reference" -> """
                    SELECT TRIM(CAST(cr.id AS CHAR(32))) id,
                           TRIM(CAST(cr.drawing_version_id AS CHAR(32))) drawingVersionId,
                           TRIM(CAST(cr.disclosure_id AS CHAR(32))) disclosureId,
                           TRIM(CAST(cr.daily_log_id AS CHAR(32))) dailyLogId,
                           TRIM(CAST(cr.wbs_task_id AS CHAR(32))) wbsTaskId, cr.reference_date referenceDate,
                           cr.work_area workArea, cr.reference_description referenceDescription, cr.status
                    FROM tech_construction_reference cr
                    JOIN pm_project p ON p.id = cr.project_id AND p.tenant_id = cr.tenant_id
                    WHERE cr.deleted_flag = 0 AND cr.status = 'RECORDED' AND %s
                      AND NOT EXISTS (
                          SELECT 1 FROM tech_acceptance_archive a
                          WHERE a.tenant_id = cr.tenant_id
                            AND a.construction_reference_id = cr.id
                            AND a.deleted_flag = 0
                      )
                    ORDER BY cr.reference_date DESC, cr.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            case "archive" -> """
                    SELECT TRIM(CAST(a.id AS CHAR(32))) id,
                           TRIM(CAST(a.drawing_version_id AS CHAR(32))) drawingVersionId,
                           TRIM(CAST(a.construction_reference_id AS CHAR(32))) constructionReferenceId,
                           TRIM(CAST(a.quality_inspection_id AS CHAR(32))) qualityInspectionId,
                           a.archive_code archiveCode,
                           a.acceptance_date acceptanceDate, a.acceptance_conclusion acceptanceConclusion,
                           a.archive_location archiveLocation, a.status, a.archived_at archivedAt
                    FROM tech_acceptance_archive a
                    JOIN pm_project p ON p.id = a.project_id AND p.tenant_id = a.tenant_id
                    WHERE a.deleted_flag = 0 AND %s
                    ORDER BY a.acceptance_date DESC, a.id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(projectPredicate);
            default -> throw invalidView();
        };
        List<Object> parameters = new ArrayList<>(scopeParameters);
        parameters.add(pageSize);
        parameters.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql, parameters.toArray());
        return new PageResult<>(pageNo, pageSize, total, records);
    }

    private String normalizeView(String view) {
        String normalized = view == null ? "" : view.trim().toLowerCase(Locale.ROOT);
        if (List.of("scheme", "drawing", "review", "rfi", "disclosure", "archive")
                .contains(normalized)) return normalized;
        throw invalidView();
    }

    private void validatePage(int pageNo, int pageSize, int secondaryPageNo) {
        if (pageNo < 1 || secondaryPageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException("TECH_WORKSPACE_PAGE_INVALID", "技术管理工作台分页参数不合法");
        }
    }

    private String primaryKind(String view) {
        return switch (view) {
            case "scheme", "drawing", "review", "rfi", "disclosure" -> view;
            case "archive" -> "reference";
            default -> throw invalidView();
        };
    }

    private String secondaryKind(String view) {
        return switch (view) {
            case "drawing" -> "version";
            case "rfi" -> "response";
            case "archive" -> "archive";
            case "scheme", "review", "disclosure" -> null;
            default -> throw invalidView();
        };
    }

    private long primaryTotal(String view, Totals totals) {
        return switch (view) {
            case "scheme" -> totals.scheme;
            case "drawing" -> totals.drawing;
            case "review" -> totals.review;
            case "rfi" -> totals.rfiTotal;
            case "disclosure" -> totals.disclosure;
            case "archive" -> totals.reference;
            default -> throw invalidView();
        };
    }

    private long secondaryTotal(String view, Totals totals) {
        return switch (view) {
            case "drawing" -> totals.version;
            case "rfi" -> totals.response;
            case "archive" -> totals.archive;
            default -> 0;
        };
    }

    private BusinessException invalidView() {
        return new BusinessException("TECH_WORKSPACE_VIEW_INVALID", "技术管理工作台视图不合法");
    }

    private record Totals(
            long scheme,
            long drawing,
            long version,
            long review,
            long rfiTotal,
            long rfi,
            long response,
            long disclosure,
            long reference,
            long archive) {
        private WorkspaceCounts counts() {
            return new WorkspaceCounts(scheme, drawing, review, rfi, disclosure, archive);
        }
    }
}
