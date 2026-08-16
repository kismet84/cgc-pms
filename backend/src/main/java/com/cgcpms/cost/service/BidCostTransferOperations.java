package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.service.CostSubjectV2Service.BidTransferRequestCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.TransferCommand;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class BidCostTransferOperations extends CostSubjectV2Support {

    private final ObjectProvider<WorkflowEngine> workflowEngineProvider;
    private final CostFactLineageResolver costFactLineageResolver;

    BidCostTransferOperations(JdbcTemplate jdbc, ProjectAccessChecker projectAccessChecker,
                              CostFactLineageResolver costFactLineageResolver,
                              ObjectProvider<WorkflowEngine> workflowEngineProvider) {
        super(jdbc, projectAccessChecker);
        this.costFactLineageResolver = costFactLineageResolver;
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
        requireMappingVersion(command.mappingVersionId(), "ACTIVE");
        requireProjectOpenForNormalCostGovernance(command.projectId());
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
        List<Map<String, Object>> sourceItems = currentBidCostItems(
                command.mappingVersionId(), command.bidCostId(), false);
        if (sourceItems.isEmpty()) throw new BusinessException("BID_COST_MAPPING_MISSING", "投标成本没有可转入的末级科目映射");
        if (sourceItems.size() > APPROVAL_DETAIL_ROW_LIMIT) {
            throw new BusinessException("BID_COST_TRANSFER_LINE_LIMIT_EXCEEDED", "投标成本转入明细最多1000行，请拆分后提交");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : sourceItems) {
            Long targetSubjectId = longValue(row.get("target_subject_id"));
            requireSubject(targetSubjectId, true);
            BigDecimal transferred = transferredAmountForRoot(
                    command.targetId(), longValue(row.get("root_cost_item_id")), false);
            if (transferred != null && transferred.signum() != 0) {
                throw new BusinessException("BID_COST_TRANSFER_DUPLICATE", "同一投标成本事实在当前目标成本版本中已转入");
            }
            total = total.add(money(row.get("amount_without_tax")));
        }
        if (total.signum() <= 0) throw new BusinessException("BID_COST_TRANSFER_AMOUNT_INVALID", "可转入投标成本必须大于零");
        Long id = IdWorker.getId();
        String sourceSnapshotHash = sourceSnapshotHash(sourceItems);
        try {
            jdbc.update("""
                    INSERT INTO bid_cost_target_transfer_request
                    (id,tenant_id,request_code,bid_cost_id,project_id,target_id,mapping_version_id,idempotency_key,
                     total_amount,source_snapshot_hash,status,version,created_by,updated_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,?,?)
                    """, id, tenantId(), "BCTRQ-" + id, command.bidCostId(), command.projectId(), command.targetId(),
                    command.mappingVersionId(), "BCTRQ-IDEMP-" + id, total, sourceSnapshotHash,
                    userId(), userId(), command.remark());
            for (Map<String, Object> row : sourceItems) {
                jdbc.update("""
                        INSERT INTO bid_cost_target_transfer_request_line
                        (id,tenant_id,request_id,source_cost_item_id,source_subject_id,target_subject_id,amount,source_snapshot_hash)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), id, longValue(row.get("id")),
                        longValue(row.get("cost_subject_id")), longValue(row.get("target_subject_id")),
                        money(row.get("amount_without_tax")), sourceSnapshotHash);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("BID_COST_TRANSFER_REQUEST_DUPLICATE", "转入申请幂等键重复", ex);
        }
        return bidTransferRequest(id);
    }

    Map<String, Object> submitBidTransferRequest(Long id) {
        Map<String, Object> request = bidTransferRequestForUpdate(id);
        requireCurrentUserCreated(request.get("createdBy"), "投标成本转入申请");
        String status = String.valueOf(request.get("status"));
        if (!List.of("DRAFT", "REJECTED", "WITHDRAWN").contains(status)) {
            throw new BusinessException("BID_COST_TRANSFER_REQUEST_NOT_SUBMITTABLE", "仅草稿、驳回或撤回申请可以提交");
        }
        requireProjectOpenForNormalCostGovernance(longValue(request.get("projectId")));
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
        validateBidTransferSnapshot(request);
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

    Map<String, Object> cancelBidTransferRequest(Long id) {
        Map<String, Object> request = bidTransferRequestForUpdate(id);
        requireCurrentUserCreated(request.get("createdBy"), "投标成本转入申请");
        requireProject(longValue(request.get("projectId")));
        if (!"DRAFT".equals(request.get("status")) || request.get("approvalInstanceId") != null) {
            throw new BusinessException("BID_COST_TRANSFER_REQUEST_NOT_CANCELLABLE", "仅未提交审批的草稿申请可取消");
        }
        int updated = jdbc.update("""
                UPDATE bid_cost_target_transfer_request
                SET status='CANCELLED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND deleted_flag=0 AND status='DRAFT'
                  AND approval_instance_id IS NULL
                """, userId(), tenantId(), id);
        if (updated != 1) {
            throw new BusinessException("BID_COST_TRANSFER_REQUEST_STATE_INVALID", "投标成本移交申请状态已变化");
        }
        return bidTransferRequest(id);
    }

    Map<String, Object> bidTransferRequest(Long id) {
        return one("""
                SELECT id,request_code requestCode,bid_cost_id bidCostId,project_id projectId,
                       target_id targetId,mapping_version_id mappingVersionId,total_amount totalAmount,
                       source_snapshot_hash sourceSnapshotHash,status,approval_instance_id approvalInstanceId,final_transfer_id finalTransferId,
                       created_by createdBy,created_at createdAt,remark
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
                SELECT id,bid_cost_id,project_id,target_id,mapping_version_id,idempotency_key,total_amount,source_snapshot_hash,
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
        requireProjectOpenForNormalCostGovernance(projectId);
        validateBidTransferSnapshot(Map.of(
                "bidCostId", request.get("bid_cost_id"), "mappingVersionId", request.get("mapping_version_id"),
                "sourceSnapshotHash", request.get("source_snapshot_hash"), "totalAmount", request.get("total_amount")));
        Map<String, Object> target = one("SELECT id,project_id,approval_status,is_active FROM cost_target WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE", targetId);
        if (!Objects.equals(longValue(target.get("project_id")), projectId)
                || !List.of("DRAFT", "REJECTED").contains(String.valueOf(target.get("approval_status")))
                || intValue(target.get("is_active")) == 1) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "目标成本在审批期间已变为不可编辑状态");
        }
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT source_cost_item_id,source_subject_id,target_subject_id,amount,source_snapshot_hash
                FROM bid_cost_target_transfer_request_line WHERE tenant_id=? AND request_id=? ORDER BY id
                """, tenantId(), requestId);
        if (lines.isEmpty()) throw new BusinessException("BID_COST_TRANSFER_REQUEST_LINES_MISSING", "投标成本移交申请缺少快照明细");
        BigDecimal lineTotal = lines.stream().map(line -> money(line.get("amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lineTotal.compareTo(money(request.get("total_amount"))) != 0
                || lines.stream().anyMatch(line -> !Objects.equals(line.get("source_snapshot_hash"), request.get("source_snapshot_hash")))) {
            throw new BusinessException("BID_COST_TRANSFER_SOURCE_DRIFT", "投标成本转入快照不完整或金额不守恒");
        }
        for (Map<String, Object> line : lines) {
            BigDecimal transferred = transferredAmountForRoot(targetId,
                    rootCostItemId(longValue(line.get("source_cost_item_id"))), true);
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
        throw new BusinessException("WORKFLOW_REQUIRED", "投标成本转入冲销必须通过统一成本冲销申请审批");
    }

    Long reverseBidTransferApproved(Long originalId, Long reversalRequestId,
                                    Long approvalInstanceId, String remark) {
        Map<String, Object> original = one("""
                SELECT h.id,h.bid_cost_id,h.project_id,h.target_id,h.mapping_version_id,h.total_amount,h.status,
                       t.approval_status targetApprovalStatus,t.is_active targetActive
                FROM bid_cost_target_transfer h
                JOIN cost_target t ON t.tenant_id=h.tenant_id AND t.id=h.target_id AND t.deleted_flag=0
                WHERE h.tenant_id=? AND h.id=? AND h.reversal_of_id IS NULL FOR UPDATE
                """, originalId);
        if (!"POSTED".equals(original.get("status"))) throw new BusinessException("BID_COST_TRANSFER_NOT_REVERSIBLE", "仅原始已过账转入可冲销");
        if (!List.of("DRAFT", "REJECTED").contains(String.valueOf(original.get("targetApprovalStatus")))
                || intValue(original.get("targetActive")) == 1) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "目标成本已审批或生效，须通过新目标版本调整，不能直接冲销转入");
        }
        requireProject(longValue(original.get("project_id")));
        requireApprovedWorkflow(approvalInstanceId, WorkflowBusinessTypes.COST_REVERSAL, reversalRequestId);
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
                    "BCTR-IDEMP-" + reversalRequestId, originalTotal.negate(), approvalInstanceId, originalId, userId(), remark);
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
                       source_snapshot_hash sourceSnapshotHash,
                       status,approval_instance_id approvalInstanceId,final_transfer_id finalTransferId,
                       created_by createdBy,created_at createdAt,remark
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

    private void validateBidTransferSnapshot(Map<String, Object> request) {
        Long mappingVersionId = longValue(request.get("mappingVersionId"));
        Long bidCostId = longValue(request.get("bidCostId"));
        List<Map<String, Object>> current = currentBidCostItems(mappingVersionId, bidCostId, true);
        BigDecimal total = current.stream().map(row -> money(row.get("amount_without_tax")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (current.isEmpty() || total.compareTo(money(request.get("totalAmount"))) != 0
                || !Objects.equals(sourceSnapshotHash(current), request.get("sourceSnapshotHash"))) {
            throw new BusinessException("BID_COST_TRANSFER_SOURCE_DRIFT", "投标成本或科目映射已变化，请重新创建转入申请");
        }
    }

    private static String sourceSnapshotHash(List<Map<String, Object>> items) {
        StringBuilder canonical = new StringBuilder();
        for (Map<String, Object> item : items) {
            canonical.append(item.get("id")).append('|')
                    .append(item.get("root_cost_item_id")).append('|')
                    .append(item.get("cost_subject_id")).append('|')
                    .append(money(item.get("amount_without_tax")).setScale(2)).append('|')
                    .append(item.get("target_subject_id")).append('|')
                    .append(Objects.toString(item.get("cost_status"), "")).append('|')
                    .append(Objects.toString(item.get("updated_at"), "")).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private List<Map<String, Object>> currentBidCostItems(Long mappingVersionId, Long bidCostId,
                                                          boolean lockFacts) {
        List<Map<String, Object>> items = queryCurrentBidCostItems(mappingVersionId, bidCostId);
        for (Map<String, Object> item : items) {
            CostItem leaf = costFactLineageResolver.requireCurrentLeaf(
                    tenantId(), longValue(item.get("root_cost_item_id")));
            if (!Objects.equals(leaf.getId(), longValue(item.get("id")))) {
                throw new BusinessException("BID_COST_TRANSFER_SOURCE_DRIFT", "投标成本重分类链已变化，请重新创建转入申请");
            }
        }
        return lockFacts ? queryCurrentBidCostItems(mappingVersionId, bidCostId) : items;
    }

    private List<Map<String, Object>> queryCurrentBidCostItems(Long mappingVersionId, Long bidCostId) {
        return jdbc.queryForList("""
                WITH RECURSIVE bid_lineage(
                  id,original_cost_item_id,cost_subject_id,amount_without_tax,cost_status,updated_at,
                  source_type,classification_status,adjustment_batch_id,root_cost_item_id
                ) AS (
                  SELECT ci.id,ci.original_cost_item_id,ci.cost_subject_id,ci.amount_without_tax,
                         ci.cost_status,ci.updated_at,ci.source_type,ci.classification_status,
                         ci.adjustment_batch_id,ci.id
                  FROM cost_item ci
                  WHERE ci.tenant_id=? AND ci.source_type='BID_COST' AND ci.source_id=?
                    AND ci.deleted_flag=0
                  UNION ALL
                  SELECT child.id,child.original_cost_item_id,child.cost_subject_id,child.amount_without_tax,
                         child.cost_status,child.updated_at,child.source_type,child.classification_status,
                         child.adjustment_batch_id,parent.root_cost_item_id
                  FROM cost_item child JOIN bid_lineage parent ON child.original_cost_item_id=parent.id
                  WHERE child.tenant_id=? AND child.deleted_flag=0
                )
                SELECT c.id,c.root_cost_item_id,c.cost_subject_id,c.amount_without_tax,c.cost_status,c.updated_at,
                       COALESCE(m.target_subject_id,c.cost_subject_id) target_subject_id
                FROM bid_lineage c
                LEFT JOIN cost_recalculation_batch own_batch
                  ON own_batch.tenant_id=? AND own_batch.id=c.adjustment_batch_id
                LEFT JOIN cost_subject_mapping_item m ON m.tenant_id=?
                  AND m.mapping_version_id=? AND m.source_subject_id=c.cost_subject_id
                WHERE c.source_type IN ('BID_COST','COST_RECALCULATION_POSITIVE')
                  AND c.cost_status IN ('CONFIRMED','POSTED')
                  AND (c.adjustment_batch_id IS NULL OR own_batch.status='POSTED')
                  AND (m.target_subject_id IS NOT NULL
                       OR c.classification_status IN ('CLASSIFIED','OVERRIDDEN','ADJUSTMENT','REVERSAL'))
                  AND NOT EXISTS (
                    SELECT 1 FROM cost_item successor
                    LEFT JOIN cost_recalculation_batch successor_batch
                      ON successor_batch.tenant_id=successor.tenant_id
                     AND successor_batch.id=successor.adjustment_batch_id
                    WHERE successor.tenant_id=? AND successor.original_cost_item_id=c.id
                      AND successor.deleted_flag=0
                      AND (successor.source_type='COST_RECALCULATION_REVERSAL'
                           OR (successor.source_type='COST_RECALCULATION_NEGATIVE'
                               AND successor_batch.status='POSTED')))
                ORDER BY c.id
                """, tenantId(), bidCostId, tenantId(), tenantId(), tenantId(), mappingVersionId, tenantId());
    }

    private Long rootCostItemId(Long costItemId) {
        return costFactLineageResolver.rootId(tenantId(), costItemId);
    }

    private BigDecimal transferredAmountForRoot(Long targetId, Long rootCostItemId, boolean lockLines) {
        String lock = lockLines ? " ORDER BY l.id FOR UPDATE" : " ORDER BY l.id";
        List<BigDecimal> amounts = jdbc.queryForList("""
                WITH RECURSIVE family(id) AS (
                  SELECT id FROM cost_item WHERE tenant_id=? AND id=? AND deleted_flag=0
                  UNION ALL
                  SELECT child.id FROM cost_item child JOIN family parent ON child.original_cost_item_id=parent.id
                  WHERE child.tenant_id=? AND child.deleted_flag=0
                )
                SELECT l.amount FROM bid_cost_target_transfer_line l
                JOIN bid_cost_target_transfer h ON h.id=l.transfer_id AND h.tenant_id=l.tenant_id
                JOIN family f ON f.id=l.source_cost_item_id
                WHERE l.tenant_id=? AND h.target_id=?
                """ + lock, BigDecimal.class, tenantId(), rootCostItemId, tenantId(), tenantId(), targetId);
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
