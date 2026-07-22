package com.cgcpms.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostSubjectV2Service;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.quality.dto.QualitySafetyModels.*;
import com.cgcpms.quality.entity.*;
import com.cgcpms.quality.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QualitySafetyService {
    private static final Set<String> INSPECTION_TYPES = Set.of("QUALITY", "SAFETY");
    private static final Set<String> FREQUENCIES = Set.of("SINGLE", "WEEKLY", "MONTHLY");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> ISSUE_STATUSES = Set.of("OPEN", "RECTIFYING", "PENDING_REINSPECTION", "CLOSED");
    private static final Set<String> EXTERNAL_PARTNER_TYPES = Set.of("SUPPLIER", "SUB", "SUBCONTRACTOR");
    private static final String SOURCE_TYPE = "QUALITY_SAFETY_CONSEQUENCE";
    private static final String COST_TYPE = "QUALITY_REWORK";

    private final QualityInspectionPlanMapper planMapper;
    private final QualityInspectionRecordMapper inspectionMapper;
    private final QualitySafetyIssueMapper issueMapper;
    private final QualityRectificationMapper rectificationMapper;
    private final QualityConsequenceMapper consequenceMapper;
    private final QualityPartnerEvaluationMapper evaluationMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final PmProjectMapper projectMapper;
    private final MdPartnerMapper partnerMapper;
    private final CtContractMapper contractMapper;
    private final SysFileMapper fileMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectV2Service costSubjectV2Service;
    private final JdbcTemplate jdbc;

    public List<QualityInspectionPlan> listPlans(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "查询质量安全检查计划");
        return planMapper.selectList(new LambdaQueryWrapper<QualityInspectionPlan>()
                .eq(QualityInspectionPlan::getTenantId, tenantId())
                .eq(QualityInspectionPlan::getProjectId, projectId)
                .orderByDesc(QualityInspectionPlan::getStartDate)
                .orderByDesc(QualityInspectionPlan::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityInspectionPlan createPlan(PlanCommand command) {
        validatePlanCommand(command);
        projectAccessChecker.checkAccess(command.projectId(), "创建质量安全检查计划");
        requireProjectActive(command.projectId());
        requireActiveProjectMember(command.projectId(), command.ownerUserId());
        QualityInspectionPlan plan = new QualityInspectionPlan();
        applyPlan(plan, command);
        plan.setTenantId(tenantId());
        plan.setStatus("DRAFT");
        plan.setVersion(0);
        try {
            planMapper.insert(plan);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("QS_PLAN_CODE_DUPLICATE", "同一项目下检查计划编号不能重复");
        }
        return requirePlan(plan.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityInspectionPlan updatePlan(Long id, PlanCommand command) {
        validatePlanCommand(command);
        QualityInspectionPlan plan = requirePlan(id);
        projectAccessChecker.checkAccess(plan.getProjectId(), "修改质量安全检查计划");
        projectAccessChecker.checkAccess(command.projectId(), "修改质量安全检查计划");
        requireProjectActive(command.projectId());
        requireActiveProjectMember(command.projectId(), command.ownerUserId());
        if (!"DRAFT".equals(plan.getStatus())) throw immutable("已激活的检查计划不可修改");
        applyPlan(plan, command);
        try {
            if (planMapper.updateById(plan) != 1) throw concurrent();
        } catch (DuplicateKeyException e) {
            throw new BusinessException("QS_PLAN_CODE_DUPLICATE", "同一项目下检查计划编号不能重复");
        }
        return requirePlan(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityInspectionPlan activatePlan(Long id) {
        QualityInspectionPlan plan = requirePlan(id);
        projectAccessChecker.checkAccess(plan.getProjectId(), "激活质量安全检查计划");
        requireProjectActive(plan.getProjectId());
        int updated = planMapper.update(null, new LambdaUpdateWrapper<QualityInspectionPlan>()
                .eq(QualityInspectionPlan::getId, id)
                .eq(QualityInspectionPlan::getTenantId, tenantId())
                .eq(QualityInspectionPlan::getStatus, "DRAFT")
                .set(QualityInspectionPlan::getStatus, "ACTIVE")
                .set(QualityInspectionPlan::getActivatedBy, userId())
                .set(QualityInspectionPlan::getActivatedAt, LocalDateTime.now()));
        if (updated != 1) throw immutable("检查计划只能从草稿状态激活，禁止重复激活");
        return requirePlan(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityInspectionPlan completePlan(Long id) {
        QualityInspectionPlan plan = requirePlan(id);
        projectAccessChecker.checkAccess(plan.getProjectId(), "完成质量安全检查计划");
        if (!"ACTIVE".equals(plan.getStatus())) throw immutable("只有执行中的检查计划可以完成");
        long submitted = inspectionMapper.selectCount(new LambdaQueryWrapper<QualityInspectionRecord>()
                .eq(QualityInspectionRecord::getTenantId, tenantId()).eq(QualityInspectionRecord::getPlanId, id)
                .eq(QualityInspectionRecord::getStatus, "SUBMITTED"));
        long drafts = inspectionMapper.selectCount(new LambdaQueryWrapper<QualityInspectionRecord>()
                .eq(QualityInspectionRecord::getTenantId, tenantId()).eq(QualityInspectionRecord::getPlanId, id)
                .eq(QualityInspectionRecord::getStatus, "DRAFT"));
        long openIssues = issueMapper.selectCount(new LambdaQueryWrapper<QualitySafetyIssue>()
                .eq(QualitySafetyIssue::getTenantId, tenantId()).eq(QualitySafetyIssue::getPlanId, id)
                .ne(QualitySafetyIssue::getStatus, "CLOSED"));
        if (submitted == 0) throw new BusinessException("QS_PLAN_NO_INSPECTION", "至少提交一份检查记录后才能完成计划");
        if (drafts > 0) throw new BusinessException("QS_PLAN_HAS_DRAFT_INSPECTION", "仍有未提交的检查记录，不能完成计划");
        if (openIssues > 0) throw new BusinessException("QS_PLAN_HAS_OPEN_ISSUE", "仍有未关闭的问题单，不能完成计划");
        int updated = planMapper.update(null, new LambdaUpdateWrapper<QualityInspectionPlan>()
                .eq(QualityInspectionPlan::getId, id).eq(QualityInspectionPlan::getTenantId, tenantId())
                .eq(QualityInspectionPlan::getStatus, "ACTIVE")
                .set(QualityInspectionPlan::getStatus, "COMPLETED")
                .set(QualityInspectionPlan::getCompletedBy, userId())
                .set(QualityInspectionPlan::getCompletedAt, LocalDateTime.now()));
        if (updated != 1) throw concurrent();
        return requirePlan(id);
    }

    public List<QualityInspectionRecord> listInspections(Long planId) {
        QualityInspectionPlan plan = requirePlan(planId);
        projectAccessChecker.checkAccess(plan.getProjectId(), "查询质量安全检查记录");
        return inspectionMapper.selectList(new LambdaQueryWrapper<QualityInspectionRecord>()
                .eq(QualityInspectionRecord::getTenantId, tenantId())
                .eq(QualityInspectionRecord::getPlanId, planId)
                .orderByDesc(QualityInspectionRecord::getInspectionDate)
                .orderByDesc(QualityInspectionRecord::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityInspectionRecord createInspection(InspectionCommand command) {
        QualityInspectionPlan plan = requirePlan(command.planId());
        projectAccessChecker.checkAccess(plan.getProjectId(), "创建质量安全检查记录");
        requireProjectActive(plan.getProjectId());
        requireActiveProjectMember(plan.getProjectId(), command.inspectorUserId());
        if (!"ACTIVE".equals(plan.getStatus())) throw new BusinessException("QS_PLAN_NOT_ACTIVE", "只有执行中的检查计划可以录入检查记录");
        if (command.inspectionDate().isBefore(plan.getStartDate()) || command.inspectionDate().isAfter(plan.getEndDate())) {
            throw new BusinessException("QS_INSPECTION_DATE_OUT_OF_PLAN", "检查日期必须在计划起止日期内");
        }
        QualityInspectionRecord record = new QualityInspectionRecord();
        record.setTenantId(tenantId());
        record.setPlanId(plan.getId());
        record.setProjectId(plan.getProjectId());
        record.setInspectionCode(normalizeCode(command.inspectionCode()));
        record.setInspectionDate(command.inspectionDate());
        record.setLocation(command.location().trim());
        record.setInspectorUserId(command.inspectorUserId());
        record.setConclusion("PENDING");
        record.setSummary(command.summary().trim());
        record.setStatus("DRAFT");
        record.setVersion(0);
        record.setRemark(command.remark());
        try {
            inspectionMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("QS_INSPECTION_CODE_DUPLICATE", "同一项目下检查记录编号不能重复");
        }
        return requireInspection(record.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public QualitySafetyIssue createIssue(Long inspectionId, IssueCommand command) {
        if (!Objects.equals(inspectionId, command.inspectionId()))
            throw new BusinessException("QS_INSPECTION_ID_MISMATCH", "路径检查记录与请求数据不一致");
        QualityInspectionRecord inspection = requireInspection(inspectionId);
        projectAccessChecker.checkAccess(inspection.getProjectId(), "创建质量安全问题单");
        requireActiveProjectMember(inspection.getProjectId(), command.responsibleUserId());
        if (!"DRAFT".equals(inspection.getStatus())) throw immutable("已提交检查记录不能新增问题单");
        String severity = upper(command.severity());
        if (!SEVERITIES.contains(severity)) throw new BusinessException("QS_ISSUE_SEVERITY_INVALID", "问题严重程度不合法");
        String responsibleKind = upper(command.responsibleKind());
        Long partnerId = command.responsiblePartnerId();
        if ("PARTNER".equals(responsibleKind)) {
            requireExternalPartner(partnerId);
        } else if ("INTERNAL".equals(responsibleKind)) {
            if (partnerId != null) throw new BusinessException("QS_ISSUE_PARTNER_FORBIDDEN", "内部责任问题不能绑定外部合作方");
        } else {
            throw new BusinessException("QS_RESPONSIBLE_KIND_INVALID", "责任类型必须为内部或合作方");
        }
        if (command.dueDate().isBefore(inspection.getInspectionDate()))
            throw new BusinessException("QS_ISSUE_DUE_DATE_INVALID", "整改期限不能早于检查日期");
        QualityInspectionPlan plan = requirePlan(inspection.getPlanId());
        QualitySafetyIssue issue = new QualitySafetyIssue();
        issue.setTenantId(tenantId());
        issue.setPlanId(plan.getId());
        issue.setInspectionId(inspection.getId());
        issue.setProjectId(inspection.getProjectId());
        issue.setIssueCode(nextIssueCode(inspection));
        issue.setIssueType(plan.getInspectionType());
        issue.setCategory(command.category().trim());
        issue.setSeverity(severity);
        issue.setTitle(command.title().trim());
        issue.setDescription(command.description().trim());
        issue.setResponsibleKind(responsibleKind);
        issue.setResponsiblePartnerId(partnerId);
        issue.setResponsibleUserId(command.responsibleUserId());
        issue.setDueDate(command.dueDate());
        issue.setStatus("OPEN");
        issue.setVersion(0);
        issue.setRemark(command.remark());
        try {
            issueMapper.insert(issue);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("QS_ISSUE_CREATE_CONFLICT", "问题单编号冲突，请重试");
        }
        return requireIssue(issue.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityInspectionRecord submitInspection(Long id) {
        QualityInspectionRecord inspection = requireInspection(id);
        projectAccessChecker.checkAccess(inspection.getProjectId(), "提交质量安全检查记录");
        if (!"DRAFT".equals(inspection.getStatus())) throw immutable("检查记录已提交，禁止重复提交");
        requireFile("QS_INSPECTION", inspection.getId(), "INSPECTION_EVIDENCE", "检查记录至少需要一份现场证据");
        List<QualitySafetyIssue> issues = issueMapper.selectList(new LambdaQueryWrapper<QualitySafetyIssue>()
                .eq(QualitySafetyIssue::getTenantId, tenantId()).eq(QualitySafetyIssue::getInspectionId, id));
        for (QualitySafetyIssue issue : issues) {
            requireFile("QS_ISSUE", issue.getId(), "ISSUE_EVIDENCE", "每个问题单至少需要一份问题证据");
        }
        int updated = inspectionMapper.update(null, new LambdaUpdateWrapper<QualityInspectionRecord>()
                .eq(QualityInspectionRecord::getId, id).eq(QualityInspectionRecord::getTenantId, tenantId())
                .eq(QualityInspectionRecord::getStatus, "DRAFT")
                .set(QualityInspectionRecord::getConclusion, issues.isEmpty() ? "PASS" : "ISSUES")
                .set(QualityInspectionRecord::getStatus, "SUBMITTED")
                .set(QualityInspectionRecord::getSubmittedBy, userId())
                .set(QualityInspectionRecord::getSubmittedAt, LocalDateTime.now()));
        if (updated != 1) throw concurrent();
        for (QualitySafetyIssue issue : issues) {
            int issueUpdated = issueMapper.update(null, new LambdaUpdateWrapper<QualitySafetyIssue>()
                    .eq(QualitySafetyIssue::getId, issue.getId()).eq(QualitySafetyIssue::getTenantId, tenantId())
                    .eq(QualitySafetyIssue::getStatus, "OPEN")
                    .set(QualitySafetyIssue::getStatus, "RECTIFYING"));
            if (issueUpdated != 1) throw concurrent();
        }
        return requireInspection(id);
    }

    public List<QualitySafetyIssue> listIssues(Long projectId, String status) {
        projectAccessChecker.checkAccess(projectId, "查询质量安全问题单");
        LambdaQueryWrapper<QualitySafetyIssue> query = new LambdaQueryWrapper<QualitySafetyIssue>()
                .eq(QualitySafetyIssue::getTenantId, tenantId()).eq(QualitySafetyIssue::getProjectId, projectId);
        if (status != null && !status.isBlank()) {
            String normalized = upper(status);
            if (!ISSUE_STATUSES.contains(normalized)) throw new BusinessException("QS_ISSUE_STATUS_INVALID", "问题单状态不合法");
            query.eq(QualitySafetyIssue::getStatus, normalized);
        }
        return issueMapper.selectList(query.orderByAsc(QualitySafetyIssue::getDueDate)
                .orderByDesc(QualitySafetyIssue::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityRectification createRectification(RectificationCommand command) {
        QualitySafetyIssue issue = requireIssue(command.issueId());
        projectAccessChecker.checkAccess(issue.getProjectId(), "创建质量安全整改记录");
        requireActiveProjectMember(issue.getProjectId(), command.responsibleUserId());
        if (!"RECTIFYING".equals(issue.getStatus())) throw new BusinessException("QS_ISSUE_NOT_RECTIFYING", "当前问题单不在整改状态");
        if (!Objects.equals(issue.getResponsibleUserId(), command.responsibleUserId()))
            throw new BusinessException("QS_RECTIFICATION_RESPONSIBLE_MISMATCH", "整改责任人必须与问题单责任人一致");
        if (command.plannedCompleteDate().isAfter(issue.getDueDate()))
            throw new BusinessException("QS_RECTIFICATION_OVERDUE_PLAN", "整改计划完成日不能晚于问题单整改期限");
        long active = rectificationMapper.selectCount(new LambdaQueryWrapper<QualityRectification>()
                .eq(QualityRectification::getTenantId, tenantId()).eq(QualityRectification::getIssueId, issue.getId())
                .in(QualityRectification::getStatus, List.of("DRAFT", "SUBMITTED")));
        if (active > 0) throw new BusinessException("QS_RECTIFICATION_ACTIVE_EXISTS", "问题单已有进行中的整改轮次");
        List<QualityRectification> history = rectificationMapper.selectList(new LambdaQueryWrapper<QualityRectification>()
                .eq(QualityRectification::getTenantId, tenantId()).eq(QualityRectification::getIssueId, issue.getId())
                .orderByDesc(QualityRectification::getRoundNo));
        int round = history.isEmpty() ? 1 : history.get(0).getRoundNo() + 1;
        QualityRectification rectification = new QualityRectification();
        rectification.setTenantId(tenantId());
        rectification.setIssueId(issue.getId());
        rectification.setProjectId(issue.getProjectId());
        rectification.setRoundNo(round);
        rectification.setActionDescription(command.actionDescription().trim());
        rectification.setResponsibleUserId(command.responsibleUserId());
        rectification.setPlannedCompleteDate(command.plannedCompleteDate());
        rectification.setStatus("DRAFT");
        rectification.setVersion(0);
        rectification.setRemark(command.remark());
        try {
            rectificationMapper.insert(rectification);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("QS_RECTIFICATION_ROUND_CONFLICT", "整改轮次冲突，请刷新后重试");
        }
        return requireRectification(rectification.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityRectification submitRectification(Long id) {
        QualityRectification rectification = requireRectification(id);
        QualitySafetyIssue issue = requireIssue(rectification.getIssueId());
        projectAccessChecker.checkAccess(issue.getProjectId(), "提交质量安全整改");
        if (!isAdmin() && !Objects.equals(userId(), rectification.getResponsibleUserId()))
            throw new BusinessException("QS_RECTIFICATION_SUBMIT_FORBIDDEN", "只能由整改责任人提交整改结果");
        if (!"DRAFT".equals(rectification.getStatus()) || !"RECTIFYING".equals(issue.getStatus()))
            throw immutable("整改记录已提交或问题单状态已变化");
        requireFile("QS_RECTIFICATION", id, "RECTIFICATION_EVIDENCE", "提交整改必须上传整改完成证据");
        LocalDateTime now = LocalDateTime.now();
        int updated = rectificationMapper.update(null, new LambdaUpdateWrapper<QualityRectification>()
                .eq(QualityRectification::getId, id).eq(QualityRectification::getTenantId, tenantId())
                .eq(QualityRectification::getStatus, "DRAFT")
                .set(QualityRectification::getStatus, "SUBMITTED")
                .set(QualityRectification::getActualCompletedAt, now)
                .set(QualityRectification::getSubmittedBy, userId())
                .set(QualityRectification::getSubmittedAt, now));
        if (updated != 1) throw concurrent();
        int issueUpdated = issueMapper.update(null, new LambdaUpdateWrapper<QualitySafetyIssue>()
                .eq(QualitySafetyIssue::getId, issue.getId()).eq(QualitySafetyIssue::getTenantId, tenantId())
                .eq(QualitySafetyIssue::getStatus, "RECTIFYING")
                .set(QualitySafetyIssue::getStatus, "PENDING_REINSPECTION"));
        if (issueUpdated != 1) throw concurrent();
        return requireRectification(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityRectification reinspect(Long id, ReinspectionCommand command) {
        QualityRectification rectification = requireRectification(id);
        QualitySafetyIssue issue = requireIssue(rectification.getIssueId());
        projectAccessChecker.checkAccess(issue.getProjectId(), "复验质量安全整改");
        if (Objects.equals(userId(), rectification.getResponsibleUserId()))
            throw new BusinessException("QS_REINSPECTION_SEGREGATION_REQUIRED", "整改责任人不能复验本人提交的整改");
        if (!"SUBMITTED".equals(rectification.getStatus()) || !"PENDING_REINSPECTION".equals(issue.getStatus()))
            throw immutable("当前整改记录不处于待复验状态");
        String result = upper(command.result());
        if (!Set.of("PASS", "REJECT").contains(result))
            throw new BusinessException("QS_REINSPECTION_RESULT_INVALID", "复验结果只能为通过或驳回");
        requireFile("QS_RECTIFICATION", id, "REINSPECTION_EVIDENCE", "复验必须上传复验证据");
        LocalDateTime now = LocalDateTime.now();
        String rectStatus = "PASS".equals(result) ? "PASSED" : "REJECTED";
        int updated = rectificationMapper.update(null, new LambdaUpdateWrapper<QualityRectification>()
                .eq(QualityRectification::getId, id).eq(QualityRectification::getTenantId, tenantId())
                .eq(QualityRectification::getStatus, "SUBMITTED")
                .set(QualityRectification::getStatus, rectStatus)
                .set(QualityRectification::getReinspectionComment, command.comment().trim())
                .set(QualityRectification::getReinspectedBy, userId())
                .set(QualityRectification::getReinspectedAt, now));
        if (updated != 1) throw concurrent();
        LambdaUpdateWrapper<QualitySafetyIssue> issueUpdate = new LambdaUpdateWrapper<QualitySafetyIssue>()
                .eq(QualitySafetyIssue::getId, issue.getId()).eq(QualitySafetyIssue::getTenantId, tenantId())
                .eq(QualitySafetyIssue::getStatus, "PENDING_REINSPECTION")
                .set(QualitySafetyIssue::getStatus, "PASS".equals(result) ? "CLOSED" : "RECTIFYING");
        if ("PASS".equals(result)) {
            issueUpdate.set(QualitySafetyIssue::getClosedBy, userId()).set(QualitySafetyIssue::getClosedAt, now);
        }
        if (issueMapper.update(null, issueUpdate) != 1) throw concurrent();
        return requireRectification(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityConsequence createConsequence(ConsequenceCommand command) {
        QualitySafetyIssue issue = requireIssue(command.issueId());
        projectAccessChecker.checkAccess(issue.getProjectId(), "登记质量安全处罚成本与评价");
        if (!"CLOSED".equals(issue.getStatus())) throw new BusinessException("QS_ISSUE_NOT_CLOSED", "问题单关闭后才能登记处罚成本与评价");
        if (!"PARTNER".equals(issue.getResponsibleKind()) || !Objects.equals(issue.getResponsiblePartnerId(), command.partnerId()))
            throw new BusinessException("QS_CONSEQUENCE_PARTNER_MISMATCH", "处罚评价合作方必须与问题单责任合作方一致");
        requireExternalPartner(command.partnerId());
        validateContract(command.contractId(), issue.getProjectId(), command.partnerId());
        validateDecision(command);
        if (consequenceMapper.selectCount(new LambdaQueryWrapper<QualityConsequence>()
                .eq(QualityConsequence::getTenantId, tenantId()).eq(QualityConsequence::getIssueId, issue.getId())) > 0)
            throw new BusinessException("QS_CONSEQUENCE_EXISTS", "同一问题单只能登记一份处罚成本与评价");
        QualityConsequence consequence = new QualityConsequence();
        consequence.setTenantId(tenantId());
        consequence.setIssueId(issue.getId());
        consequence.setProjectId(issue.getProjectId());
        consequence.setPartnerId(command.partnerId());
        consequence.setContractId(command.contractId());
        consequence.setConsequenceCode(normalizeCode(command.consequenceCode()));
        consequence.setDecisionType(upper(command.decisionType()));
        consequence.setFineAmount(command.fineAmount());
        consequence.setReworkCostAmount(command.reworkCostAmount());
        consequence.setEvaluationScore(command.evaluationScore());
        consequence.setEvaluationComment(command.evaluationComment().trim());
        consequence.setStatus("DRAFT");
        consequence.setVersion(0);
        consequence.setRemark(command.remark());
        try {
            consequenceMapper.insert(consequence);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("QS_CONSEQUENCE_CONFLICT", "处罚成本记录编号或问题关系冲突");
        }
        return requireConsequence(consequence.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityConsequence postConsequence(Long id) {
        QualityConsequence consequence = requireConsequence(id);
        QualitySafetyIssue issue = requireIssue(consequence.getIssueId());
        projectAccessChecker.checkAccess(issue.getProjectId(), "确认质量安全处罚成本与评价");
        if (!"CLOSED".equals(issue.getStatus()) || !"DRAFT".equals(consequence.getStatus()))
            throw immutable("只有已关闭问题的草稿处罚成本记录可以确认");
        Long costItemId = createCostIfRequired(consequence);
        Long costSubjectId = costItemId == null ? null : costItemMapper.selectById(costItemId).getCostSubjectId();
        QualityPartnerEvaluation evaluation = new QualityPartnerEvaluation();
        evaluation.setTenantId(tenantId());
        evaluation.setConsequenceId(consequence.getId());
        evaluation.setIssueId(issue.getId());
        evaluation.setProjectId(issue.getProjectId());
        evaluation.setPartnerId(consequence.getPartnerId());
        evaluation.setEvaluationType(issue.getIssueType());
        evaluation.setScore(consequence.getEvaluationScore());
        evaluation.setEvaluationComment(consequence.getEvaluationComment());
        evaluation.setEvaluatedBy(userId());
        evaluation.setEvaluatedAt(LocalDateTime.now());
        evaluation.setCreatedBy(userId());
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setDeletedFlag(0);
        evaluationMapper.insert(evaluation);
        int updated = consequenceMapper.update(null, new LambdaUpdateWrapper<QualityConsequence>()
                .eq(QualityConsequence::getId, id).eq(QualityConsequence::getTenantId, tenantId())
                .eq(QualityConsequence::getStatus, "DRAFT")
                .set(QualityConsequence::getStatus, "POSTED")
                .set(QualityConsequence::getCostSubjectId, costSubjectId)
                .set(QualityConsequence::getCostItemId, costItemId)
                .set(QualityConsequence::getEvaluationId, evaluation.getId())
                .set(QualityConsequence::getPostedBy, userId())
                .set(QualityConsequence::getPostedAt, LocalDateTime.now()));
        if (updated != 1) throw concurrent();
        return requireConsequence(id);
    }

    public Trace trace(Long issueId) {
        QualitySafetyIssue issue = requireIssue(issueId);
        projectAccessChecker.checkAccess(issue.getProjectId(), "追溯质量安全整改闭环");
        QualityInspectionPlan plan = requirePlan(issue.getPlanId());
        QualityInspectionRecord inspection = requireInspection(issue.getInspectionId());
        List<QualityRectification> rectifications = rectificationMapper.selectList(new LambdaQueryWrapper<QualityRectification>()
                .eq(QualityRectification::getTenantId, tenantId()).eq(QualityRectification::getIssueId, issueId)
                .orderByAsc(QualityRectification::getRoundNo));
        QualityConsequence consequence = first(consequenceMapper.selectList(new LambdaQueryWrapper<QualityConsequence>()
                .eq(QualityConsequence::getTenantId, tenantId()).eq(QualityConsequence::getIssueId, issueId)));
        QualityPartnerEvaluation evaluation = consequence == null ? null : first(evaluationMapper.selectList(
                new LambdaQueryWrapper<QualityPartnerEvaluation>()
                        .eq(QualityPartnerEvaluation::getTenantId, tenantId())
                        .eq(QualityPartnerEvaluation::getConsequenceId, consequence.getId())
                        .eq(QualityPartnerEvaluation::getDeletedFlag, 0)));
        CostItem costItem = consequence == null || consequence.getCostItemId() == null
                ? null : costItemMapper.selectById(consequence.getCostItemId());
        return new Trace(plan, inspection, issue, rectifications, consequence, evaluation, costItem);
    }

    private void validatePlanCommand(PlanCommand command) {
        if (!INSPECTION_TYPES.contains(upper(command.inspectionType())))
            throw new BusinessException("QS_PLAN_TYPE_INVALID", "检查类型只能为质量或安全");
        if (!FREQUENCIES.contains(upper(command.frequencyType())))
            throw new BusinessException("QS_PLAN_FREQUENCY_INVALID", "检查频次不合法");
        if (command.endDate().isBefore(command.startDate()))
            throw new BusinessException("QS_PLAN_DATE_INVALID", "计划结束日期不能早于开始日期");
    }

    private void applyPlan(QualityInspectionPlan plan, PlanCommand command) {
        plan.setProjectId(command.projectId());
        plan.setPlanCode(normalizeCode(command.planCode()));
        plan.setPlanName(command.planName().trim());
        plan.setInspectionType(upper(command.inspectionType()));
        plan.setFrequencyType(upper(command.frequencyType()));
        plan.setStartDate(command.startDate());
        plan.setEndDate(command.endDate());
        plan.setOwnerUserId(command.ownerUserId());
        plan.setRemark(command.remark());
    }

    private void validateDecision(ConsequenceCommand command) {
        String type = upper(command.decisionType());
        BigDecimal fine = command.fineAmount();
        BigDecimal cost = command.reworkCostAmount();
        boolean valid = switch (type) {
            case "NONE" -> fine.signum() == 0 && cost.signum() == 0;
            case "FINE" -> fine.signum() > 0 && cost.signum() == 0;
            case "REWORK_COST" -> fine.signum() == 0 && cost.signum() > 0;
            case "BOTH" -> fine.signum() > 0 && cost.signum() > 0;
            default -> false;
        };
        if (!valid) throw new BusinessException("QS_CONSEQUENCE_DECISION_INVALID", "处罚决策与罚款、返工成本金额不一致");
    }

    private Long createCostIfRequired(QualityConsequence consequence) {
        if (consequence.getReworkCostAmount().signum() <= 0) return null;
        CostItem existing = first(costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, tenantId()).eq(CostItem::getSourceType, SOURCE_TYPE)
                .eq(CostItem::getSourceId, consequence.getId()).eq(CostItem::getSourceItemId, 0L)));
        if (existing != null) return existing.getId();
        CostItem cost = new CostItem();
        cost.setTenantId(tenantId());
        cost.setProjectId(consequence.getProjectId());
        cost.setContractId(consequence.getContractId());
        cost.setPartnerId(consequence.getPartnerId());
        QualitySafetyIssue issue = requireIssue(consequence.getIssueId());
        cost.setCostSubjectId(costSubjectV2Service.resolveRule(SOURCE_TYPE, issue.getIssueType(), consequence.getProjectId()));
        cost.setCostType(COST_TYPE);
        cost.setAmount(consequence.getReworkCostAmount());
        cost.setTaxAmount(BigDecimal.ZERO);
        cost.setAmountWithoutTax(consequence.getReworkCostAmount());
        cost.setSourceType(SOURCE_TYPE);
        cost.setSourceId(consequence.getId());
        cost.setSourceItemId(0L);
        cost.setCostDate(LocalDate.now());
        cost.setCostStatus("CONFIRMED");
        cost.setGeneratedFlag(1);
        cost.setRemark("质量安全问题返工成本，问题单=" + consequence.getIssueId());
        try {
            costItemMapper.insert(cost);
            return cost.getId();
        } catch (DuplicateKeyException e) {
            existing = first(costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                    .eq(CostItem::getTenantId, tenantId()).eq(CostItem::getSourceType, SOURCE_TYPE)
                    .eq(CostItem::getSourceId, consequence.getId()).eq(CostItem::getSourceItemId, 0L)));
            if (existing != null) return existing.getId();
            throw e;
        }
    }

    private void validateContract(Long contractId, Long projectId, Long partnerId) {
        if (contractId == null)
            throw new BusinessException("QS_CONTRACT_REQUIRED", "关联合同不能为空");
        CtContract contract = contractMapper.selectById(contractId);
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId()))
            throw new BusinessException("QS_CONTRACT_NOT_FOUND", "关联合同不存在");
        if (!Objects.equals(contract.getProjectId(), projectId))
            throw new BusinessException("QS_CONTRACT_PROJECT_MISMATCH", "关联合同不属于问题所在项目");
        if (!Objects.equals(contract.getPartyAId(), partnerId) && !Objects.equals(contract.getPartyBId(), partnerId))
            throw new BusinessException("QS_CONTRACT_PARTNER_MISMATCH", "关联合同未绑定责任合作方");
    }

    private void requireProjectActive(Long projectId) {
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId()))
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        if (!"ACTIVE".equals(project.getStatus()))
            throw new BusinessException("QS_PROJECT_NOT_ACTIVE", "只有进行中的项目可以新建或激活质量安全检查");
    }

    private void requireActiveProjectMember(Long projectId, Long userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_user u
                JOIN pm_project_member m ON m.tenant_id=u.tenant_id AND m.user_id=u.id
                WHERE u.id=? AND u.tenant_id=? AND u.status='ENABLE' AND u.deleted_flag=0
                 AND m.project_id=? AND m.status='ACTIVE' AND m.deleted_flag=0
                """, Integer.class, userId, tenantId(), projectId);
        if (count == null || count == 0)
            throw new BusinessException("QS_RESPONSIBLE_PROJECT_MEMBER_INVALID", "责任人不存在、跨租户、已停用或不是目标项目有效成员");
    }

    private MdPartner requireExternalPartner(Long partnerId) {
        if (partnerId == null) throw new BusinessException("QS_PARTNER_REQUIRED", "外部责任问题必须绑定供应商或分包商");
        MdPartner partner = partnerMapper.selectById(partnerId);
        if (partner == null || !Objects.equals(partner.getTenantId(), tenantId()))
            throw new BusinessException("QS_PARTNER_NOT_FOUND", "责任合作方不存在");
        if (!EXTERNAL_PARTNER_TYPES.contains(upper(partner.getPartnerType())))
            throw new BusinessException("QS_PARTNER_TYPE_INVALID", "责任合作方必须是供应商或分包商");
        if (!"ENABLE".equals(partner.getStatus()))
            throw new BusinessException("QS_PARTNER_DISABLED", "责任合作方已停用");
        return partner;
    }

    private void requireFile(String businessType, Long businessId, String documentType, String message) {
        long count = fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId()).eq(SysFile::getBusinessType, businessType)
                .eq(SysFile::getBusinessId, businessId).eq(SysFile::getDocumentType, documentType)
                .eq(SysFile::getVirusScanStatus, "CLEAN"));
        if (count == 0) throw new BusinessException("QS_EVIDENCE_REQUIRED", message);
    }

    private String nextIssueCode(QualityInspectionRecord inspection) {
        long count = issueMapper.selectCount(new LambdaQueryWrapper<QualitySafetyIssue>()
                .eq(QualitySafetyIssue::getTenantId, tenantId()).eq(QualitySafetyIssue::getInspectionId, inspection.getId()));
        return normalizeCode(inspection.getInspectionCode()) + "-ISS-" + String.format("%03d", count + 1);
    }

    private QualityInspectionPlan requirePlan(Long id) {
        QualityInspectionPlan result = planMapper.selectById(id);
        if (result == null || !Objects.equals(result.getTenantId(), tenantId()))
            throw new BusinessException("QS_PLAN_NOT_FOUND", "质量安全检查计划不存在");
        return result;
    }

    private QualityInspectionRecord requireInspection(Long id) {
        QualityInspectionRecord result = inspectionMapper.selectById(id);
        if (result == null || !Objects.equals(result.getTenantId(), tenantId()))
            throw new BusinessException("QS_INSPECTION_NOT_FOUND", "质量安全检查记录不存在");
        return result;
    }

    private QualitySafetyIssue requireIssue(Long id) {
        QualitySafetyIssue result = issueMapper.selectById(id);
        if (result == null || !Objects.equals(result.getTenantId(), tenantId()))
            throw new BusinessException("QS_ISSUE_NOT_FOUND", "质量安全问题单不存在");
        return result;
    }

    private QualityRectification requireRectification(Long id) {
        QualityRectification result = rectificationMapper.selectById(id);
        if (result == null || !Objects.equals(result.getTenantId(), tenantId()))
            throw new BusinessException("QS_RECTIFICATION_NOT_FOUND", "整改记录不存在");
        return result;
    }

    private QualityConsequence requireConsequence(Long id) {
        QualityConsequence result = consequenceMapper.selectById(id);
        if (result == null || !Objects.equals(result.getTenantId(), tenantId()))
            throw new BusinessException("QS_CONSEQUENCE_NOT_FOUND", "处罚成本记录不存在");
        return result;
    }

    private Long tenantId() { return UserContext.getCurrentTenantId(); }
    private Long userId() { return UserContext.getCurrentUserId(); }
    private boolean isAdmin() { return UserContext.getCurrentRoles().stream().anyMatch(Set.of("ADMIN", "SUPER_ADMIN")::contains); }
    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private String normalizeCode(String value) { return upper(value); }
    private BusinessException immutable(String message) { return new BusinessException("QS_STATE_IMMUTABLE", message); }
    private BusinessException concurrent() { return new BusinessException("QS_CONCURRENT_MODIFICATION", "数据状态已变化，请刷新后重试"); }
    private <T> T first(List<T> values) { return values == null || values.isEmpty() ? null : values.get(0); }
}
