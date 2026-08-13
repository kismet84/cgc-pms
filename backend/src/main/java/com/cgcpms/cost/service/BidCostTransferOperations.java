package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostSubjectV2Service.BidTransferRequestCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.TransferCommand;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class BidCostTransferOperations extends CostSubjectV2Support {

    private final ObjectProvider<WorkflowEngine> workflowEngineProvider;

    BidCostTransferOperations(JdbcTemplate jdbc, ProjectAccessChecker projectAccessChecker,
                              ObjectProvider<WorkflowEngine> workflowEngineProvider) {
        super(jdbc, projectAccessChecker);
        this.workflowEngineProvider = workflowEngineProvider;
    }

    List<Map<String, Object>> bidTransferRequests() {
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        if (projectIds.isEmpty()) return List.of();
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId());
        parameters.addAll(projectIds);
        return jdbc.queryForList("""
                SELECT r.id,r.request_code requestCode,r.bid_cost_id bidCostId,b.bid_code bidCode,
                       r.project_id projectId,p.project_code projectCode,p.project_name projectName,
                       r.target_id targetId,t.version_no targetVersionNo,t.version_name targetVersionName,
                       r.mapping_version_id mappingVersionId,r.total_amount totalAmount,
                       r.status,r.approval_instance_id approvalInstanceId,r.final_transfer_id finalTransferId,
                       r.created_at createdAt,r.remark
                FROM bid_cost_target_transfer_request r
                LEFT JOIN bid_cost b ON b.tenant_id=r.tenant_id AND b.id=r.bid_cost_id
                LEFT JOIN pm_project p ON p.tenant_id=r.tenant_id AND p.id=r.project_id
                LEFT JOIN cost_target t ON t.tenant_id=r.tenant_id AND t.id=r.target_id
                WHERE r.tenant_id=? AND r.deleted_flag=0 AND r.project_id IN (%s)
                ORDER BY r.created_at DESC,r.id DESC
                """.formatted(placeholders(projectIds)), parameters.toArray());
    }

    Map<String, Object> createBidTransferRequest(BidTransferRequestCommand command) {
        requireText(command.idempotencyKey(), "幂等键不能为空");
        requireProject(command.projectId());
        Map<String, Object> bid = one("SELECT id,project_id,bid_status FROM bid_cost WHERE tenant_id=? AND id=? AND deleted_flag=0", command.bidCostId());
        if (!"WON".equals(bid.get("bid_status")) || !Objects.equals(longValue(bid.get("project_id")), command.projectId())) {
            throw new BusinessException("BID_COST_NOT_WON", "仅已中标且绑定当前项目的投标成本可以转入");
        }
        requireNoCompetingBidTransfer(command.bidCostId(), command.targetId(), null);
        Map<String, Object> target = one("SELECT id,project_id,approval_status,is_active FROM cost_target WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE", command.targetId());
        if (!Objects.equals(longValue(target.get("project_id")), command.projectId())) {
            throw new BusinessException("COST_TARGET_PROJECT_MISMATCH", "目标成本不属于中标项目");
        }
        if (!List.of("DRAFT", "REJECTED").contains(String.valueOf(target.get("approval_status")))
                || intValue(target.get("is_active")) == 1) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "投标成本仅可转入草稿或驳回且未生效的目标成本版本");
        }
        requireMappingVersion(command.mappingVersionId(), "ACTIVE");
        List<Map<String, Object>> sourceItems = jdbc.queryForList("""
                SELECT c.id,c.cost_subject_id,c.amount_without_tax,m.target_subject_id
                FROM cost_item c JOIN cost_subject_mapping_item m ON m.tenant_id=c.tenant_id
                  AND m.mapping_version_id=? AND m.source_subject_id=c.cost_subject_id
                WHERE c.tenant_id=? AND c.source_id=? AND c.source_type IN ('BID_COST','BID_COST_TRANSFERRED')
                  AND c.deleted_flag=0 AND c.cost_status<>'WRITE_OFF' AND m.target_subject_id IS NOT NULL
                """, command.mappingVersionId(), tenantId(), command.bidCostId());
        if (sourceItems.isEmpty()) throw new BusinessException("BID_COST_MAPPING_MISSING", "投标成本没有可转入的末级科目映射");
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : sourceItems) {
            Long targetSubjectId = longValue(row.get("target_subject_id"));
            requireSubject(targetSubjectId, true);
            BigDecimal transferred = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.amount),0) FROM bid_cost_target_transfer_line l
                    JOIN bid_cost_target_transfer h ON h.id=l.transfer_id AND h.tenant_id=l.tenant_id
                    WHERE l.tenant_id=? AND h.target_id=? AND l.source_cost_item_id=?
                    """, BigDecimal.class, tenantId(), command.targetId(), longValue(row.get("id")));
            if (transferred != null && transferred.signum() != 0) {
                throw new BusinessException("BID_COST_TRANSFER_DUPLICATE", "同一投标成本事实在当前目标成本版本中已转入");
            }
            total = total.add(money(row.get("amount_without_tax")));
        }
        if (total.signum() <= 0) throw new BusinessException("BID_COST_TRANSFER_AMOUNT_INVALID", "可转入投标成本必须大于零");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO bid_cost_target_transfer_request
                    (id,tenant_id,request_code,bid_cost_id,project_id,target_id,mapping_version_id,idempotency_key,
                     total_amount,status,version,created_by,updated_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,'DRAFT',0,?,?,?)
                    """, id, tenantId(), "BCTRQ-" + id, command.bidCostId(), command.projectId(), command.targetId(),
                    command.mappingVersionId(), command.idempotencyKey().trim(), total, userId(), userId(), command.remark());
            for (Map<String, Object> row : sourceItems) {
                jdbc.update("""
                        INSERT INTO bid_cost_target_transfer_request_line
                        (id,tenant_id,request_id,source_cost_item_id,source_subject_id,target_subject_id,amount)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), id, longValue(row.get("id")),
                        longValue(row.get("cost_subject_id")), longValue(row.get("target_subject_id")),
                        money(row.get("amount_without_tax")));
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("BID_COST_TRANSFER_REQUEST_DUPLICATE", "转入申请幂等键重复", ex);
        }
        return bidTransferRequest(id);
    }

    Map<String, Object> submitBidTransferRequest(Long id) {
        Map<String, Object> request = bidTransferRequestForUpdate(id);
        String status = String.valueOf(request.get("status"));
        if (!List.of("DRAFT", "REJECTED", "WITHDRAWN").contains(status)) {
            throw new BusinessException("BID_COST_TRANSFER_REQUEST_NOT_SUBMITTABLE", "仅草稿、驳回或撤回申请可以提交");
        }
        requireProject(longValue(request.get("projectId")));
        Map<String, Object> target = one("SELECT id,project_id,approval_status,is_active FROM cost_target " +
                        "WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE",
                longValue(request.get("targetId")));
        if (!Objects.equals(longValue(target.get("project_id")), longValue(request.get("projectId")))
                || !List.of("DRAFT", "REJECTED").contains(String.valueOf(target.get("approval_status")))
                || intValue(target.get("is_active")) == 1) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "目标成本已进入审批或生效，不能提交投标成本移交");
        }
        requireNoCompetingBidTransfer(longValue(request.get("bidCostId")),
                longValue(request.get("targetId")), id);
        Long instanceId = longValue(request.get("approvalInstanceId"));
        if (instanceId == null) {
            workflowEngineProvider.getObject().submitBidCostTargetTransfer(userId(), UserContext.getCurrentUsername(), tenantId(),
                    WorkflowBusinessTypes.BID_COST_TARGET_TRANSFER, id,
                    "投标成本移交 " + request.get("requestCode"), money(request.get("totalAmount")),
                    longValue(request.get("projectId")), null, null, null, null);
        } else {
            workflowEngineProvider.getObject().resubmitBidCostTargetTransfer(
                    instanceId, userId(), UserContext.getCurrentUsername());
        }
        return bidTransferRequest(id);
    }

    Map<String, Object> bidTransferRequest(Long id) {
        return one("""
                SELECT id,request_code requestCode,bid_cost_id bidCostId,project_id projectId,
                       target_id targetId,mapping_version_id mappingVersionId,total_amount totalAmount,
                       status,approval_instance_id approvalInstanceId,final_transfer_id finalTransferId,
                       created_at createdAt,remark
                FROM bid_cost_target_transfer_request WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, id);
    }

    void markBidTransferRequestSubmitted(Long id, Long instanceId) {
        Map<String, Object> request = bidTransferRequest(id);
        requireWorkflowAmount(instanceId, WorkflowBusinessTypes.BID_COST_TARGET_TRANSFER,
                id, money(request.get("totalAmount")));
        int updated = jdbc.update("""
                UPDATE bid_cost_target_transfer_request
                SET status='SUBMITTED',approval_instance_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND deleted_flag=0 AND status IN ('DRAFT','REJECTED','WITHDRAWN')
                  AND (approval_instance_id IS NULL OR approval_instance_id=?)
                """, instanceId, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("BID_COST_TRANSFER_REQUEST_STATE_INVALID", "投标成本移交申请状态已变化");
    }

    void markBidTransferRequestRejected(Long id, Long instanceId, String status) {
        if (!List.of("REJECTED", "WITHDRAWN").contains(status)) throw new IllegalArgumentException("unsupported status");
        int updated = jdbc.update("""
                UPDATE bid_cost_target_transfer_request
                SET status=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND deleted_flag=0 AND status='SUBMITTED' AND approval_instance_id=?
                """, status, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("BID_COST_TRANSFER_REQUEST_STATE_INVALID", "投标成本移交申请状态已变化");
    }

    Long postBidTransferRequest(Long requestId, Long instanceId) {
        Map<String, Object> request = one("""
                SELECT id,bid_cost_id,project_id,target_id,mapping_version_id,idempotency_key,total_amount,
                       status,approval_instance_id,final_transfer_id,remark
                FROM bid_cost_target_transfer_request WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, requestId);
        if ("POSTED".equals(request.get("status"))) return longValue(request.get("final_transfer_id"));
        if (!"SUBMITTED".equals(request.get("status"))
                || !Objects.equals(longValue(request.get("approval_instance_id")), instanceId)) {
            throw new BusinessException("BID_COST_TRANSFER_REQUEST_STATE_INVALID", "投标成本移交申请未处于当前审批中");
        }
        requireApprovedWorkflow(instanceId, WorkflowBusinessTypes.BID_COST_TARGET_TRANSFER, requestId);
        Long projectId = longValue(request.get("project_id"));
        Long targetId = longValue(request.get("target_id"));
        requireProject(projectId);
        Map<String, Object> target = one("SELECT id,project_id,approval_status,is_active FROM cost_target WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE", targetId);
        if (!Objects.equals(longValue(target.get("project_id")), projectId)
                || !List.of("DRAFT", "REJECTED").contains(String.valueOf(target.get("approval_status")))
                || intValue(target.get("is_active")) == 1) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "目标成本在审批期间已变为不可编辑状态");
        }
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT source_cost_item_id,source_subject_id,target_subject_id,amount
                FROM bid_cost_target_transfer_request_line WHERE tenant_id=? AND request_id=? ORDER BY id
                """, tenantId(), requestId);
        if (lines.isEmpty()) throw new BusinessException("BID_COST_TRANSFER_REQUEST_LINES_MISSING", "投标成本移交申请缺少快照明细");
        for (Map<String, Object> line : lines) {
            BigDecimal transferred = jdbc.queryForList("""
                    SELECT l.amount FROM bid_cost_target_transfer_line l
                    JOIN bid_cost_target_transfer h ON h.id=l.transfer_id AND h.tenant_id=l.tenant_id
                    WHERE l.tenant_id=? AND h.target_id=? AND l.source_cost_item_id=?
                    ORDER BY l.id FOR UPDATE
                    """, BigDecimal.class, tenantId(), targetId, longValue(line.get("source_cost_item_id")))
                    .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (transferred.signum() != 0) {
                throw new BusinessException("BID_COST_TRANSFER_DUPLICATE", "审批期间投标成本事实已被其他申请转入");
            }
        }
        Long finalId = IdWorker.getId();
        BigDecimal total = money(request.get("total_amount"));
        try {
            jdbc.update("""
                    INSERT INTO bid_cost_target_transfer
                    (id,tenant_id,bid_cost_id,project_id,target_id,mapping_version_id,transfer_code,idempotency_key,total_amount,
                     status,approval_instance_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,'POSTED',?,?,?)
                    """, finalId, tenantId(), longValue(request.get("bid_cost_id")), projectId, targetId,
                    longValue(request.get("mapping_version_id")), "BCT-" + finalId, request.get("idempotency_key"),
                    total, instanceId, userId(), request.get("remark"));
            for (Map<String, Object> line : lines) {
                Long targetSubjectId = longValue(line.get("target_subject_id"));
                BigDecimal amount = money(line.get("amount"));
                jdbc.update("""
                        INSERT INTO bid_cost_target_transfer_line
                        (id,tenant_id,transfer_id,source_cost_item_id,source_subject_id,target_subject_id,amount)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), finalId, longValue(line.get("source_cost_item_id")),
                        longValue(line.get("source_subject_id")), targetSubjectId, amount);
                upsertTargetItem(targetId, projectId, targetSubjectId, amount);
            }
            jdbc.update("""
                    UPDATE cost_target SET total_target_amount=total_target_amount+?,total_bid_cost_amount=total_bid_cost_amount+?,
                        total_responsibility_amount=total_responsibility_amount+?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=?
                    """, total, total, total, userId(), tenantId(), targetId);
            if (jdbc.update("""
                    UPDATE bid_cost_target_transfer_request
                    SET status='POSTED',final_transfer_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=? AND status='SUBMITTED' AND approval_instance_id=?
                    """, finalId, userId(), tenantId(), requestId, instanceId) != 1) {
                throw new BusinessException("BID_COST_TRANSFER_REQUEST_STATE_INVALID", "投标成本移交申请终态写入失败");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("BID_COST_TRANSFER_DUPLICATE", "投标成本移交申请已处理或事实重复", ex);
        }
        return finalId;
    }

    List<Map<String, Object>> transfers() {
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        if (projectIds.isEmpty()) return List.of();
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId());
        parameters.addAll(projectIds);
        return jdbc.queryForList("""
                SELECT t.*,b.bid_project_name,ct.version_no,COUNT(l.id) line_count
                FROM bid_cost_target_transfer t
                JOIN bid_cost b ON b.id=t.bid_cost_id AND b.tenant_id=t.tenant_id
                JOIN cost_target ct ON ct.id=t.target_id AND ct.tenant_id=t.tenant_id
                LEFT JOIN bid_cost_target_transfer_line l ON l.transfer_id=t.id AND l.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND t.project_id IN (%s)
                GROUP BY t.id,b.bid_project_name,ct.version_no ORDER BY t.posted_at DESC
                """.formatted(placeholders(projectIds)), parameters.toArray());
    }

    /** businessId is the workflow's bid_cost_id, not a transfer row id. */
    Map<String, Object> bidCostTransferDetail(Long businessId) {
        Map<String, Object> main = one("""
                SELECT t.id,t.bid_cost_id bidCostId,b.bid_code bidCode,b.bid_project_name bidProjectName,
                 t.project_id projectId,p.project_code projectCode,p.project_name projectName,
                 t.target_id targetId,ct.version_no targetVersionNo,ct.version_name targetVersionName,
                 t.mapping_version_id mappingVersionId,m.version_code mappingVersionCode,m.version_name mappingVersionName,
                 t.transfer_code transferCode,t.total_amount totalAmount,t.status,t.posted_at postedAt,t.remark
                FROM bid_cost_target_transfer t
                JOIN bid_cost b ON b.id=t.bid_cost_id AND b.tenant_id=t.tenant_id AND b.deleted_flag=0
                JOIN pm_project p ON p.id=t.project_id AND p.tenant_id=t.tenant_id AND p.deleted_flag=0
                JOIN cost_target ct ON ct.id=t.target_id AND ct.tenant_id=t.tenant_id AND ct.deleted_flag=0
                JOIN cost_subject_mapping_version m ON m.id=t.mapping_version_id AND m.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND t.bid_cost_id=? AND t.reversal_of_id IS NULL
                ORDER BY t.posted_at DESC,t.id DESC LIMIT 1
                """, businessId);
        requireProject(longValue(main.get("projectId")));
        return transferDetail(main);
    }

    /** businessId is the original transfer id used by the reversal workflow. */
    Map<String, Object> bidCostTransferReversalDetail(Long businessId) {
        Map<String, Object> main = one("""
                SELECT r.id,r.reversal_of_id originalTransferId,o.transfer_code originalTransferCode,
                 r.bid_cost_id bidCostId,b.bid_code bidCode,b.bid_project_name bidProjectName,
                 r.project_id projectId,p.project_code projectCode,p.project_name projectName,
                 r.target_id targetId,ct.version_no targetVersionNo,ct.version_name targetVersionName,
                 r.mapping_version_id mappingVersionId,m.version_code mappingVersionCode,m.version_name mappingVersionName,
                 r.transfer_code transferCode,r.total_amount totalAmount,r.status,r.posted_at postedAt,r.remark
                FROM bid_cost_target_transfer r
                JOIN bid_cost_target_transfer o ON o.id=r.reversal_of_id AND o.tenant_id=r.tenant_id
                JOIN bid_cost b ON b.id=r.bid_cost_id AND b.tenant_id=r.tenant_id AND b.deleted_flag=0
                JOIN pm_project p ON p.id=r.project_id AND p.tenant_id=r.tenant_id AND p.deleted_flag=0
                JOIN cost_target ct ON ct.id=r.target_id AND ct.tenant_id=r.tenant_id AND ct.deleted_flag=0
                JOIN cost_subject_mapping_version m ON m.id=r.mapping_version_id AND m.tenant_id=r.tenant_id
                WHERE r.tenant_id=? AND r.reversal_of_id=?
                """, businessId);
        requireProject(longValue(main.get("projectId")));
        return transferDetail(main);
    }

    Long transferBidCost(TransferCommand command) {
        requireText(command.idempotencyKey(), "幂等键不能为空");
        requireProject(command.projectId());
        requireApprovedWorkflow(command.approvalInstanceId(), "BID_COST_TARGET_TRANSFER", command.bidCostId());
        Map<String, Object> bid = one("SELECT id,project_id,bid_status FROM bid_cost WHERE tenant_id=? AND id=?", command.bidCostId());
        if (!"WON".equals(bid.get("bid_status")) || !Objects.equals(longValue(bid.get("project_id")), command.projectId())) {
            throw new BusinessException("BID_COST_NOT_WON", "仅已中标且绑定当前项目的投标成本可以转入");
        }
        Map<String, Object> target = one("SELECT id,project_id,approval_status,is_active FROM cost_target WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE", command.targetId());
        if (!Objects.equals(longValue(target.get("project_id")), command.projectId())) throw new BusinessException("COST_TARGET_PROJECT_MISMATCH", "目标成本不属于中标项目");
        if (!List.of("DRAFT", "REJECTED").contains(String.valueOf(target.get("approval_status"))) || intValue(target.get("is_active")) == 1) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "投标成本仅可转入草稿或驳回且未生效的目标成本版本");
        }
        requireMappingVersion(command.mappingVersionId(), "ACTIVE");
        List<Map<String, Object>> sourceItems = jdbc.queryForList("""
                SELECT c.id,c.cost_subject_id,c.amount_without_tax,m.target_subject_id
                FROM cost_item c JOIN cost_subject_mapping_item m ON m.tenant_id=c.tenant_id
                  AND m.mapping_version_id=? AND m.source_subject_id=c.cost_subject_id
                WHERE c.tenant_id=? AND c.source_id=? AND c.source_type IN ('BID_COST','BID_COST_TRANSFERRED')
                  AND c.deleted_flag=0 AND c.cost_status<>'WRITE_OFF' AND m.target_subject_id IS NOT NULL
                """, command.mappingVersionId(), tenantId(), command.bidCostId());
        if (sourceItems.isEmpty()) throw new BusinessException("BID_COST_MAPPING_MISSING", "投标成本没有可转入的末级科目映射");
        for (Map<String, Object> row : sourceItems) {
            requireSubject(longValue(row.get("target_subject_id")), true);
            BigDecimal transferred = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.amount),0) FROM bid_cost_target_transfer_line l
                    JOIN bid_cost_target_transfer h ON h.id=l.transfer_id AND h.tenant_id=l.tenant_id
                    WHERE l.tenant_id=? AND h.target_id=? AND l.source_cost_item_id=?
                    """, BigDecimal.class, tenantId(), command.targetId(), longValue(row.get("id")));
            if (transferred != null && transferred.signum() != 0) {
                throw new BusinessException("BID_COST_TRANSFER_DUPLICATE", "同一投标成本事实在当前目标成本版本中已转入");
            }
        }
        BigDecimal total = sourceItems.stream().map(row -> money(row.get("amount_without_tax"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) throw new BusinessException("BID_COST_TRANSFER_AMOUNT_INVALID", "可转入投标成本必须大于零");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO bid_cost_target_transfer
                    (id,tenant_id,bid_cost_id,project_id,target_id,mapping_version_id,transfer_code,idempotency_key,total_amount,
                     status,approval_instance_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,'POSTED',?,?,?)
                    """, id, tenantId(), command.bidCostId(), command.projectId(), command.targetId(), command.mappingVersionId(),
                    "BCT-" + id, command.idempotencyKey().trim(), total, command.approvalInstanceId(), userId(), command.remark());
            for (Map<String, Object> row : sourceItems) {
                Long targetSubjectId = longValue(row.get("target_subject_id"));
                BigDecimal amount = money(row.get("amount_without_tax"));
                jdbc.update("""
                        INSERT INTO bid_cost_target_transfer_line
                        (id,tenant_id,transfer_id,source_cost_item_id,source_subject_id,target_subject_id,amount)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), id, longValue(row.get("id")), longValue(row.get("cost_subject_id")), targetSubjectId, amount);
                upsertTargetItem(command.targetId(), command.projectId(), targetSubjectId, amount);
            }
            jdbc.update("""
                    UPDATE cost_target SET total_target_amount=total_target_amount+?,total_bid_cost_amount=total_bid_cost_amount+?,
                        total_responsibility_amount=total_responsibility_amount+?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=?
                    """, total, total, total, userId(), tenantId(), command.targetId());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("BID_COST_TRANSFER_DUPLICATE", "同一幂等键或投标成本事实已转入", ex);
        }
        return id;
    }

    Long reverseBidTransfer(Long originalId, Long approvalInstanceId, String idempotencyKey, String remark) {
        requireText(idempotencyKey, "幂等键不能为空");
        Map<String, Object> original = one("""
                SELECT id,bid_cost_id,project_id,target_id,mapping_version_id,total_amount,status
                FROM bid_cost_target_transfer WHERE tenant_id=? AND id=? AND reversal_of_id IS NULL
                """, originalId);
        if (!"POSTED".equals(original.get("status"))) throw new BusinessException("BID_COST_TRANSFER_NOT_REVERSIBLE", "仅原始已过账转入可冲销");
        requireProject(longValue(original.get("project_id")));
        requireApprovedWorkflow(approvalInstanceId, "BID_COST_TARGET_TRANSFER_REVERSAL", originalId);
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT source_cost_item_id,source_subject_id,target_subject_id,amount
                FROM bid_cost_target_transfer_line WHERE tenant_id=? AND transfer_id=? ORDER BY id
                """, tenantId(), originalId);
        Long reversalId = IdWorker.getId();
        BigDecimal originalTotal = money(original.get("total_amount"));
        try {
            jdbc.update("""
                    INSERT INTO bid_cost_target_transfer
                    (id,tenant_id,bid_cost_id,project_id,target_id,mapping_version_id,transfer_code,idempotency_key,total_amount,
                     status,approval_instance_id,reversal_of_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,'REVERSED',?,?,?,?)
                    """, reversalId, tenantId(), longValue(original.get("bid_cost_id")), longValue(original.get("project_id")),
                    longValue(original.get("target_id")), longValue(original.get("mapping_version_id")), "BCTR-" + reversalId,
                    idempotencyKey.trim(), originalTotal.negate(), approvalInstanceId, originalId, userId(), remark);
            for (Map<String, Object> line : lines) {
                BigDecimal amount = money(line.get("amount"));
                Long targetSubjectId = longValue(line.get("target_subject_id"));
                int updated = jdbc.update("""
                        UPDATE cost_target_item SET target_amount=target_amount-?,bid_cost_amount=bid_cost_amount-?,
                            responsibility_amount=responsibility_amount-?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                        WHERE tenant_id=? AND target_id=? AND cost_subject_id=? AND deleted_flag=0
                          AND target_amount>=? AND bid_cost_amount>=? AND responsibility_amount>=?
                        """, amount, amount, amount, userId(), tenantId(), longValue(original.get("target_id")), targetSubjectId,
                        amount, amount, amount);
                if (updated != 1) throw new BusinessException("BID_COST_REVERSAL_TARGET_CONFLICT", "目标成本已变化，无法安全冲销投标转入");
                jdbc.update("""
                        INSERT INTO bid_cost_target_transfer_line
                        (id,tenant_id,transfer_id,source_cost_item_id,source_subject_id,target_subject_id,amount)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), reversalId, longValue(line.get("source_cost_item_id")),
                        longValue(line.get("source_subject_id")), targetSubjectId, amount.negate());
            }
            int headerUpdated = jdbc.update("""
                    UPDATE cost_target SET total_target_amount=total_target_amount-?,total_bid_cost_amount=total_bid_cost_amount-?,
                        total_responsibility_amount=total_responsibility_amount-?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=? AND total_target_amount>=? AND total_bid_cost_amount>=? AND total_responsibility_amount>=?
                    """, originalTotal, originalTotal, originalTotal, userId(), tenantId(), longValue(original.get("target_id")),
                    originalTotal, originalTotal, originalTotal);
            if (headerUpdated != 1) throw new BusinessException("BID_COST_REVERSAL_TARGET_CONFLICT", "目标成本总额已变化，无法安全冲销");
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("BID_COST_TRANSFER_ALREADY_REVERSED", "该转入已冲销或幂等键重复", ex);
        }
        return reversalId;
    }

    void upsertTargetItem(Long targetId, Long projectId, Long subjectId, BigDecimal amount) {
        int updated = jdbc.update("""
                UPDATE cost_target_item SET target_amount=target_amount+?,bid_cost_amount=bid_cost_amount+?,
                    responsibility_amount=responsibility_amount+?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND target_id=? AND cost_subject_id=? AND deleted_flag=0
                """, amount, amount, amount, userId(), tenantId(), targetId, subjectId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO cost_target_item
                    (id,tenant_id,target_id,project_id,cost_subject_id,target_amount,bid_cost_amount,responsibility_amount,
                     sort_order,created_by,created_at,updated_at,deleted_flag,remark)
                    VALUES (?,?,?,?,?,?,?,?,999,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'投标成本V2转入')
                    """, IdWorker.getId(), tenantId(), targetId, projectId, subjectId, amount, amount, amount, userId());
        }
    }

    Map<String, Object> transferDetail(Map<String, Object> main) {
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT l.id,l.source_subject_id sourceSubjectId,ss.subject_code sourceSubjectCode,
                 ss.subject_name sourceSubjectName,l.target_subject_id targetSubjectId,
                 ts.subject_code targetSubjectCode,ts.subject_name targetSubjectName,l.amount
                FROM bid_cost_target_transfer_line l
                JOIN cost_subject ss ON ss.id=l.source_subject_id AND ss.tenant_id=l.tenant_id AND ss.deleted_flag=0
                JOIN cost_subject ts ON ts.id=l.target_subject_id AND ts.tenant_id=l.tenant_id AND ts.deleted_flag=0
                WHERE l.tenant_id=? AND l.transfer_id=? ORDER BY ss.subject_code,l.id
                """, tenantId(), main.get("id"));
        if (items.isEmpty()) throw new BusinessException("BUSINESS_SOURCE_NOT_FOUND", "业务来源不存在或不可用");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("main", main);
        result.put("items", items);
        return result;
    }

    Map<String, Object> bidTransferRequestForUpdate(Long id) {
        return one("""
                SELECT id,request_code requestCode,bid_cost_id bidCostId,project_id projectId,
                       target_id targetId,mapping_version_id mappingVersionId,total_amount totalAmount,
                       status,approval_instance_id approvalInstanceId,final_transfer_id finalTransferId,
                       created_at createdAt,remark
                FROM bid_cost_target_transfer_request
                WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, id);
    }

    void requireNoCompetingBidTransfer(Long bidCostId, Long targetId, Long excludedId) {
        List<Long> competing = jdbc.queryForList("""
                SELECT id FROM bid_cost_target_transfer_request
                WHERE tenant_id=? AND bid_cost_id=? AND target_id=? AND deleted_flag=0
                  AND status IN ('DRAFT','SUBMITTED') AND (? IS NULL OR id<>?)
                ORDER BY id FOR UPDATE
                """, Long.class, tenantId(), bidCostId, targetId, excludedId, excludedId);
        if (!competing.isEmpty()) {
            throw new BusinessException("BID_COST_TRANSFER_REQUEST_ACTIVE", "同一投标成本与目标版本已有活动移交申请");
        }
    }

}
