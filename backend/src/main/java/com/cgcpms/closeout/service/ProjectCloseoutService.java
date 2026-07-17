package com.cgcpms.closeout.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.closeout.dto.ProjectCloseoutModels.*;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectCloseoutService {
    private final JdbcTemplate jdbc;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;

    public Map<String, Object> overview(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "查看项目竣工收尾工作台");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closeout", findOne("""
                SELECT c.id,c.project_id projectId,c.closeout_code closeoutCode,c.planned_completion_date plannedCompletionDate,
                 c.actual_completion_date actualCompletionDate,c.status,c.final_owner_settlement_id finalOwnerSettlementId,
                 c.tail_collection_verified_at tailCollectionVerifiedAt,c.closed_at closedAt,c.remark
                FROM project_closeout c WHERE c.tenant_id=? AND c.project_id=? AND c.deleted_flag=0
                """, tenant(), projectId));
        result.put("sectionAcceptances", jdbc.queryForList("""
                SELECT a.id,a.closeout_id closeoutId,a.wbs_task_id wbsTaskId,w.task_code taskCode,w.task_name taskName,
                 a.quality_inspection_id qualityInspectionId,a.acceptance_code acceptanceCode,a.acceptance_name acceptanceName,
                 a.acceptance_date acceptanceDate,a.conclusion,a.status,a.confirmed_at confirmedAt,a.remark
                FROM closeout_section_acceptance a JOIN project_wbs_task w ON w.id=a.wbs_task_id
                WHERE a.tenant_id=? AND a.project_id=? AND a.deleted_flag=0 ORDER BY a.acceptance_date,a.id
                """, tenant(), projectId));
        result.put("finalAcceptances", jdbc.queryForList("""
                SELECT id,closeout_id closeoutId,acceptance_code acceptanceCode,acceptance_date acceptanceDate,
                 organizer,participant_summary participantSummary,conclusion,acceptance_summary acceptanceSummary,
                 status,approval_instance_id approvalInstanceId,approved_at approvedAt,remark
                FROM closeout_final_acceptance WHERE tenant_id=? AND project_id=? AND deleted_flag=0
                ORDER BY created_at DESC
                """, tenant(), projectId));
        result.put("settlements", jdbc.queryForList("""
                SELECT s.id,s.contract_id contractId,s.settlement_code settlementCode,s.settlement_date settlementDate,
                 s.gross_amount grossAmount,s.retention_amount retentionAmount,s.net_receivable_amount netReceivableAmount,
                 s.status,s.settlement_type settlementType
                FROM owner_settlement s JOIN project_closeout c ON c.final_owner_settlement_id=s.id
                WHERE c.tenant_id=? AND c.project_id=? AND c.deleted_flag=0
                """, tenant(), projectId));
        result.put("receivables", jdbc.queryForList("""
                SELECT r.id,r.settlement_id settlementId,r.contract_id contractId,r.receivable_type receivableType,
                 r.receivable_code receivableCode,r.original_amount originalAmount,r.collected_amount collectedAmount,
                 r.outstanding_amount outstandingAmount,r.due_date dueDate,r.status
                FROM account_receivable r JOIN project_closeout c ON c.final_owner_settlement_id=r.settlement_id
                WHERE c.tenant_id=? AND c.project_id=? AND r.deleted_flag=0 ORDER BY r.receivable_type,r.id
                """, tenant(), projectId));
        result.put("warranties", jdbc.queryForList("""
                SELECT id,closeout_id closeoutId,contract_id contractId,receivable_id receivableId,warranty_code warrantyCode,
                 warranty_amount warrantyAmount,warranty_start_date warrantyStartDate,warranty_end_date warrantyEndDate,
                 responsible_user_id responsibleUserId,status,released_at releasedAt,remark
                FROM closeout_warranty WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY created_at DESC
                """, tenant(), projectId));
        result.put("defects", jdbc.queryForList("""
                SELECT id,warranty_id warrantyId,defect_code defectCode,defect_title defectTitle,
                 responsible_user_id responsibleUserId,rectification_deadline rectificationDeadline,status,
                 rectified_by rectifiedBy,rectified_at rectifiedAt,verified_by verifiedBy,verified_at verifiedAt,
                 verification_comment verificationComment,remark
                FROM closeout_defect WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY created_at DESC
                """, tenant(), projectId));
        result.put("archiveTransfers", jdbc.queryForList("""
                SELECT id,closeout_id closeoutId,transfer_code transferCode,transfer_date transferDate,
                 recipient_organization recipientOrganization,recipient_name recipientName,archive_location archiveLocation,
                 transfer_scope transferScope,status,accepted_at acceptedAt,remark
                FROM closeout_archive_transfer WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY created_at DESC
                """, tenant(), projectId));
        result.put("wbsReadiness", jdbc.queryForMap("""
                SELECT COUNT(*) totalTasks,
                 COALESCE(SUM(CASE WHEN status='COMPLETED' THEN 0 ELSE 1 END),0) incompleteTasks
                FROM project_wbs_task WHERE tenant_id=? AND project_id=? AND deleted_flag=0
                """, tenant(), projectId));
        result.put("wbsTasks", jdbc.queryForList("""
                SELECT id,task_code taskCode,task_name taskName,work_area workArea,status,actual_progress actualProgress
                FROM project_wbs_task WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY sort_order,id
                """, tenant(), projectId));
        result.put("qualityInspections", jdbc.queryForList("""
                SELECT id,inspection_code inspectionCode,inspection_date inspectionDate,location,conclusion,status
                FROM qs_inspection_record WHERE tenant_id=? AND project_id=? AND deleted_flag=0
                ORDER BY inspection_date DESC,id DESC
                """, tenant(), projectId));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> initiate(InitiateCommand command) {
        projectAccessChecker.checkAccess(command.projectId(), "发起项目竣工收尾");
        Map<String, Object> project = requireProject(command.projectId(), true);
        if (!Set.of("ACTIVE", "SUSPENDED").contains(string(project.get("status"))))
            throw error("CLOSEOUT_PROJECT_STATE_INVALID", "只有进行中或暂停中的项目可以发起收尾");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO project_closeout(id,tenant_id,project_id,closeout_code,planned_completion_date,status,
                     version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,'INITIATED',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), command.projectId(), command.closeoutCode().trim(),
                    command.plannedCompletionDate(), user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("CLOSEOUT_DUPLICATE", "同一项目只能存在一条收尾主线，且收尾编号不可重复");
        }
        return closeout(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createSectionAcceptance(Long closeoutId, SectionAcceptanceCommand command) {
        Map<String, Object> closeout = requireCloseout(closeoutId, true);
        requireMutable(closeout);
        Long projectId = longValue(closeout.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "登记分部分项验收");
        Map<String, Object> wbs = queryOne("SELECT project_id,status FROM project_wbs_task WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "CLOSEOUT_WBS_NOT_FOUND", "WBS任务不存在", command.wbsTaskId(), tenant());
        if (!projectId.equals(longValue(wbs.get("project_id"))))
            throw error("CLOSEOUT_PROJECT_MISMATCH", "WBS任务不属于当前项目");
        Map<String, Object> inspection = queryOne("SELECT project_id,status,conclusion FROM qs_inspection_record WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "CLOSEOUT_INSPECTION_NOT_FOUND", "质量验收记录不存在", command.qualityInspectionId(), tenant());
        if (!projectId.equals(longValue(inspection.get("project_id"))))
            throw error("CLOSEOUT_PROJECT_MISMATCH", "质量验收记录不属于当前项目");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO closeout_section_acceptance(id,tenant_id,closeout_id,project_id,wbs_task_id,quality_inspection_id,
                     acceptance_code,acceptance_name,acceptance_date,conclusion,status,version,
                     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), closeoutId, projectId, command.wbsTaskId(), command.qualityInspectionId(),
                    command.acceptanceCode().trim(), command.acceptanceName().trim(), command.acceptanceDate(), command.conclusion(),
                    user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("CLOSEOUT_SECTION_DUPLICATE", "验收编号重复或该WBS任务已登记验收");
        }
        return sectionAcceptance(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmSectionAcceptance(Long id) {
        Map<String, Object> row = requireSection(id, true);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "确认分部分项验收");
        if (!"DRAFT".equals(string(row.get("status"))))
            throw error("CLOSEOUT_SECTION_STATE_INVALID", "只有草稿分部分项验收可以确认");
        requireFile("CLOSEOUT_SECTION_ACCEPTANCE", id, "SECTION_ACCEPTANCE_RECORD", "确认分部分项验收前必须上传验收记录");
        Map<String, Object> wbs = queryOne("SELECT status FROM project_wbs_task WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "CLOSEOUT_WBS_NOT_FOUND", "WBS任务不存在", row.get("wbs_task_id"), tenant());
        if (!"COMPLETED".equals(string(wbs.get("status"))))
            throw error("CLOSEOUT_WBS_NOT_COMPLETED", "对应WBS任务尚未完工，不能确认验收");
        Map<String, Object> inspection = queryOne("SELECT status,conclusion FROM qs_inspection_record WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "CLOSEOUT_INSPECTION_NOT_FOUND", "质量验收记录不存在", row.get("quality_inspection_id"), tenant());
        if (!"SUBMITTED".equals(string(inspection.get("status"))) || !"PASS".equals(string(inspection.get("conclusion"))))
            throw error("CLOSEOUT_QUALITY_NOT_PASSED", "只有已提交且结论通过的质量验收记录可支撑分项验收");
        if (jdbc.update("""
                UPDATE closeout_section_acceptance SET status='ACCEPTED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND status='DRAFT'
                """, user(), user(), id, tenant()) != 1) throw concurrent();
        jdbc.update("UPDATE project_closeout SET status='SECTION_ACCEPTANCE',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='INITIATED'",
                user(), row.get("closeout_id"), tenant());
        return sectionAcceptance(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createFinalAcceptance(Long closeoutId, FinalAcceptanceCommand command) {
        Map<String, Object> closeout = requireCloseout(closeoutId, true);
        requireMutable(closeout);
        Long projectId = longValue(closeout.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "登记竣工验收");
        validateFinalAcceptanceReadiness(closeoutId, projectId);
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO closeout_final_acceptance(id,tenant_id,closeout_id,project_id,acceptance_code,acceptance_date,
                     organizer,participant_summary,conclusion,acceptance_summary,status,version,
                     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), closeoutId, projectId, command.acceptanceCode().trim(), command.acceptanceDate(),
                    command.organizer().trim(), command.participantSummary().trim(), command.conclusion(),
                    command.acceptanceSummary().trim(), user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("CLOSEOUT_FINAL_ACCEPTANCE_DUPLICATE", "同一收尾主线只能存在一张竣工验收单，且验收编号不可重复");
        }
        return finalAcceptance(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitFinalAcceptance(Long id) {
        Map<String, Object> row = requireFinalAcceptance(id, true);
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "提交竣工验收审批");
        if (!Set.of("DRAFT", "REJECTED").contains(string(row.get("status"))))
            throw error("CLOSEOUT_FINAL_SUBMIT_STATE_INVALID", "只有草稿或驳回的竣工验收可以提交");
        if (!"PASS".equals(string(row.get("conclusion"))))
            throw error("CLOSEOUT_FINAL_NOT_PASSED", "竣工验收结论必须为通过才能提交审批");
        validateFinalAcceptanceReadiness(longValue(row.get("closeout_id")), longValue(row.get("project_id")));
        requireFile("CLOSEOUT_FINAL_ACCEPTANCE", id, "FINAL_ACCEPTANCE_CERTIFICATE", "提交竣工验收前必须上传竣工验收证明");
        Long existing = longValueNullable(row.get("approval_instance_id"));
        WfInstance instance = "REJECTED".equals(string(row.get("status"))) && existing != null
                ? workflowEngine.resubmit(existing, user(), UserContext.getCurrentUsername())
                : workflowEngine.submit(user(), UserContext.getCurrentUsername(), tenant(),
                WorkflowBusinessTypes.PROJECT_FINAL_ACCEPTANCE, id, string(row.get("acceptance_code")), BigDecimal.ZERO,
                longValue(row.get("project_id")), null, "项目竣工验收审批", null, null);
        if (jdbc.update("""
                UPDATE closeout_final_acceptance SET status='PENDING',approval_instance_id=?,version=version+1,
                 updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status IN('DRAFT','REJECTED')
                """, instance.getId(), user(), id, tenant()) != 1) throw concurrent();
        jdbc.update("UPDATE project_closeout SET status='FINAL_ACCEPTANCE_PENDING',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                user(), row.get("closeout_id"), tenant());
        return finalAcceptance(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onFinalAcceptanceApproved(Long id) {
        Map<String, Object> row = requireFinalAcceptance(id, true);
        if ("APPROVED".equals(string(row.get("status")))) return;
        if (!"PENDING".equals(string(row.get("status"))))
            throw error("CLOSEOUT_FINAL_APPROVAL_STATE_INVALID", "竣工验收审批状态不正确");
        if (jdbc.update("""
                UPDATE closeout_final_acceptance SET status='APPROVED',approved_by=?,approved_at=CURRENT_TIMESTAMP,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'
                """, user(), user(), id, tenant()) != 1) throw concurrent();
        jdbc.update("UPDATE project_closeout SET status='FINAL_ACCEPTANCE_APPROVED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                user(), row.get("closeout_id"), tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public void onFinalAcceptanceRejected(Long id) {
        Map<String, Object> row = requireFinalAcceptance(id, true);
        if ("REJECTED".equals(string(row.get("status")))) return;
        if (!"PENDING".equals(string(row.get("status"))))
            throw error("CLOSEOUT_FINAL_REJECT_STATE_INVALID", "竣工验收审批状态不正确");
        jdbc.update("UPDATE closeout_final_acceptance SET status='REJECTED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                user(), id, tenant());
        jdbc.update("UPDATE project_closeout SET status='SECTION_ACCEPTANCE',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                user(), row.get("closeout_id"), tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bindFinalSettlement(Long closeoutId, SettlementBindingCommand command) {
        Map<String, Object> closeout = requireCloseout(closeoutId, true);
        Long projectId = longValue(closeout.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "绑定竣工结算");
        requireStage(closeout, Set.of("FINAL_ACCEPTANCE_APPROVED"), "竣工验收审批通过后才能绑定竣工结算");
        Map<String, Object> settlement = queryOne("SELECT * FROM owner_settlement WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE",
                "CLOSEOUT_SETTLEMENT_NOT_FOUND", "业主结算不存在", command.ownerSettlementId(), tenant());
        if (!projectId.equals(longValue(settlement.get("project_id"))))
            throw error("CLOSEOUT_PROJECT_MISMATCH", "业主结算不属于当前项目");
        if (!"RECEIVABLE_CREATED".equals(string(settlement.get("status"))))
            throw error("CLOSEOUT_SETTLEMENT_NOT_FINALIZED", "只有审批通过且已生成应收的业主结算可以作为竣工结算");
        BigDecimal retention = decimal(settlement.get("retention_amount"));
        if (retention.signum() <= 0)
            throw error("CLOSEOUT_RETENTION_REQUIRED", "竣工结算必须形成可追溯的质保金应收");
        Integer regularCount = jdbc.queryForObject("SELECT COUNT(*) FROM account_receivable WHERE tenant_id=? AND settlement_id=? AND receivable_type='REGULAR' AND deleted_flag=0",
                Integer.class, tenant(), command.ownerSettlementId());
        Integer retentionCount = jdbc.queryForObject("SELECT COUNT(*) FROM account_receivable WHERE tenant_id=? AND settlement_id=? AND receivable_type='RETENTION' AND deleted_flag=0",
                Integer.class, tenant(), command.ownerSettlementId());
        if (regularCount == null || regularCount == 0 || retentionCount == null || retentionCount == 0)
            throw error("CLOSEOUT_RECEIVABLE_INCOMPLETE", "竣工结算必须同时生成尾款和质保金应收");
        jdbc.update("UPDATE owner_settlement SET settlement_type='FINAL',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                user(), command.ownerSettlementId(), tenant());
        if (jdbc.update("""
                UPDATE project_closeout SET final_owner_settlement_id=?,status='FINAL_SETTLEMENT_BOUND',version=version+1,
                 updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND final_owner_settlement_id IS NULL
                """, command.ownerSettlementId(), user(), closeoutId, tenant()) != 1) throw concurrent();
        return closeout(closeoutId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> verifyTailCollection(Long closeoutId) {
        Map<String, Object> closeout = requireCloseout(closeoutId, true);
        projectAccessChecker.checkAccess(longValue(closeout.get("project_id")), "确认竣工尾款回收");
        requireStage(closeout, Set.of("FINAL_SETTLEMENT_BOUND"), "绑定竣工结算后才能确认尾款回收");
        Long settlementId = longValue(closeout.get("final_owner_settlement_id"));
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(original_amount),0) original_amount,COALESCE(SUM(outstanding_amount),0) outstanding_amount
                FROM account_receivable WHERE tenant_id=? AND settlement_id=? AND receivable_type='REGULAR' AND deleted_flag=0
                """, tenant(), settlementId);
        BigDecimal original = decimal(totals.get("original_amount"));
        if (original.signum() <= 0 || decimal(totals.get("outstanding_amount")).signum() != 0)
            throw error("CLOSEOUT_TAIL_COLLECTION_INCOMPLETE", "竣工尾款尚未全部回收");
        BigDecimal allocated = jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.allocated_amount),0) FROM collection_allocation a
                JOIN collection_record c ON c.id=a.collection_id
                JOIN account_receivable r ON r.id=a.receivable_id
                WHERE a.tenant_id=? AND r.settlement_id=? AND r.receivable_type='REGULAR'
                 AND c.status='SUCCESS' AND c.deleted_flag=0 AND r.deleted_flag=0
                """, BigDecimal.class, tenant(), settlementId);
        if (allocated == null || allocated.compareTo(original) < 0)
            throw error("CLOSEOUT_TAIL_COLLECTION_TRACE_MISSING", "尾款虽已核销，但缺少足额成功回款分配记录");
        jdbc.update("""
                UPDATE project_closeout SET status='TAIL_PAYMENT_COLLECTED',tail_collection_verified_at=CURRENT_TIMESTAMP,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?
                """, user(), closeoutId, tenant());
        return closeout(closeoutId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> registerWarranty(Long closeoutId, WarrantyCommand command) {
        Map<String, Object> closeout = requireCloseout(closeoutId, true);
        Long projectId = longValue(closeout.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "登记质保责任");
        requireStage(closeout, Set.of("TAIL_PAYMENT_COLLECTED", "WARRANTY_ACTIVE", "DEFECT_LIABILITY"), "尾款回收确认后才能登记质保责任");
        if (command.warrantyEndDate().isBefore(command.warrantyStartDate()))
            throw error("CLOSEOUT_WARRANTY_DATE_INVALID", "质保截止日期不能早于开始日期");
        Long settlementId = longValue(closeout.get("final_owner_settlement_id"));
        Map<String, Object> receivable = queryOne("""
                SELECT * FROM account_receivable WHERE id=? AND tenant_id=? AND settlement_id=?
                 AND receivable_type='RETENTION' AND deleted_flag=0 FOR UPDATE
                """, "CLOSEOUT_RETENTION_RECEIVABLE_NOT_FOUND", "质保金应收不存在", command.receivableId(), tenant(), settlementId);
        if (!projectId.equals(longValue(receivable.get("project_id")))
                || !command.contractId().equals(longValue(receivable.get("contract_id"))))
            throw error("CLOSEOUT_PROJECT_MISMATCH", "质保金应收、合同与项目关系不一致");
        if (decimal(receivable.get("original_amount")).compareTo(command.warrantyAmount()) != 0)
            throw error("CLOSEOUT_WARRANTY_AMOUNT_MISMATCH", "质保金额必须等于结算形成的质保金应收原值");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO closeout_warranty(id,tenant_id,closeout_id,project_id,contract_id,receivable_id,warranty_code,
                     warranty_amount,warranty_start_date,warranty_end_date,responsible_user_id,status,version,
                     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), closeoutId, projectId, command.contractId(), command.receivableId(),
                    command.warrantyCode().trim(), command.warrantyAmount(), command.warrantyStartDate(), command.warrantyEndDate(),
                    command.responsibleUserId(), user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("CLOSEOUT_WARRANTY_DUPLICATE", "质保编号重复或该质保金应收已登记责任期");
        }
        jdbc.update("UPDATE project_closeout SET status='WARRANTY_ACTIVE',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                user(), closeoutId, tenant());
        return warranty(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createDefect(Long warrantyId, DefectCommand command) {
        Map<String, Object> warranty = requireWarranty(warrantyId, true);
        projectAccessChecker.checkAccess(longValue(warranty.get("project_id")), "登记缺陷责任");
        if (!Set.of("ACTIVE", "DEFECT_LIABILITY").contains(string(warranty.get("status"))))
            throw error("CLOSEOUT_WARRANTY_STATE_INVALID", "当前质保责任状态不允许新增缺陷");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO closeout_defect(id,tenant_id,closeout_id,project_id,warranty_id,defect_code,defect_title,
                     defect_description,responsible_user_id,rectification_deadline,status,version,
                     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'OPEN',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), warranty.get("closeout_id"), warranty.get("project_id"), warrantyId,
                    command.defectCode().trim(), command.defectTitle().trim(), command.defectDescription().trim(),
                    command.responsibleUserId(), command.rectificationDeadline(), user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("CLOSEOUT_DEFECT_DUPLICATE", "缺陷编号不可重复");
        }
        jdbc.update("UPDATE closeout_warranty SET status='DEFECT_LIABILITY',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                user(), warrantyId, tenant());
        jdbc.update("UPDATE project_closeout SET status='DEFECT_LIABILITY',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                user(), warranty.get("closeout_id"), tenant());
        return defect(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rectifyDefect(Long id, RectificationCommand command) {
        Map<String, Object> defect = requireDefect(id, true);
        projectAccessChecker.checkAccess(longValue(defect.get("project_id")), "提交缺陷整改");
        if (!"OPEN".equals(string(defect.get("status"))))
            throw error("CLOSEOUT_DEFECT_RECTIFY_STATE_INVALID", "只有待整改缺陷可以提交整改");
        requireFile("CLOSEOUT_DEFECT", id, "DEFECT_RECTIFICATION_EVIDENCE", "提交缺陷整改前必须上传整改证据");
        if (jdbc.update("""
                UPDATE closeout_defect SET status='PENDING_VERIFICATION',rectification_content=?,rectified_by=?,rectified_at=CURRENT_TIMESTAMP,
                 verified_by=NULL,verified_at=NULL,verification_comment=NULL,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND status='OPEN'
                """, command.rectificationContent().trim(), user(), user(), id, tenant()) != 1) throw concurrent();
        return defect(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> verifyDefect(Long id, DefectVerificationCommand command) {
        Map<String, Object> defect = requireDefect(id, true);
        projectAccessChecker.checkAccess(longValue(defect.get("project_id")), "复验缺陷整改");
        if (!"PENDING_VERIFICATION".equals(string(defect.get("status"))))
            throw error("CLOSEOUT_DEFECT_VERIFY_STATE_INVALID", "只有待复验缺陷可以复验");
        if (user().equals(longValue(defect.get("rectified_by"))))
            throw error("CLOSEOUT_DEFECT_REVIEWER_CONFLICT", "整改人与复验人必须分离");
        String status = "ACCEPTED".equals(command.decision()) ? "CLOSED" : "OPEN";
        if (jdbc.update("""
                UPDATE closeout_defect SET status=?,verified_by=?,verified_at=CURRENT_TIMESTAMP,verification_comment=?,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND tenant_id=? AND status='PENDING_VERIFICATION'
                """, status, user(), command.verificationComment().trim(), user(), id, tenant()) != 1) throw concurrent();
        return defect(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> releaseWarranty(Long id) {
        Map<String, Object> warranty = requireWarranty(id, true);
        projectAccessChecker.checkAccess(longValue(warranty.get("project_id")), "释放质保金");
        if (!Set.of("ACTIVE", "DEFECT_LIABILITY").contains(string(warranty.get("status"))))
            throw error("CLOSEOUT_WARRANTY_RELEASE_STATE_INVALID", "当前质保责任状态不允许释放");
        if (localDate(warranty.get("warranty_end_date")).isAfter(LocalDate.now()))
            throw error("CLOSEOUT_WARRANTY_NOT_DUE", "质保期尚未届满，不能释放质保金");
        Integer openDefects = jdbc.queryForObject("SELECT COUNT(*) FROM closeout_defect WHERE tenant_id=? AND warranty_id=? AND status<>'CLOSED' AND deleted_flag=0",
                Integer.class, tenant(), id);
        if (openDefects != null && openDefects > 0)
            throw error("CLOSEOUT_DEFECTS_OPEN", "仍有未关闭缺陷，不能释放质保金");
        Map<String, Object> receivable = queryOne("SELECT * FROM account_receivable WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "CLOSEOUT_RETENTION_RECEIVABLE_NOT_FOUND", "质保金应收不存在", warranty.get("receivable_id"), tenant());
        if (decimal(receivable.get("outstanding_amount")).signum() != 0)
            throw error("CLOSEOUT_RETENTION_COLLECTION_INCOMPLETE", "质保金尚未回收完毕");
        BigDecimal allocated = jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.allocated_amount),0) FROM collection_allocation a
                JOIN collection_record c ON c.id=a.collection_id
                WHERE a.tenant_id=? AND a.receivable_id=? AND c.status='SUCCESS' AND c.deleted_flag=0
                """, BigDecimal.class, tenant(), warranty.get("receivable_id"));
        if (allocated == null || allocated.compareTo(decimal(receivable.get("original_amount"))) < 0)
            throw error("CLOSEOUT_RETENTION_TRACE_MISSING", "质保金缺少足额成功回款分配记录");
        requireFile("CLOSEOUT_WARRANTY", id, "WARRANTY_RELEASE_VOUCHER", "释放质保金前必须上传释放或回收凭证");
        jdbc.update("""
                UPDATE closeout_warranty SET status='RELEASED',released_by=?,released_at=CURRENT_TIMESTAMP,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?
                """, user(), user(), id, tenant());
        Long closeoutId = longValue(warranty.get("closeout_id"));
        Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM closeout_warranty WHERE tenant_id=? AND closeout_id=? AND status<>'RELEASED' AND deleted_flag=0",
                Integer.class, tenant(), closeoutId);
        if (remaining != null && remaining == 0) {
            jdbc.update("UPDATE project_closeout SET status='WARRANTY_RELEASED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                    user(), closeoutId, tenant());
        }
        return warranty(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createArchiveTransfer(Long closeoutId, ArchiveTransferCommand command) {
        Map<String, Object> closeout = requireCloseout(closeoutId, true);
        projectAccessChecker.checkAccess(longValue(closeout.get("project_id")), "登记竣工档案移交");
        requireStage(closeout, Set.of("WARRANTY_RELEASED"), "全部质保责任解除后才能移交竣工档案");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO closeout_archive_transfer(id,tenant_id,closeout_id,project_id,transfer_code,transfer_date,
                     recipient_organization,recipient_name,archive_location,transfer_scope,status,version,
                     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), closeoutId, closeout.get("project_id"), command.transferCode().trim(),
                    command.transferDate(), command.recipientOrganization().trim(), command.recipientName().trim(),
                    command.archiveLocation().trim(), command.transferScope().trim(), user(), user(), command.remark());
        } catch (DuplicateKeyException e) {
            throw error("CLOSEOUT_ARCHIVE_DUPLICATE", "同一收尾主线只能存在一张档案移交单，且移交编号不可重复");
        }
        return archiveTransfer(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> acceptArchiveTransfer(Long id) {
        Map<String, Object> archive = requireArchiveTransfer(id, true);
        projectAccessChecker.checkAccess(longValue(archive.get("project_id")), "确认竣工档案接收");
        if (!"DRAFT".equals(string(archive.get("status"))))
            throw error("CLOSEOUT_ARCHIVE_STATE_INVALID", "只有草稿档案移交单可以确认接收");
        requireFile("CLOSEOUT_ARCHIVE_TRANSFER", id, "ARCHIVE_TRANSFER_LIST", "确认档案接收前必须上传签收清单");
        if (jdbc.update("""
                UPDATE closeout_archive_transfer SET status='ACCEPTED',accepted_by=?,accepted_at=CURRENT_TIMESTAMP,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'
                """, user(), user(), id, tenant()) != 1) throw concurrent();
        jdbc.update("UPDATE project_closeout SET status='READY_TO_CLOSE',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='WARRANTY_RELEASED'",
                user(), archive.get("closeout_id"), tenant());
        return archiveTransfer(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> closeProject(Long closeoutId, CloseProjectCommand command) {
        Map<String, Object> closeout = requireCloseout(closeoutId, true);
        Long projectId = longValue(closeout.get("project_id"));
        projectAccessChecker.checkAccess(projectId, "关闭项目");
        requireStage(closeout, Set.of("READY_TO_CLOSE"), "档案签收完成后才能关闭项目");
        Integer openReceivables = jdbc.queryForObject("SELECT COUNT(*) FROM account_receivable WHERE tenant_id=? AND settlement_id=? AND outstanding_amount>0 AND deleted_flag=0",
                Integer.class, tenant(), closeout.get("final_owner_settlement_id"));
        if (openReceivables != null && openReceivables > 0)
            throw error("CLOSEOUT_RECEIVABLES_OPEN", "竣工结算仍有未回收应收款");
        Integer openDefects = jdbc.queryForObject("SELECT COUNT(*) FROM closeout_defect WHERE tenant_id=? AND closeout_id=? AND status<>'CLOSED' AND deleted_flag=0",
                Integer.class, tenant(), closeoutId);
        if (openDefects != null && openDefects > 0)
            throw error("CLOSEOUT_DEFECTS_OPEN", "仍有未关闭缺陷");
        Integer activeContracts = jdbc.queryForObject("SELECT COUNT(*) FROM ct_contract WHERE tenant_id=? AND project_id=? AND contract_status NOT IN('SETTLED','TERMINATED') AND deleted_flag=0",
                Integer.class, tenant(), projectId);
        if (activeContracts != null && activeContracts > 0)
            throw error("CLOSEOUT_CONTRACTS_OPEN", "项目仍有未结清合同，不能关闭");
        Integer runningWorkflows = jdbc.queryForObject("SELECT COUNT(*) FROM wf_instance WHERE tenant_id=? AND project_id=? AND instance_status='RUNNING'",
                Integer.class, tenant(), projectId);
        if (runningWorkflows != null && runningWorkflows > 0)
            throw error("CLOSEOUT_WORKFLOWS_RUNNING", "项目仍有运行中的审批流程，不能关闭");
        if (jdbc.update("""
                UPDATE pm_project SET status='CLOSED',actual_end_date=?,remark=CONCAT(COALESCE(remark,''),?),
                 updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status IN('ACTIVE','SUSPENDED')
                """, command.actualCompletionDate(), "\n项目收尾关闭：" + command.reason().trim(), user(), projectId, tenant()) != 1)
            throw error("CLOSEOUT_PROJECT_STATE_INVALID", "项目状态已变化，无法关闭");
        jdbc.update("""
                UPDATE project_closeout SET status='CLOSED',actual_completion_date=?,closed_by=?,closed_at=CURRENT_TIMESTAMP,
                 version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?
                """, command.actualCompletionDate(), user(), user(), closeoutId, tenant());
        return trace(closeoutId);
    }

    public Map<String, Object> trace(Long closeoutId) {
        Map<String, Object> closeout = requireCloseout(closeoutId, false);
        projectAccessChecker.checkAccess(longValue(closeout.get("project_id")), "追溯项目竣工收尾");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closeout", closeout);
        result.put("project", queryOne("SELECT * FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "PROJECT_NOT_FOUND", "项目不存在", closeout.get("project_id"), tenant()));
        result.put("sectionAcceptances", jdbc.queryForList("""
                SELECT a.*,w.task_code,w.task_name,q.inspection_code,q.inspection_date,q.conclusion quality_conclusion
                FROM closeout_section_acceptance a JOIN project_wbs_task w ON w.id=a.wbs_task_id
                JOIN qs_inspection_record q ON q.id=a.quality_inspection_id
                WHERE a.tenant_id=? AND a.closeout_id=? AND a.deleted_flag=0 ORDER BY a.acceptance_date,a.id
                """, tenant(), closeoutId));
        List<Map<String, Object>> finals = jdbc.queryForList("SELECT * FROM closeout_final_acceptance WHERE tenant_id=? AND closeout_id=? AND deleted_flag=0",
                tenant(), closeoutId);
        result.put("finalAcceptances", finals);
        List<Object> approvalIds = finals.stream().map(r -> r.get("approval_instance_id")).filter(v -> v != null).toList();
        result.put("approvalRecords", approvalIds.isEmpty() ? List.of() : jdbc.queryForList(
                "SELECT * FROM wf_record WHERE tenant_id=? AND instance_id IN(" + placeholders(approvalIds.size()) + ") ORDER BY created_at,id",
                args(tenant(), approvalIds)));
        Long settlementId = longValueNullable(closeout.get("final_owner_settlement_id"));
        result.put("finalSettlement", settlementId == null ? null : findOne("SELECT * FROM owner_settlement WHERE id=? AND tenant_id=?", settlementId, tenant()));
        result.put("receivables", settlementId == null ? List.of() : jdbc.queryForList("SELECT * FROM account_receivable WHERE tenant_id=? AND settlement_id=? AND deleted_flag=0 ORDER BY receivable_type,id",
                tenant(), settlementId));
        result.put("collectionAllocations", settlementId == null ? List.of() : jdbc.queryForList("""
                SELECT a.*,c.collection_code,c.external_txn_no,c.collected_at,c.status collection_status,r.receivable_type
                FROM collection_allocation a JOIN collection_record c ON c.id=a.collection_id
                JOIN account_receivable r ON r.id=a.receivable_id
                WHERE a.tenant_id=? AND r.settlement_id=? ORDER BY c.collected_at,a.id
                """, tenant(), settlementId));
        result.put("warranties", jdbc.queryForList("SELECT * FROM closeout_warranty WHERE tenant_id=? AND closeout_id=? AND deleted_flag=0 ORDER BY created_at,id", tenant(), closeoutId));
        result.put("defects", jdbc.queryForList("SELECT * FROM closeout_defect WHERE tenant_id=? AND closeout_id=? AND deleted_flag=0 ORDER BY created_at,id", tenant(), closeoutId));
        result.put("archiveTransfers", jdbc.queryForList("SELECT * FROM closeout_archive_transfer WHERE tenant_id=? AND closeout_id=? AND deleted_flag=0 ORDER BY created_at,id", tenant(), closeoutId));
        return result;
    }

    private void validateFinalAcceptanceReadiness(Long closeoutId, Long projectId) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM project_wbs_task WHERE tenant_id=? AND project_id=? AND deleted_flag=0", Integer.class, tenant(), projectId);
        if (total == null || total == 0) throw error("CLOSEOUT_WBS_REQUIRED", "项目必须建立WBS并完成分部分项验收");
        Integer incomplete = jdbc.queryForObject("SELECT COUNT(*) FROM project_wbs_task WHERE tenant_id=? AND project_id=? AND status<>'COMPLETED' AND deleted_flag=0", Integer.class, tenant(), projectId);
        if (incomplete != null && incomplete > 0) throw error("CLOSEOUT_WBS_INCOMPLETE", "仍有未完工WBS任务，不能发起竣工验收");
        Integer missing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_wbs_task w WHERE w.tenant_id=? AND w.project_id=? AND w.deleted_flag=0
                 AND NOT EXISTS(SELECT 1 FROM closeout_section_acceptance a WHERE a.tenant_id=w.tenant_id
                   AND a.closeout_id=? AND a.wbs_task_id=w.id AND a.status='ACCEPTED' AND a.deleted_flag=0)
                """, Integer.class, tenant(), projectId, closeoutId);
        if (missing != null && missing > 0) throw error("CLOSEOUT_SECTION_ACCEPTANCE_INCOMPLETE", "仍有WBS任务未完成分部分项验收");
        Integer openQuality = jdbc.queryForObject("SELECT COUNT(*) FROM qs_issue WHERE tenant_id=? AND project_id=? AND status<>'CLOSED' AND deleted_flag=0", Integer.class, tenant(), projectId);
        if (openQuality != null && openQuality > 0) throw error("CLOSEOUT_QUALITY_ISSUES_OPEN", "仍有未关闭质量安全问题，不能发起竣工验收");
    }

    private void requireMutable(Map<String, Object> closeout) {
        if (Set.of("CLOSED", "READY_TO_CLOSE").contains(string(closeout.get("status"))))
            throw error("CLOSEOUT_IMMUTABLE", "项目收尾已锁定，不能修改");
    }

    private void requireStage(Map<String, Object> closeout, Set<String> allowed, String message) {
        if (!allowed.contains(string(closeout.get("status")))) throw error("CLOSEOUT_STAGE_INVALID", message);
    }

    private void requireFile(String businessType, Long businessId, String documentType, String message) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_file WHERE tenant_id=? AND business_type=? AND business_id=?
                 AND document_type=? AND virus_scan_status='CLEAN' AND deleted_flag=0
                """, Integer.class, tenant(), businessType, businessId, documentType);
        if (count == null || count == 0) throw error("CLOSEOUT_ATTACHMENT_REQUIRED", message);
    }

    private Map<String, Object> requireProject(Long id, boolean lock) {
        return queryOne("SELECT * FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "PROJECT_NOT_FOUND", "项目不存在", id, tenant());
    }

    private Map<String, Object> requireCloseout(Long id, boolean lock) {
        return queryOne("SELECT * FROM project_closeout WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "CLOSEOUT_NOT_FOUND", "项目收尾主档不存在", id, tenant());
    }

    private Map<String, Object> requireSection(Long id, boolean lock) {
        return queryOne("SELECT * FROM closeout_section_acceptance WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "CLOSEOUT_SECTION_NOT_FOUND", "分部分项验收不存在", id, tenant());
    }

    private Map<String, Object> requireFinalAcceptance(Long id, boolean lock) {
        return queryOne("SELECT * FROM closeout_final_acceptance WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "CLOSEOUT_FINAL_ACCEPTANCE_NOT_FOUND", "竣工验收不存在", id, tenant());
    }

    private Map<String, Object> requireWarranty(Long id, boolean lock) {
        return queryOne("SELECT * FROM closeout_warranty WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "CLOSEOUT_WARRANTY_NOT_FOUND", "质保责任不存在", id, tenant());
    }

    private Map<String, Object> requireDefect(Long id, boolean lock) {
        return queryOne("SELECT * FROM closeout_defect WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "CLOSEOUT_DEFECT_NOT_FOUND", "缺陷责任不存在", id, tenant());
    }

    private Map<String, Object> requireArchiveTransfer(Long id, boolean lock) {
        return queryOne("SELECT * FROM closeout_archive_transfer WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""),
                "CLOSEOUT_ARCHIVE_NOT_FOUND", "档案移交单不存在", id, tenant());
    }

    private Map<String, Object> closeout(Long id) { return requireCloseout(id, false); }
    private Map<String, Object> sectionAcceptance(Long id) { return requireSection(id, false); }
    private Map<String, Object> finalAcceptance(Long id) { return requireFinalAcceptance(id, false); }
    private Map<String, Object> warranty(Long id) { return requireWarranty(id, false); }
    private Map<String, Object> defect(Long id) { return requireDefect(id, false); }
    private Map<String, Object> archiveTransfer(Long id) { return requireArchiveTransfer(id, false); }

    private Map<String, Object> queryOne(String sql, String code, String message, Object... args) {
        try { return jdbc.queryForMap(sql, args); }
        catch (EmptyResultDataAccessException e) { throw error(code, message); }
    }

    private Map<String, Object> findOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
    private Object[] args(Object first, List<?> values) {
        Object[] args = new Object[values.size() + 1]; args[0] = first;
        for (int i = 0; i < values.size(); i++) args[i + 1] = values.get(i);
        return args;
    }

    private Long tenant() { return UserContext.getCurrentTenantId(); }
    private Long user() { return UserContext.getCurrentUserId(); }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private Long longValue(Object value) { return value instanceof Number n ? n.longValue() : Long.valueOf(value.toString()); }
    private Long longValueNullable(Object value) { return value == null ? null : longValue(value); }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal d ? d : new BigDecimal(value.toString()); }
    private LocalDate localDate(Object value) { return value instanceof LocalDate d ? d : value instanceof Date d ? d.toLocalDate() : LocalDate.parse(value.toString()); }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private BusinessException concurrent() { return error("CLOSEOUT_CONCURRENT_MODIFICATION", "业务状态已变化，请刷新后重试"); }
}
