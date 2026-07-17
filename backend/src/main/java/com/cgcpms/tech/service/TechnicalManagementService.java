package com.cgcpms.tech.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.tech.dto.TechnicalManagementModels.*;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TechnicalManagementService {
    private final JdbcTemplate jdbc;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;

    public Map<String, Object> overview(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "查看技术管理工作台");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemes", jdbc.queryForList("""
                SELECT id,project_id projectId,scheme_code schemeCode,scheme_name schemeName,scheme_type schemeType,
                 responsible_user_id responsibleUserId,planned_effective_date plannedEffectiveDate,status,
                 approval_instance_id approvalInstanceId,approved_at approvedAt,remark
                FROM technical_scheme WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY created_at DESC
                """, tenant(), projectId));
        result.put("drawings", jdbc.queryForList("""
                SELECT d.id,d.project_id projectId,d.drawing_code drawingCode,d.drawing_name drawingName,d.specialty,
                 d.source_organization sourceOrganization,d.current_version_id currentVersionId,d.status,
                 v.version_no currentVersionNo,v.status currentVersionStatus,d.remark
                FROM tech_drawing d LEFT JOIN tech_drawing_version v ON v.id=d.current_version_id
                WHERE d.tenant_id=? AND d.project_id=? AND d.deleted_flag=0 ORDER BY d.created_at DESC
                """, tenant(), projectId));
        result.put("versions", jdbc.queryForList("""
                SELECT v.id,v.drawing_id drawingId,d.drawing_code drawingCode,v.version_no versionNo,
                 v.previous_version_id previousVersionId,v.source_rfi_id sourceRfiId,v.received_at receivedAt,
                 v.change_summary changeSummary,v.status
                FROM tech_drawing_version v JOIN tech_drawing d ON d.id=v.drawing_id
                WHERE v.tenant_id=? AND v.project_id=? AND v.deleted_flag=0 ORDER BY v.received_at DESC,v.id DESC
                """, tenant(), projectId));
        result.put("reviews", jdbc.queryForList("""
                SELECT id,drawing_version_id drawingVersionId,review_code reviewCode,review_date reviewDate,
                 chair_user_id chairUserId,participant_summary participantSummary,conclusion,review_summary reviewSummary,
                 requires_rfi requiresRfi,status
                FROM tech_drawing_review WHERE tenant_id=? AND project_id=? AND deleted_flag=0
                ORDER BY review_date DESC,id DESC
                """, tenant(), projectId));
        result.put("rfis", jdbc.queryForList("""
                SELECT r.id,r.drawing_version_id drawingVersionId,r.review_id reviewId,r.rfi_code rfiCode,
                 r.subject,r.priority,r.response_due_date responseDueDate,r.status,r.raised_at raisedAt,r.closed_at closedAt
                FROM tech_rfi r WHERE r.tenant_id=? AND r.project_id=? AND r.deleted_flag=0
                ORDER BY CASE r.status WHEN 'SUBMITTED' THEN 1 WHEN 'RESPONDED' THEN 2 WHEN 'CHANGE_PENDING' THEN 3 ELSE 4 END,
                 r.response_due_date,r.created_at DESC
                """, tenant(), projectId));
        result.put("responses", jdbc.queryForList("""
                SELECT p.id,p.rfi_id rfiId,p.response_content responseContent,p.change_required changeRequired,
                 p.responder_name responderName,p.responded_by respondedBy,p.responded_at respondedAt,
                 p.status reviewStatus,p.reviewed_by reviewedBy,p.reviewed_at reviewedAt,p.review_comment reviewComment
                FROM tech_rfi_response p JOIN tech_rfi r ON r.id=p.rfi_id
                WHERE p.tenant_id=? AND r.project_id=? ORDER BY p.responded_at DESC,p.id DESC
                """, tenant(), projectId));
        result.put("disclosures", jdbc.queryForList("""
                SELECT id,drawing_version_id drawingVersionId,scheme_id schemeId,disclosure_code disclosureCode,
                 disclosure_title disclosureTitle,disclosure_date disclosureDate,presenter_user_id presenterUserId,status
                FROM tech_disclosure WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY disclosure_date DESC,id DESC
                """, tenant(), projectId));
        result.put("constructionReferences", jdbc.queryForList("""
                SELECT id,drawing_version_id drawingVersionId,disclosure_id disclosureId,daily_log_id dailyLogId,
                 wbs_task_id wbsTaskId,reference_date referenceDate,work_area workArea,status
                FROM tech_construction_reference WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY reference_date DESC,id DESC
                """, tenant(), projectId));
        result.put("archives", jdbc.queryForList("""
                SELECT id,drawing_version_id drawingVersionId,construction_reference_id constructionReferenceId,
                 quality_inspection_id qualityInspectionId,archive_code archiveCode,acceptance_date acceptanceDate,
                 acceptance_conclusion acceptanceConclusion,archive_location archiveLocation,status,archived_at archivedAt
                FROM tech_acceptance_archive WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY acceptance_date DESC,id DESC
                """, tenant(), projectId));
        result.put("constructionFacts", jdbc.queryForList("""
                SELECT p.id progressId,l.id dailyLogId,l.report_date reportDate,t.id wbsTaskId,t.task_code taskCode,
                 t.task_name taskName,t.work_area workArea,p.current_progress currentProgress,p.completed_quantity completedQuantity
                FROM site_daily_progress p JOIN site_daily_log l ON l.id=p.daily_log_id
                JOIN project_wbs_task t ON t.id=p.wbs_task_id
                WHERE p.tenant_id=? AND p.project_id=? AND l.status='SUBMITTED' AND l.deleted_flag=0 AND t.deleted_flag=0
                ORDER BY l.report_date DESC,p.id DESC
                """, tenant(), projectId));
        result.put("qualityInspections", jdbc.queryForList("""
                SELECT id,inspection_code inspectionCode,inspection_date inspectionDate,location,conclusion,status
                FROM qs_inspection_record WHERE tenant_id=? AND project_id=? AND status='SUBMITTED'
                 AND conclusion='PASS' AND deleted_flag=0 ORDER BY inspection_date DESC,id DESC
                """, tenant(), projectId));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createScheme(SchemeCommand command) {
        projectAccessChecker.checkAccess(command.projectId(), "创建技术方案");
        requireActiveProject(command.projectId());
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO technical_scheme(id,tenant_id,project_id,scheme_code,scheme_name,scheme_type,
                     responsible_user_id,planned_effective_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), command.projectId(), command.schemeCode().trim(), command.schemeName().trim(),
                    command.schemeType(), command.responsibleUserId(), command.plannedEffectiveDate(), user(), user(), command.remark());
            insertTechItem("TECHNICAL_SCHEME", id, command.projectId(), "TECH_PLAN", command.schemeCode(),
                    command.schemeName(), "HIGH", "OPEN", command.responsibleUserId(), command.plannedEffectiveDate());
        } catch (DuplicateKeyException e) {
            throw error("TECH_SCHEME_DUPLICATE", "同一项目的技术方案编码不能重复");
        }
        return scheme(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitScheme(Long id) {
        Map<String, Object> row = requireScheme(id, true);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "提交技术方案审批");
        if (!Set.of("DRAFT", "REJECTED").contains(string(row.get("status"))))
            throw error("TECH_SCHEME_SUBMIT_STATE_INVALID", "只有草稿或驳回的技术方案可以提交");
        requireFile("TECH_SCHEME", id, "SCHEME_FILE", "提交技术方案前必须上传方案正文");
        Long existing = longValueNullable(row.get("approval_instance_id"));
        WfInstance instance = "REJECTED".equals(string(row.get("status"))) && existing != null
                ? workflowEngine.resubmit(existing, user(), UserContext.getCurrentUsername())
                : workflowEngine.submit(user(), UserContext.getCurrentUsername(), tenant(), WorkflowBusinessTypes.TECHNICAL_SCHEME,
                id, string(row.get("scheme_code")), BigDecimal.ZERO, longValue(row.get("project_id")), null,
                "技术方案审批", null, null);
        int changed = jdbc.update("""
                UPDATE technical_scheme SET status='PENDING',approval_instance_id=?,version=version+1,
                 updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status IN('DRAFT','REJECTED')
                """, instance.getId(), user(), id, tenant());
        if (changed != 1) throw concurrent();
        updateTechItem("TECHNICAL_SCHEME", id, "PENDING", null);
        return scheme(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onSchemeApproved(Long id) {
        Map<String, Object> row = requireScheme(id, true);
        if ("APPROVED".equals(string(row.get("status")))) return;
        if (!"PENDING".equals(string(row.get("status"))))
            throw error("TECH_SCHEME_APPROVAL_STATE_INVALID", "技术方案审批状态不正确");
        jdbc.update("""
                UPDATE technical_scheme SET status='SUPERSEDED',version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND project_id=? AND scheme_type=? AND status='APPROVED' AND id<>?
                """, tenant(), row.get("project_id"), row.get("scheme_type"), id);
        if (jdbc.update("""
                UPDATE technical_scheme SET status='APPROVED',approved_at=CURRENT_TIMESTAMP,version=version+1,
                 updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'
                """, id, tenant()) != 1) throw concurrent();
        updateTechItem("TECHNICAL_SCHEME", id, "CLOSED", LocalDateTime.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public void onSchemeRejected(Long id) {
        jdbc.update("""
                UPDATE technical_scheme SET status='REJECTED',version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND status='PENDING'
                """, id, tenant());
        updateTechItem("TECHNICAL_SCHEME", id, "OPEN", null);
    }

    public Map<String, Object> scheme(Long id) {
        Map<String, Object> row = new LinkedHashMap<>(requireScheme(id, false));
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "查看技术方案");
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receiveDrawing(DrawingReceiptCommand command) {
        projectAccessChecker.checkAccess(command.projectId(), "接收图纸");
        requireActiveProject(command.projectId());
        Long drawingId = IdWorker.getId();
        Long versionId = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO tech_drawing(id,tenant_id,project_id,drawing_code,drawing_name,specialty,source_organization,
                     current_version_id,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,'ACTIVE',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, drawingId, tenant(), command.projectId(), command.drawingCode().trim(), command.drawingName().trim(),
                    command.specialty().trim(), command.sourceOrganization().trim(), versionId, user(), user(), command.remark());
            insertDrawingVersion(versionId, command.projectId(), drawingId, command.versionNo(), null, null,
                    command.receivedAt(), command.changeSummary(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("TECH_DRAWING_DUPLICATE", "同一项目的图纸编码或版本不能重复");
        }
        return trace(drawingId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receiveVersion(Long drawingId, DrawingVersionCommand command) {
        Map<String, Object> drawing = requireDrawing(drawingId);
        Long projectId = longValue(drawing.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "接收图纸变更版本");
        requireActiveProject(projectId);
        Map<String, Object> previous = requireVersion(command.previousVersionId(), false);
        Map<String, Object> rfi = requireRfi(command.sourceRfiId(), true);
        if (!drawingId.equals(longValue(previous.get("drawing_id")))
                || !command.previousVersionId().equals(longValue(rfi.get("drawing_version_id"))))
            throw error("TECH_DRAWING_VERSION_SOURCE_MISMATCH", "新版本、上一版本和RFI必须属于同一图纸链");
        if (!"CHANGE_PENDING".equals(string(rfi.get("status"))))
            throw error("TECH_RFI_CHANGE_NOT_REQUIRED", "只有已确认需要改图的RFI才能生成新图纸版本");
        Integer accepted = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tech_rfi_response WHERE tenant_id=? AND rfi_id=? AND status='ACCEPTED' AND change_required=1
                """, Integer.class, tenant(), command.sourceRfiId());
        if (accepted == null || accepted != 1)
            throw error("TECH_RFI_ACCEPTED_CHANGE_RESPONSE_REQUIRED", "RFI必须存在已接受且要求改图的设计回复");
        Long versionId = IdWorker.getId();
        try {
            insertDrawingVersion(versionId, projectId, drawingId, command.versionNo(), command.previousVersionId(),
                    command.sourceRfiId(), command.receivedAt(), command.changeSummary(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("TECH_DRAWING_VERSION_DUPLICATE", "同一图纸版本号不能重复");
        }
        return version(versionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createReview(Long versionId, ReviewCommand command) {
        Map<String, Object> version = requireVersion(versionId, true);
        Long projectId = longValue(version.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "登记图纸会审");
        if (!"RECEIVED".equals(string(version.get("status"))))
            throw error("TECH_DRAWING_REVIEW_STATE_INVALID", "只有已接收图纸版本可以发起会审");
        requireFile("TECH_DRAWING_VERSION", versionId, "DRAWING_FILE", "会审前必须上传本版完整图纸");
        if (!command.requiresRfi() && !"PASS".equals(command.conclusion()))
            throw error("TECH_DRAWING_REVIEW_RFI_REQUIRED", "条件通过或退回的会审必须标记需要RFI");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO tech_drawing_review(id,tenant_id,project_id,drawing_version_id,review_code,review_date,
                     chair_user_id,participant_summary,conclusion,review_summary,requires_rfi,status,version,
                     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), projectId, versionId, command.reviewCode().trim(), command.reviewDate(),
                    command.chairUserId(), command.participantSummary().trim(), command.conclusion(),
                    command.reviewSummary().trim(), command.requiresRfi() ? 1 : 0, user(), user(), command.remark());
            if (jdbc.update("""
                    UPDATE tech_drawing_version SET status='UNDER_REVIEW',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND tenant_id=? AND status='RECEIVED'
                    """, user(), versionId, tenant()) != 1) throw concurrent();
        } catch (DuplicateKeyException e) {
            throw error("TECH_DRAWING_REVIEW_DUPLICATE", "图纸版本只能建立一份会审记录且会审编号不能重复");
        }
        return review(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmReview(Long reviewId) {
        Map<String, Object> review = requireReview(reviewId, true);
        projectAccessChecker.checkAccess(longValue(review.get("project_id")), "确认图纸会审");
        if (!"DRAFT".equals(string(review.get("status"))))
            throw error("TECH_DRAWING_REVIEW_CONFIRMED", "会审记录已确认，禁止重复操作");
        requireFile("TECH_DRAWING_REVIEW", reviewId, "REVIEW_MINUTES", "确认会审前必须上传会审纪要");
        if (jdbc.update("""
                UPDATE tech_drawing_review SET status='CONFIRMED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'
                """, user(), user(), reviewId, tenant()) != 1) throw concurrent();
        Long versionId = longValue(review.get("drawing_version_id"));
        if (booleanValue(review.get("requires_rfi"))) {
            jdbc.update("UPDATE tech_drawing_version SET status='RFI_PENDING',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='UNDER_REVIEW'", versionId, tenant());
        } else {
            approveVersion(versionId);
        }
        return review(reviewId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createRfi(Long reviewId, RfiCommand command) {
        Map<String, Object> review = requireReview(reviewId, false);
        Long projectId = longValue(review.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "发起RFI");
        if (!"CONFIRMED".equals(string(review.get("status"))) || !booleanValue(review.get("requires_rfi")))
            throw error("TECH_RFI_REVIEW_REQUIRED", "只有已确认且存在问题的图纸会审可以发起RFI");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO tech_rfi(id,tenant_id,project_id,drawing_version_id,review_id,rfi_code,subject,question,
                     priority,raised_by,raised_at,response_due_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), projectId, review.get("drawing_version_id"), reviewId, command.rfiCode().trim(),
                    command.subject().trim(), command.question().trim(), command.priority(), user(), command.responseDueDate(),
                    user(), user(), command.remark());
            insertTechItem("TECH_RFI", id, projectId, "TECH_ISSUE", command.rfiCode(), command.subject(),
                    command.priority(), "OPEN", user(), command.responseDueDate());
        } catch (DuplicateKeyException e) {
            throw error("TECH_RFI_DUPLICATE", "同一项目的RFI编号不能重复");
        }
        return rfi(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitRfi(Long id) {
        Map<String, Object> row = requireRfi(id, true);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "提交RFI");
        if (!"DRAFT".equals(string(row.get("status"))))
            throw error("TECH_RFI_SUBMIT_STATE_INVALID", "只有草稿RFI可以提交");
        requireFile("TECH_RFI", id, "RFI_EVIDENCE", "提交RFI前必须上传问题证据");
        if (jdbc.update("""
                UPDATE tech_rfi SET status='SUBMITTED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND status='DRAFT'
                """, user(), id, tenant()) != 1) throw concurrent();
        updateTechItem("TECH_RFI", id, "PENDING", null);
        return rfi(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> respondRfi(Long id, RfiResponseCommand command) {
        Map<String, Object> rfi = requireRfi(id, true);
        projectAccessChecker.checkAccess(longValue(rfi.get("project_id")), "回复RFI");
        if (!"SUBMITTED".equals(string(rfi.get("status"))))
            throw error("TECH_RFI_RESPONSE_STATE_INVALID", "只有已提交且待回复的RFI可以登记设计回复");
        Integer responseNo = jdbc.queryForObject("SELECT COALESCE(MAX(response_no),0)+1 FROM tech_rfi_response WHERE tenant_id=? AND rfi_id=?", Integer.class, tenant(), id);
        Long responseId = IdWorker.getId();
        jdbc.update("""
                INSERT INTO tech_rfi_response(id,tenant_id,rfi_id,response_no,response_content,change_required,responder_name,
                 responded_by,responded_at,status,created_by,created_at,updated_by,updated_at)
                VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'SUBMITTED',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)
                """, responseId, tenant(), id, responseNo, command.responseContent().trim(), command.changeRequired() ? 1 : 0,
                command.responderName().trim(), user(), user(), user());
        if (jdbc.update("""
                UPDATE tech_rfi SET status='RESPONDED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND status='SUBMITTED'
                """, user(), id, tenant()) != 1) throw concurrent();
        return response(responseId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewResponse(Long responseId, ResponseReviewCommand command) {
        Map<String, Object> response = requireResponse(responseId, true);
        Map<String, Object> rfi = requireRfi(longValue(response.get("rfi_id")), true);
        projectAccessChecker.checkAccess(longValue(rfi.get("project_id")), "确认RFI设计回复");
        if (!"SUBMITTED".equals(string(response.get("status"))) || !"RESPONDED".equals(string(rfi.get("status"))))
            throw error("TECH_RFI_RESPONSE_REVIEW_STATE_INVALID", "设计回复已评审或RFI状态已变化");
        if (Objects.equals(longValue(response.get("responded_by")), user()))
            throw error("TECH_RFI_RESPONSE_REVIEWER_CONFLICT", "设计回复登记人与确认人必须相互独立");
        requireFile("TECH_RFI_RESPONSE", responseId, "DESIGN_RESPONSE", "确认设计回复前必须上传正式回复文件");
        if ("REJECTED".equals(command.decision())) {
            updateResponseReview(responseId, "REJECTED", command.reviewComment());
            jdbc.update("UPDATE tech_rfi SET status='SUBMITTED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='RESPONDED'", user(), rfi.get("id"), tenant());
            return response(responseId);
        }
        Map<String, Object> review = requireReview(longValue(rfi.get("review_id")), false);
        if ("REJECTED".equals(string(review.get("conclusion"))) && !booleanValue(response.get("change_required")))
            throw error("TECH_RFI_REJECTED_REVIEW_REQUIRES_CHANGE", "会审退回的图纸必须通过改版闭环，不能按无需改图接受");
        updateResponseReview(responseId, "ACCEPTED", command.reviewComment());
        if (booleanValue(response.get("change_required"))) {
            jdbc.update("UPDATE tech_rfi SET status='CHANGE_PENDING',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='RESPONDED'", user(), rfi.get("id"), tenant());
            updateTechItem("TECH_RFI", longValue(rfi.get("id")), "PENDING", null);
        } else {
            closeRfi(longValue(rfi.get("id")));
            approveVersionWhenResolved(longValue(rfi.get("drawing_version_id")));
        }
        return response(responseId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createDisclosure(Long projectId, DisclosureCommand command) {
        projectAccessChecker.checkAccess(projectId, "创建技术交底");
        Map<String, Object> version = requireVersion(command.drawingVersionId(), false);
        if (!projectId.equals(longValue(version.get("project_id"))) || !"APPROVED".equals(string(version.get("status"))))
            throw error("TECH_DISCLOSURE_APPROVED_DRAWING_REQUIRED", "技术交底必须绑定同项目当前批准图纸版本");
        Map<String, Object> drawing = requireDrawing(longValue(version.get("drawing_id")));
        if (!command.drawingVersionId().equals(longValueNullable(drawing.get("current_version_id"))))
            throw error("TECH_DISCLOSURE_CURRENT_VERSION_REQUIRED", "技术交底不得引用已被替代的图纸版本");
        requireAllRfisClosed(longValue(version.get("drawing_id")));
        if (command.schemeId() != null) {
            Map<String, Object> scheme = requireScheme(command.schemeId(), false);
            if (!projectId.equals(longValue(scheme.get("project_id"))) || !"APPROVED".equals(string(scheme.get("status"))))
                throw error("TECH_DISCLOSURE_SCHEME_INVALID", "技术交底关联的技术方案必须属于同一项目且已审批");
        }
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO tech_disclosure(id,tenant_id,project_id,drawing_version_id,scheme_id,disclosure_code,
                     disclosure_title,disclosure_date,presenter_user_id,recipient_summary,disclosure_content,status,version,
                     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), projectId, command.drawingVersionId(), command.schemeId(), command.disclosureCode().trim(),
                    command.disclosureTitle().trim(), command.disclosureDate(), command.presenterUserId(),
                    command.recipientSummary().trim(), command.disclosureContent().trim(), user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("TECH_DISCLOSURE_DUPLICATE", "同一项目的技术交底编号不能重复");
        }
        return disclosure(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmDisclosure(Long id) {
        Map<String, Object> row = requireDisclosure(id, true);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "确认技术交底");
        if (!"DRAFT".equals(string(row.get("status"))))
            throw error("TECH_DISCLOSURE_CONFIRMED", "技术交底已确认，禁止重复操作");
        requireFile("TECH_DISCLOSURE", id, "DISCLOSURE_RECORD", "确认技术交底前必须上传签字记录");
        if (jdbc.update("""
                UPDATE tech_disclosure SET status='CONFIRMED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'
                """, user(), user(), id, tenant()) != 1) throw concurrent();
        return disclosure(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createConstructionReference(Long projectId, ConstructionReferenceCommand command) {
        projectAccessChecker.checkAccess(projectId, "登记施工图纸引用");
        Map<String, Object> disclosure = requireDisclosure(command.disclosureId(), false);
        if (!projectId.equals(longValue(disclosure.get("project_id"))) || !"CONFIRMED".equals(string(disclosure.get("status"))))
            throw error("TECH_REFERENCE_DISCLOSURE_REQUIRED", "施工引用必须绑定同项目已确认技术交底");
        Map<String, Object> version = requireVersion(longValue(disclosure.get("drawing_version_id")), false);
        if (!"APPROVED".equals(string(version.get("status"))))
            throw error("TECH_REFERENCE_DRAWING_NOT_ACTIVE", "施工引用的图纸版本已失效或尚未批准");
        Map<String, Object> log = queryOne("SELECT * FROM site_daily_log WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "TECH_REFERENCE_DAILY_LOG_NOT_FOUND", "现场日报不存在", command.dailyLogId(), tenant());
        if (!projectId.equals(longValue(log.get("project_id"))) || !"SUBMITTED".equals(string(log.get("status"))))
            throw error("TECH_REFERENCE_SUBMITTED_LOG_REQUIRED", "施工引用必须绑定同项目已提交现场日报");
        if (!command.referenceDate().equals(localDate(log.get("report_date"))))
            throw error("TECH_REFERENCE_DATE_MISMATCH", "施工引用日期必须与现场日报日期一致");
        Map<String, Object> task = queryOne("SELECT * FROM project_wbs_task WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "TECH_REFERENCE_WBS_NOT_FOUND", "WBS任务不存在", command.wbsTaskId(), tenant());
        if (!projectId.equals(longValue(task.get("project_id"))))
            throw error("TECH_REFERENCE_WBS_PROJECT_MISMATCH", "WBS任务不属于当前项目");
        Integer progress = jdbc.queryForObject("SELECT COUNT(*) FROM site_daily_progress WHERE tenant_id=? AND daily_log_id=? AND wbs_task_id=?", Integer.class, tenant(), command.dailyLogId(), command.wbsTaskId());
        if (progress == null || progress != 1)
            throw error("TECH_REFERENCE_PROGRESS_FACT_REQUIRED", "现场日报必须包含该WBS任务的实际施工进度");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO tech_construction_reference(id,tenant_id,project_id,drawing_version_id,disclosure_id,
                     daily_log_id,wbs_task_id,reference_date,work_area,reference_description,status,created_by,created_at,
                     updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'RECORDED',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), projectId, version.get("id"), command.disclosureId(), command.dailyLogId(),
                    command.wbsTaskId(), command.referenceDate(), command.workArea().trim(),
                    command.referenceDescription().trim(), user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("TECH_REFERENCE_DUPLICATE", "同一图纸版本、日报和WBS任务不能重复登记施工引用");
        }
        return constructionReference(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createArchive(Long projectId, ArchiveCommand command) {
        projectAccessChecker.checkAccess(projectId, "创建技术验收归档");
        Map<String, Object> reference = requireConstructionReference(command.constructionReferenceId());
        if (!projectId.equals(longValue(reference.get("project_id"))) || !"RECORDED".equals(string(reference.get("status"))))
            throw error("TECH_ARCHIVE_REFERENCE_INVALID", "验收归档必须绑定同项目有效施工引用");
        Map<String, Object> inspection = queryOne("SELECT * FROM qs_inspection_record WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "TECH_ARCHIVE_INSPECTION_NOT_FOUND", "质量检查记录不存在", command.qualityInspectionId(), tenant());
        if (!projectId.equals(longValue(inspection.get("project_id"))) || !"SUBMITTED".equals(string(inspection.get("status")))
                || !"PASS".equals(string(inspection.get("conclusion"))))
            throw error("TECH_ARCHIVE_PASSED_INSPECTION_REQUIRED", "验收归档必须绑定同项目已提交且结论通过的质量检查");
        Long versionId = longValue(reference.get("drawing_version_id"));
        Map<String, Object> version = requireVersion(versionId, false);
        requireAllRfisClosed(longValue(version.get("drawing_id")));
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO tech_acceptance_archive(id,tenant_id,project_id,drawing_version_id,construction_reference_id,
                     quality_inspection_id,archive_code,acceptance_date,acceptance_conclusion,archive_location,status,
                     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), projectId, versionId, command.constructionReferenceId(), command.qualityInspectionId(),
                    command.archiveCode().trim(), command.acceptanceDate(), command.acceptanceConclusion(),
                    command.archiveLocation().trim(), user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("TECH_ARCHIVE_DUPLICATE", "归档编号重复或该施工引用已经归档");
        }
        return archive(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmArchive(Long id) {
        Map<String, Object> row = requireArchive(id, true);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "确认技术验收归档");
        if (!"DRAFT".equals(string(row.get("status"))))
            throw error("TECH_ARCHIVE_CONFIRMED", "验收归档已确认，禁止重复操作");
        requireFile("TECH_ARCHIVE", id, "ACCEPTANCE_ARCHIVE", "确认归档前必须上传验收及归档凭证");
        if (jdbc.update("""
                UPDATE tech_acceptance_archive SET status='ARCHIVED',archived_by=?,archived_at=CURRENT_TIMESTAMP,
                 updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'
                """, user(), user(), id, tenant()) != 1) throw concurrent();
        return archive(id);
    }

    public Map<String, Object> trace(Long drawingId) {
        Map<String, Object> drawing = new LinkedHashMap<>(requireDrawing(drawingId));
        projectAccessChecker.checkAccess(longValue(drawing.get("project_id")), "追溯图纸技术闭环");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("drawing", drawing);
        result.put("versions", jdbc.queryForList("SELECT * FROM tech_drawing_version WHERE tenant_id=? AND drawing_id=? AND deleted_flag=0 ORDER BY received_at,id", tenant(), drawingId));
        result.put("reviews", jdbc.queryForList("""
                SELECT r.* FROM tech_drawing_review r JOIN tech_drawing_version v ON v.id=r.drawing_version_id
                WHERE r.tenant_id=? AND v.drawing_id=? AND r.deleted_flag=0 ORDER BY r.review_date,r.id
                """, tenant(), drawingId));
        result.put("rfis", jdbc.queryForList("""
                SELECT r.* FROM tech_rfi r JOIN tech_drawing_version v ON v.id=r.drawing_version_id
                WHERE r.tenant_id=? AND v.drawing_id=? AND r.deleted_flag=0 ORDER BY r.raised_at,r.id
                """, tenant(), drawingId));
        result.put("responses", jdbc.queryForList("""
                SELECT p.* FROM tech_rfi_response p JOIN tech_rfi r ON r.id=p.rfi_id
                JOIN tech_drawing_version v ON v.id=r.drawing_version_id WHERE p.tenant_id=? AND v.drawing_id=?
                ORDER BY p.responded_at,p.id
                """, tenant(), drawingId));
        result.put("disclosures", jdbc.queryForList("""
                SELECT d.* FROM tech_disclosure d JOIN tech_drawing_version v ON v.id=d.drawing_version_id
                WHERE d.tenant_id=? AND v.drawing_id=? AND d.deleted_flag=0 ORDER BY d.disclosure_date,d.id
                """, tenant(), drawingId));
        result.put("schemes", jdbc.queryForList("""
                SELECT DISTINCT s.* FROM technical_scheme s JOIN tech_disclosure d ON d.scheme_id=s.id
                JOIN tech_drawing_version v ON v.id=d.drawing_version_id
                WHERE s.tenant_id=? AND v.drawing_id=? AND s.deleted_flag=0 ORDER BY s.created_at,s.id
                """, tenant(), drawingId));
        result.put("schemeApprovals", jdbc.queryForList("""
                SELECT DISTINCT i.* FROM wf_instance i JOIN technical_scheme s ON s.approval_instance_id=i.id
                JOIN tech_disclosure d ON d.scheme_id=s.id JOIN tech_drawing_version v ON v.id=d.drawing_version_id
                WHERE i.tenant_id=? AND v.drawing_id=? AND i.deleted_flag=0 ORDER BY i.started_at,i.id
                """, tenant(), drawingId));
        result.put("constructionReferences", jdbc.queryForList("""
                SELECT c.*,l.report_date dailyLogDate,t.task_code wbsTaskCode,t.task_name wbsTaskName
                FROM tech_construction_reference c JOIN tech_drawing_version v ON v.id=c.drawing_version_id
                JOIN site_daily_log l ON l.id=c.daily_log_id JOIN project_wbs_task t ON t.id=c.wbs_task_id
                WHERE c.tenant_id=? AND v.drawing_id=? AND c.deleted_flag=0 ORDER BY c.reference_date,c.id
                """, tenant(), drawingId));
        result.put("archives", jdbc.queryForList("""
                SELECT a.*,q.inspection_code inspectionCode,q.conclusion inspectionConclusion
                FROM tech_acceptance_archive a JOIN tech_drawing_version v ON v.id=a.drawing_version_id
                JOIN qs_inspection_record q ON q.id=a.quality_inspection_id
                WHERE a.tenant_id=? AND v.drawing_id=? AND a.deleted_flag=0 ORDER BY a.acceptance_date,a.id
                """, tenant(), drawingId));
        return result;
    }

    private void insertDrawingVersion(Long id, Long projectId, Long drawingId, String versionNo, Long previousId,
                                      Long sourceRfiId, LocalDateTime receivedAt, String changeSummary, String remark) {
        jdbc.update("""
                INSERT INTO tech_drawing_version(id,tenant_id,project_id,drawing_id,version_no,previous_version_id,
                 source_rfi_id,received_at,received_by,change_summary,status,version,created_by,created_at,
                 updated_by,updated_at,deleted_flag,remark)
                VALUES(?,?,?,?,?,?,?,?,?,?,'RECEIVED',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                """, id, tenant(), projectId, drawingId, versionNo.trim(), previousId, sourceRfiId, receivedAt,
                user(), text(changeSummary), user(), user(), remark);
    }

    private void approveVersion(Long versionId) {
        Map<String, Object> version = requireVersion(versionId, true);
        Long drawingId = longValue(version.get("drawing_id"));
        Long previousVersionId = longValueNullable(version.get("previous_version_id"));
        if (previousVersionId != null) {
            jdbc.update("""
                    UPDATE tech_drawing_version SET status='SUPERSEDED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND tenant_id=? AND drawing_id=? AND status IN('RECEIVED','UNDER_REVIEW','RFI_PENDING','APPROVED')
                    """, user(), previousVersionId, tenant(), drawingId);
        }
        jdbc.update("""
                UPDATE tech_drawing_version SET status='SUPERSEDED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND drawing_id=? AND status='APPROVED' AND id<>?
                """, user(), tenant(), drawingId, versionId);
        if (jdbc.update("""
                UPDATE tech_drawing_version SET status='APPROVED',approved_at=CURRENT_TIMESTAMP,version=version+1,
                 updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status IN('UNDER_REVIEW','RFI_PENDING')
                """, user(), versionId, tenant()) != 1) throw concurrent();
        jdbc.update("UPDATE tech_drawing SET current_version_id=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", versionId, user(), drawingId, tenant());
        Long sourceRfiId = longValueNullable(version.get("source_rfi_id"));
        if (sourceRfiId != null) closeRfi(sourceRfiId);
    }

    private void approveVersionWhenResolved(Long versionId) {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM tech_rfi WHERE tenant_id=? AND drawing_version_id=? AND status NOT IN('CLOSED','CANCELLED') AND deleted_flag=0", Integer.class, tenant(), versionId);
        if (open != null && open == 0) {
            Map<String, Object> review = queryOne("SELECT * FROM tech_drawing_review WHERE tenant_id=? AND drawing_version_id=? AND deleted_flag=0",
                    "TECH_DRAWING_REVIEW_NOT_FOUND", "图纸会审不存在", tenant(), versionId);
            if (!"REJECTED".equals(string(review.get("conclusion")))) approveVersion(versionId);
        }
    }

    private void closeRfi(Long rfiId) {
        jdbc.update("UPDATE tech_rfi SET status='CLOSED',closed_by=?,closed_at=CURRENT_TIMESTAMP,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status<>'CLOSED'", user(), user(), rfiId, tenant());
        updateTechItem("TECH_RFI", rfiId, "CLOSED", LocalDateTime.now());
    }

    private void requireAllRfisClosed(Long drawingId) {
        Integer open = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tech_rfi r JOIN tech_drawing_version v ON v.id=r.drawing_version_id
                WHERE r.tenant_id=? AND v.drawing_id=? AND r.deleted_flag=0 AND r.status NOT IN('CLOSED','CANCELLED')
                """, Integer.class, tenant(), drawingId);
        if (open != null && open > 0) throw error("TECH_RFI_OPEN", "图纸仍有未关闭RFI，禁止交底或归档");
    }

    private void updateResponseReview(Long responseId, String status, String comment) {
        if (jdbc.update("""
                UPDATE tech_rfi_response SET status=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_comment=?,
                 updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='SUBMITTED'
                """, status, user(), comment.trim(), user(), responseId, tenant()) != 1) throw concurrent();
    }

    private Map<String, Object> review(Long id) {
        return requireReview(id, false);
    }

    private Map<String, Object> rfi(Long id) {
        Map<String, Object> result = new LinkedHashMap<>(requireRfi(id, false));
        result.put("responses", jdbc.queryForList("SELECT * FROM tech_rfi_response WHERE tenant_id=? AND rfi_id=? ORDER BY response_no", tenant(), id));
        return result;
    }

    private Map<String, Object> response(Long id) {
        return requireResponse(id, false);
    }

    private Map<String, Object> version(Long id) {
        return requireVersion(id, false);
    }

    private Map<String, Object> disclosure(Long id) {
        return requireDisclosure(id, false);
    }

    private Map<String, Object> constructionReference(Long id) {
        return requireConstructionReference(id);
    }

    private Map<String, Object> archive(Long id) {
        return requireArchive(id, false);
    }

    private Map<String, Object> requireScheme(Long id, boolean lock) {
        return queryOne("SELECT * FROM technical_scheme WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "TECH_SCHEME_NOT_FOUND", "技术方案不存在", id, tenant());
    }

    private Map<String, Object> requireDrawing(Long id) {
        return queryOne("SELECT * FROM tech_drawing WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "TECH_DRAWING_NOT_FOUND", "图纸不存在", id, tenant());
    }

    private Map<String, Object> requireVersion(Long id, boolean lock) {
        return queryOne("SELECT * FROM tech_drawing_version WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "TECH_DRAWING_VERSION_NOT_FOUND", "图纸版本不存在", id, tenant());
    }

    private Map<String, Object> requireReview(Long id, boolean lock) {
        return queryOne("SELECT * FROM tech_drawing_review WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "TECH_DRAWING_REVIEW_NOT_FOUND", "图纸会审不存在", id, tenant());
    }

    private Map<String, Object> requireRfi(Long id, boolean lock) {
        return queryOne("SELECT * FROM tech_rfi WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "TECH_RFI_NOT_FOUND", "RFI不存在", id, tenant());
    }

    private Map<String, Object> requireResponse(Long id, boolean lock) {
        return queryOne("SELECT * FROM tech_rfi_response WHERE id=? AND tenant_id=?" + (lock ? " FOR UPDATE" : ""),
                "TECH_RFI_RESPONSE_NOT_FOUND", "RFI设计回复不存在", id, tenant());
    }

    private Map<String, Object> requireDisclosure(Long id, boolean lock) {
        return queryOne("SELECT * FROM tech_disclosure WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "TECH_DISCLOSURE_NOT_FOUND", "技术交底不存在", id, tenant());
    }

    private Map<String, Object> requireConstructionReference(Long id) {
        return queryOne("SELECT * FROM tech_construction_reference WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "TECH_REFERENCE_NOT_FOUND", "施工引用不存在", id, tenant());
    }

    private Map<String, Object> requireArchive(Long id, boolean lock) {
        return queryOne("SELECT * FROM tech_acceptance_archive WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "TECH_ARCHIVE_NOT_FOUND", "技术验收归档不存在", id, tenant());
    }

    private Map<String, Object> queryOne(String sql, String code, String message, Object... args) {
        try {
            return jdbc.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException e) {
            throw error(code, message);
        }
    }

    private void requireActiveProject(Long projectId) {
        Map<String, Object> project = queryOne("SELECT id,tenant_id,status FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "PROJECT_NOT_FOUND", "项目不存在", projectId, tenant());
        if (!"ACTIVE".equals(string(project.get("status"))))
            throw error("TECH_PROJECT_NOT_ACTIVE", "只有进行中的项目可以开展技术业务");
    }

    private void requireFile(String businessType, Long businessId, String documentType, String message) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_file WHERE tenant_id=? AND business_type=? AND business_id=?
                 AND document_type=? AND virus_scan_status='CLEAN' AND deleted_flag=0
                """, Integer.class, tenant(), businessType, businessId, documentType);
        if (count == null || count == 0) throw error("TECH_ATTACHMENT_REQUIRED", message);
    }

    private void insertTechItem(String sourceType, Long sourceId, Long projectId, String itemType, String itemCode,
                                String title, String level, String status, Long responsibleUserId, LocalDate dueDate) {
        jdbc.update("""
                INSERT INTO tech_item(id,tenant_id,project_id,item_type,item_code,item_title,item_level,item_status,
                 discovered_at,due_date,responsible_user_id,source_type,source_id,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)
                """, IdWorker.getId(), tenant(), projectId, itemType, itemCode.trim(), title.trim(), level, status,
                Timestamp.valueOf(dueDate.atTime(LocalTime.MAX)), responsibleUserId, sourceType, sourceId, user(), user());
    }

    private void updateTechItem(String sourceType, Long sourceId, String status, LocalDateTime closedAt) {
        jdbc.update("""
                UPDATE tech_item SET item_status=?,closed_at=?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND source_type=? AND source_id=? AND deleted_flag=0
                """, status, closedAt, user(), tenant(), sourceType, sourceId);
    }

    private Long tenant() { return UserContext.getCurrentTenantId(); }
    private Long user() { return UserContext.getCurrentUserId(); }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Long longValue(Object value) { return value instanceof Number n ? n.longValue() : Long.valueOf(value.toString()); }
    private Long longValueNullable(Object value) { return value == null ? null : longValue(value); }
    private boolean booleanValue(Object value) { return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() != 0; }
    private LocalDate localDate(Object value) { return value instanceof LocalDate d ? d : value instanceof Date d ? d.toLocalDate() : LocalDate.parse(value.toString()); }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private BusinessException concurrent() { return error("TECH_CONCURRENT_MODIFICATION", "业务状态已变化，请刷新后重试"); }
}
