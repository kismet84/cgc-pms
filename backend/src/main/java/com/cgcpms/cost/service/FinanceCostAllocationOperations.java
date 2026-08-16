package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.service.CostSubjectV2Service.AllocationLine;
import com.cgcpms.cost.service.CostSubjectV2Service.FinanceAllocationCommand;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.cost.strategy.CostSubjectResolver;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class FinanceCostAllocationOperations extends CostSubjectV2Support {

    private final ObjectProvider<WorkflowEngine> workflowEngineProvider;
    private final CostSubjectResolver costSubjectResolver;
    private final CostFactLineageResolver costFactLineageResolver;
    private final AccountingPeriodGuard accountingPeriodGuard;

    FinanceCostAllocationOperations(JdbcTemplate jdbc, ProjectAccessChecker projectAccessChecker,
                                    CostSubjectResolver costSubjectResolver,
                                    CostFactLineageResolver costFactLineageResolver,
                                    AccountingPeriodGuard accountingPeriodGuard,
                                    ObjectProvider<WorkflowEngine> workflowEngineProvider) {
        super(jdbc, projectAccessChecker);
        this.costSubjectResolver = costSubjectResolver;
        this.costFactLineageResolver = costFactLineageResolver;
        this.accountingPeriodGuard = accountingPeriodGuard;
        this.workflowEngineProvider = workflowEngineProvider;
    }

    List<Map<String, Object>> financeAllocationRequests() {
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        if (projectIds.isEmpty()) return List.of();
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId());
        parameters.addAll(projectIds);
        parameters.addAll(projectIds);
        return jdbc.queryForList("""
                SELECT r.id,r.request_code requestCode,r.project_id projectId,
                       p.project_code projectCode,p.project_name projectName,
                       r.source_type sourceType,r.source_id sourceId,
                       COALESCE(e.entry_code,x.expense_code) sourceCode,
                       r.source_amount sourceAmount,r.allocation_basis allocationBasis,
                       r.accounting_period accountingPeriod,r.cost_subject_id costSubjectId,
                       s.subject_code costSubjectCode,s.subject_name costSubjectName,
                       r.status,r.approval_instance_id approvalInstanceId,
                       r.final_batch_id finalBatchId,r.created_at createdAt,r.remark
                FROM finance_cost_allocation_request r
                LEFT JOIN pm_project p ON p.tenant_id=r.tenant_id AND p.id=r.project_id
                LEFT JOIN cost_subject s ON s.tenant_id=r.tenant_id AND s.id=r.cost_subject_id
                LEFT JOIN accounting_entry_line l ON r.source_type='ACCOUNTING_ENTRY_LINE'
                  AND l.tenant_id=r.tenant_id AND l.id=r.source_id
                LEFT JOIN accounting_entry e ON e.tenant_id=r.tenant_id AND e.id=l.entry_id
                LEFT JOIN expense_application x ON r.source_type='EXPENSE_APPLICATION'
                  AND x.tenant_id=r.tenant_id AND x.id=r.source_id
                WHERE r.tenant_id=? AND r.deleted_flag=0 AND r.project_id IN (%s)
                  AND NOT EXISTS (
                    SELECT 1 FROM finance_cost_allocation_request_line l
                    WHERE l.tenant_id=r.tenant_id AND l.request_id=r.id AND l.project_id NOT IN (%s)
                  )
                ORDER BY r.created_at DESC,r.id DESC
                """.formatted(placeholders(projectIds), placeholders(projectIds)), parameters.toArray());
    }

    Map<String, Object> createFinanceAllocationRequest(FinanceAllocationCommand command) {
        validateFinanceAllocationCommand(command);
        accountingPeriodGuard.assertWritable(allocationBusinessDate(command.accountingPeriod()));
        requireProjectsOpenForNormalCostGovernance(
                command.lines().stream().map(AllocationLine::projectId).toList());
        requireSubject(command.costSubjectId(), true);
        requireNoCompetingFinanceAllocation(command.sourceType(), command.sourceId(), null);
        BigDecimal sourceAmount = sourceAmount(command.sourceType(), command.sourceId());
        BigDecimal allocatedBefore = jdbc.queryForObject("""
                SELECT COALESCE(SUM(source_amount),0)
                FROM finance_cost_allocation_batch WHERE tenant_id=? AND source_type=? AND source_id=?
                """, BigDecimal.class, tenantId(), command.sourceType(), command.sourceId());
        BigDecimal remaining = sourceAmount.subtract(allocatedBefore == null ? BigDecimal.ZERO : allocatedBefore);
        if (remaining.signum() <= 0) throw new BusinessException("FINANCE_COST_ALREADY_ALLOCATED", "来源财务费用已全部分摊");
        BigDecimal basisTotal = command.lines().stream().map(AllocationLine::basisValue)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (basisTotal.signum() <= 0) throw new BusinessException("FINANCE_COST_BASIS_INVALID", "分摊依据合计必须大于零");
        List<BigDecimal> amounts = calculateAllocation(remaining, command.lines(), basisTotal);
        Long id = IdWorker.getId();
        String sourceSnapshotHash = sourceSnapshotHash(command.sourceType(), command.sourceId(), remaining);
        try {
            jdbc.update("""
                    INSERT INTO finance_cost_allocation_request
                    (id,tenant_id,request_code,project_id,source_type,source_id,source_amount,allocation_basis,
                     accounting_period,cost_subject_id,override_reason,source_snapshot_hash,idempotency_key,status,
                     version,created_by,updated_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,?,?)
                    """, id, tenantId(), "FCARQ-" + id, command.lines().get(0).projectId(), command.sourceType(),
                    command.sourceId(), remaining, command.allocationBasis(), command.accountingPeriod(),
                    command.costSubjectId(), command.remark(), sourceSnapshotHash, "FCARQ-IDEMP-" + id,
                    userId(), userId(), command.remark());
            for (int index = 0; index < command.lines().size(); index++) {
                AllocationLine line = command.lines().get(index);
                requireScope(line.projectId(), command.costSubjectId());
                CostSubjectResolver.Decision decision = costSubjectResolver.resolveForFact(
                        tenantId(), line.projectId(), command.sourceType(), command.allocationBasis(),
                        command.sourceId(), line.projectId(), null,
                        allocationBusinessDate(command.accountingPeriod()));
                boolean overridden = !Objects.equals(decision.costSubjectId(), command.costSubjectId());
                if (overridden && (command.remark() == null || command.remark().isBlank())) {
                    throw new BusinessException("FINANCE_COST_OVERRIDE_REASON_REQUIRED", "覆盖自动匹配科目必须填写原因");
                }
                Long overrideId = decision.overrideId();
                if (overridden && overrideId != null) {
                    throw new BusinessException("FINANCE_COST_OVERRIDE_CONFLICT", "该财务来源已有不同的有效科目覆盖");
                }
                Long requestLineId = IdWorker.getId();
                jdbc.update("""
                        INSERT INTO finance_cost_allocation_request_line
                        (id,tenant_id,request_id,project_id,basis_value,matched_cost_subject_id,selected_cost_subject_id,
                         mapping_version_id,assignment_rule_id,classification_override_id,classification_status,
                         override_reason,source_snapshot_hash,allocated_amount)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, requestLineId, tenantId(), id, line.projectId(), line.basisValue(), decision.costSubjectId(),
                        command.costSubjectId(), decision.mappingVersionId(), decision.assignmentRuleId(), overrideId,
                        overridden || "OVERRIDDEN".equals(decision.classificationStatus()) ? "OVERRIDDEN" : "CLASSIFIED",
                        overridden ? command.remark().trim() : null, sourceSnapshotHash, amounts.get(index));
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_DUPLICATE", "财务分摊申请幂等键或项目明细重复", ex);
        }
        return financeAllocationRequest(id);
    }

    Map<String, Object> submitFinanceAllocationRequest(Long id) {
        Map<String, Object> request = financeAllocationRequestForUpdate(id);
        requireCurrentUserCreated(request.get("createdBy"), "财务费用分摊申请");
        String status = String.valueOf(request.get("status"));
        if (!List.of("DRAFT", "REJECTED", "WITHDRAWN").contains(status)) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_NOT_SUBMITTABLE", "仅草稿、驳回或撤回申请可以提交");
        }
        sourceAmount(String.valueOf(request.get("sourceType")), longValue(request.get("sourceId")));
        validateFrozenRequest(id, request);
        requireNoCompetingFinanceAllocation(String.valueOf(request.get("sourceType")),
                longValue(request.get("sourceId")), id);
        jdbc.queryForList("""
                SELECT DISTINCT project_id FROM finance_cost_allocation_request_line
                WHERE tenant_id=? AND request_id=?
                """, Long.class, tenantId(), id).forEach(this::requireProjectOpenForNormalCostGovernance);
        Long instanceId = longValue(request.get("approvalInstanceId"));
        if (instanceId == null) {
            workflowEngineProvider.getObject().submitFinanceCostAllocation(userId(), UserContext.getCurrentUsername(), tenantId(),
                    WorkflowBusinessTypes.FINANCE_COST_ALLOCATION, id,
                    "财务成本分摊 " + request.get("requestCode"), money(request.get("sourceAmount")),
                    longValue(request.get("projectId")), null, null, null, null);
        } else {
            workflowEngineProvider.getObject().resubmitFinanceCostAllocation(
                    instanceId, userId(), UserContext.getCurrentUsername());
        }
        return financeAllocationRequest(id);
    }

    Map<String, Object> cancelFinanceAllocationRequest(Long id) {
        Map<String, Object> request = financeAllocationRequestForUpdate(id);
        requireCurrentUserCreated(request.get("createdBy"), "财务费用分摊申请");
        jdbc.queryForList("""
                SELECT DISTINCT project_id FROM finance_cost_allocation_request_line
                WHERE tenant_id=? AND request_id=?
                """, Long.class, tenantId(), id).forEach(this::requireProject);
        if (!"DRAFT".equals(request.get("status")) || request.get("approvalInstanceId") != null) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_NOT_CANCELLABLE", "仅未提交审批的草稿申请可取消");
        }
        int updated = jdbc.update("""
                UPDATE finance_cost_allocation_request
                SET status='CANCELLED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND deleted_flag=0 AND status='DRAFT'
                  AND approval_instance_id IS NULL
                """, userId(), tenantId(), id);
        if (updated != 1) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_STATE_INVALID", "财务分摊申请状态已变化");
        }
        return financeAllocationRequest(id);
    }

    Map<String, Object> financeAllocationRequest(Long id) {
        return one("""
                SELECT id,request_code requestCode,project_id projectId,source_type sourceType,source_id sourceId,
                       source_amount sourceAmount,allocation_basis allocationBasis,accounting_period accountingPeriod,
                       cost_subject_id costSubjectId,status,approval_instance_id approvalInstanceId,
                       final_batch_id finalBatchId,source_snapshot_hash sourceSnapshotHash,
                       created_by createdBy,created_at createdAt,remark
                FROM finance_cost_allocation_request WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, id);
    }

    void markFinanceAllocationRequestSubmitted(Long id, Long instanceId) {
        Map<String, Object> request = financeAllocationRequest(id);
        requireWorkflowAmount(instanceId, WorkflowBusinessTypes.FINANCE_COST_ALLOCATION,
                id, money(request.get("sourceAmount")));
        int updated = jdbc.update("""
                UPDATE finance_cost_allocation_request
                SET status='SUBMITTED',approval_instance_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND deleted_flag=0 AND status IN ('DRAFT','REJECTED','WITHDRAWN')
                  AND (approval_instance_id IS NULL OR approval_instance_id=?)
                """, instanceId, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_STATE_INVALID", "财务分摊申请状态已变化");
    }

    void markFinanceAllocationRequestRejected(Long id, Long instanceId, String status) {
        if (!List.of("REJECTED", "WITHDRAWN").contains(status)) throw new IllegalArgumentException("unsupported status");
        int updated = jdbc.update("""
                UPDATE finance_cost_allocation_request
                SET status=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND deleted_flag=0 AND status='SUBMITTED' AND approval_instance_id=?
                """, status, userId(), tenantId(), id, instanceId);
        if (updated != 1) throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_STATE_INVALID", "财务分摊申请状态已变化");
    }

    Long postFinanceAllocationRequest(Long requestId, Long instanceId) {
        Map<String, Object> request = one("""
                SELECT id,project_id,source_type,source_id,source_amount,allocation_basis,accounting_period,
                       cost_subject_id,source_snapshot_hash,idempotency_key,status,approval_instance_id,final_batch_id,remark
                FROM finance_cost_allocation_request WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, requestId);
        if ("POSTED".equals(request.get("status"))) return longValue(request.get("final_batch_id"));
        if (!"SUBMITTED".equals(request.get("status"))
                || !Objects.equals(longValue(request.get("approval_instance_id")), instanceId)) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_STATE_INVALID", "财务分摊申请未处于当前审批中");
        }
        requireApprovedWorkflow(instanceId, WorkflowBusinessTypes.FINANCE_COST_ALLOCATION, requestId);
        LocalDate businessDate = allocationBusinessDate(String.valueOf(request.get("accounting_period")));
        accountingPeriodGuard.assertWritable(businessDate);
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT id,project_id,basis_value,allocated_amount,matched_cost_subject_id,selected_cost_subject_id,
                       mapping_version_id,assignment_rule_id,classification_override_id,classification_status,
                       override_reason,source_snapshot_hash
                FROM finance_cost_allocation_request_line WHERE tenant_id=? AND request_id=? ORDER BY id
                """, tenantId(), requestId);
        if (lines.isEmpty()) throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_LINES_MISSING", "财务分摊申请缺少快照明细");
        List<Long> projectIds = lines.stream().map(line -> longValue(line.get("project_id"))).toList();
        requireProjectsOpenForNormalCostGovernance(projectIds);
        List<Long> subjectIds = new ArrayList<>();
        subjectIds.add(longValue(request.get("cost_subject_id")));
        lines.stream().map(line -> longValue(line.get("selected_cost_subject_id"))).forEach(subjectIds::add);
        requireLeafSubjects(subjectIds);
        for (Map<String, Object> line : lines) {
            Long projectId = longValue(line.get("project_id"));
            requireScope(projectId, longValue(line.get("selected_cost_subject_id")));
        }
        BigDecimal sourceTotal = sourceAmount(String.valueOf(request.get("source_type")),
                longValue(request.get("source_id")));
        BigDecimal alreadyAllocated = jdbc.queryForList("""
                SELECT source_amount FROM finance_cost_allocation_batch
                WHERE tenant_id=? AND source_type=? AND source_id=?
                ORDER BY id FOR UPDATE
                """, BigDecimal.class, tenantId(), request.get("source_type"), request.get("source_id"))
                .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = money(request.get("source_amount"));
        BigDecimal lineTotal = lines.stream().map(line -> money(line.get("allocated_amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lineTotal.compareTo(total) != 0) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_NOT_CONSERVED", "分摊明细合计与来源金额不一致");
        }
        BigDecimal remaining = sourceTotal.subtract(alreadyAllocated);
        String currentSnapshotHash = sourceSnapshotHash(String.valueOf(request.get("source_type")),
                longValue(request.get("source_id")), remaining);
        if (!Objects.equals(currentSnapshotHash, request.get("source_snapshot_hash"))) {
            throw new BusinessException("FINANCE_COST_SOURCE_DRIFT", "财务费用来源在审批期间已变化，请重新创建申请");
        }
        if (lines.stream().anyMatch(line -> line.get("selected_cost_subject_id") == null
                || "UNCLASSIFIED".equals(line.get("classification_status")))) {
            throw new BusinessException("FINANCE_COST_UNCLASSIFIED", "分摊明细仍有待归类科目，禁止过账");
        }
        if (remaining.compareTo(total) != 0) {
            throw new BusinessException("FINANCE_COST_ALREADY_ALLOCATED", "审批期间来源财务费用已被其他申请分摊");
        }
        Long batchId = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO finance_cost_allocation_batch
                    (id,tenant_id,batch_code,source_type,source_id,source_amount,allocation_basis,accounting_period,
                     cost_subject_id,idempotency_key,status,approval_instance_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'POSTED',?,?,?)
                    """, batchId, tenantId(), "FCA-" + batchId, request.get("source_type"), request.get("source_id"),
                    total, request.get("allocation_basis"), request.get("accounting_period"),
                    request.get("cost_subject_id"), request.get("idempotency_key"), instanceId, userId(), request.get("remark"));
            for (Map<String, Object> line : lines) {
                Long projectId = longValue(line.get("project_id"));
                BigDecimal amount = money(line.get("allocated_amount"));
                requireActualFactSubject(longValue(line.get("selected_cost_subject_id")));
                Long overrideId = longValue(line.get("classification_override_id"));
                if ("OVERRIDDEN".equals(line.get("classification_status")) && overrideId == null) {
                    overrideId = IdWorker.getId();
                    jdbc.update("""
                            INSERT INTO cost_classification_override
                            (id,tenant_id,source_type,source_id,source_item_id,matched_cost_subject_id,
                             override_cost_subject_id,mapping_version_id,assignment_rule_id,override_reason,
                             status,version,created_by)
                            VALUES (?,?,?,?,?,?,?,?,?,?, 'ACTIVE',0,?)
                            """, overrideId, tenantId(), request.get("source_type"), request.get("source_id"), projectId,
                            line.get("matched_cost_subject_id"), line.get("selected_cost_subject_id"),
                            line.get("mapping_version_id"), line.get("assignment_rule_id"),
                            line.get("override_reason"), userId());
                    jdbc.update("""
                            UPDATE finance_cost_allocation_request_line SET classification_override_id=?
                            WHERE tenant_id=? AND id=? AND classification_override_id IS NULL
                            """, overrideId, tenantId(), line.get("id"));
                }
                Long costItemId = IdWorker.getId();
                jdbc.update("""
                        INSERT INTO cost_item
                        (id,tenant_id,project_id,cost_subject_id,classification_status,classification_business_category,mapping_version_id,
                         assignment_rule_id,original_cost_subject_id,classification_override_id,cost_type,
                         amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,cost_date,
                         cost_status,generated_flag,created_by,created_at,updated_at,deleted_flag,remark)
                        VALUES (?,?,?,?,?,?,?,?,?,?, ?,?,?,?,'FINANCE_COST_ALLOCATION',?,?,?,
                         'CONFIRMED',1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?)
                        """, costItemId, tenantId(), projectId, line.get("selected_cost_subject_id"),
                        line.get("classification_status"), request.get("allocation_basis"),
                        line.get("mapping_version_id"), line.get("assignment_rule_id"),
                        line.get("matched_cost_subject_id"), overrideId, "FINANCE", amount,
                        BigDecimal.ZERO, amount, batchId, line.get("id"), businessDate,
                        userId(), request.get("remark"));
                jdbc.update("""
                        INSERT INTO finance_cost_allocation_line
                        (id,tenant_id,batch_id,project_id,basis_value,allocated_amount,cost_item_id)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), batchId, projectId, line.get("basis_value"), amount, costItemId);
            }
            if (jdbc.update("""
                    UPDATE finance_cost_allocation_request
                    SET status='POSTED',final_batch_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=? AND status='SUBMITTED' AND approval_instance_id=?
                    """, batchId, userId(), tenantId(), requestId, instanceId) != 1) {
                throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_STATE_INVALID", "财务分摊申请终态写入失败");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_DUPLICATE", "财务分摊申请已处理或事实重复", ex);
        }
        return batchId;
    }

    private void requireActualFactSubject(Long subjectId) {
        requireSubject(subjectId, true);
        Integer disabledRules = jdbc.queryForObject("""
                SELECT COUNT(*) FROM overhead_allocation_rule
                WHERE tenant_id=? AND cost_subject_id=? AND status='DISABLE' AND deleted_flag=0
                """, Integer.class, tenantId(), subjectId);
        if (disabledRules != null && disabledRules > 0) {
            throw new BusinessException("OVERHEAD_RULE_DISABLED_FOR_COST",
                    "该间接费科目的分摊规则已停用，重新启用后方可生成新成本事实");
        }
    }

    List<Map<String, Object>> financeAllocations() {
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        if (projectIds.isEmpty()) return List.of();
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId());
        parameters.addAll(projectIds);
        return jdbc.queryForList("""
                SELECT b.*,s.subject_code,s.subject_name,COUNT(l.id) line_count
                FROM finance_cost_allocation_batch b
                JOIN cost_subject s ON s.id=b.cost_subject_id AND s.tenant_id=b.tenant_id
                LEFT JOIN finance_cost_allocation_line l ON l.batch_id=b.id AND l.tenant_id=b.tenant_id
                WHERE b.tenant_id=?
                  AND EXISTS (
                    SELECT 1 FROM finance_cost_allocation_line present_line
                    WHERE present_line.tenant_id=b.tenant_id AND present_line.batch_id=b.id
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM finance_cost_allocation_line denied_line
                    WHERE denied_line.tenant_id=b.tenant_id AND denied_line.batch_id=b.id
                      AND denied_line.project_id NOT IN (%s)
                  )
                GROUP BY b.id,s.subject_code,s.subject_name ORDER BY b.posted_at DESC
                """.formatted(placeholders(projectIds)), parameters.toArray());
    }

    /** businessId is the workflow's source_id, not an allocation batch id. */
    Map<String, Object> financeAllocationDetail(Long businessId) {
        Map<String, Object> main = one("""
                SELECT b.id,b.batch_code batchCode,b.source_type sourceType,b.source_id sourceId,
                 b.source_amount sourceAmount,b.allocation_basis allocationBasis,b.accounting_period accountingPeriod,
                 b.cost_subject_id costSubjectId,s.subject_code subjectCode,s.subject_name subjectName,
                 b.status,b.posted_at postedAt,b.remark
                FROM finance_cost_allocation_batch b
                JOIN cost_subject s ON s.id=b.cost_subject_id AND s.tenant_id=b.tenant_id AND s.deleted_flag=0
                WHERE b.tenant_id=? AND b.source_id=? AND b.reversal_of_id IS NULL
                ORDER BY b.posted_at DESC,b.id DESC LIMIT 1
                """, businessId);
        return financeDetail(main);
    }

    /** businessId is the original allocation batch id used by the reversal workflow. */
    Map<String, Object> financeAllocationReversalDetail(Long businessId) {
        Map<String, Object> main = one("""
                SELECT r.id,r.reversal_of_id originalBatchId,o.batch_code originalBatchCode,
                 r.batch_code batchCode,r.source_type sourceType,r.source_id sourceId,
                 r.source_amount sourceAmount,r.allocation_basis allocationBasis,r.accounting_period accountingPeriod,
                 r.cost_subject_id costSubjectId,s.subject_code subjectCode,s.subject_name subjectName,
                 r.status,r.posted_at postedAt,r.remark
                FROM finance_cost_allocation_batch r
                JOIN finance_cost_allocation_batch o ON o.id=r.reversal_of_id AND o.tenant_id=r.tenant_id
                JOIN cost_subject s ON s.id=r.cost_subject_id AND s.tenant_id=r.tenant_id AND s.deleted_flag=0
                WHERE r.tenant_id=? AND r.reversal_of_id=?
                """, businessId);
        return financeDetail(main);
    }

    Long allocateFinanceCost(FinanceAllocationCommand command) {
        throw new BusinessException("WORKFLOW_REQUIRED", "财务成本分摊必须通过申请、试算和财务负责人审批");
    }

    Long reverseFinanceAllocation(Long originalId, Long approvalInstanceId, String idempotencyKey, String remark) {
        throw new BusinessException("WORKFLOW_REQUIRED", "财务成本分摊冲销必须通过统一成本冲销申请审批");
    }

    Long reverseFinanceAllocationApproved(Long originalId, Long reversalRequestId,
                                          Long approvalInstanceId, String remark) {
        accountingPeriodGuard.assertWritable(LocalDate.now());
        Map<String, Object> original = one("""
                SELECT id,source_type,source_id,source_amount,allocation_basis,accounting_period,cost_subject_id,status
                FROM finance_cost_allocation_batch WHERE tenant_id=? AND id=? AND reversal_of_id IS NULL
                """, originalId);
        if (!"POSTED".equals(original.get("status"))) throw new BusinessException("FINANCE_COST_NOT_REVERSIBLE", "仅原始已过账分摊可冲销");
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT l.id,l.project_id,l.basis_value,l.allocated_amount,l.cost_item_id,
                       ci.cost_subject_id,ci.classification_status,ci.mapping_version_id,ci.assignment_rule_id,
                       ci.original_cost_subject_id,ci.classification_override_id,ci.classification_snapshot_id,
                       ci.tax_amount,ci.amount_without_tax
                FROM finance_cost_allocation_line l
                JOIN cost_item ci ON ci.tenant_id=l.tenant_id AND ci.id=l.cost_item_id AND ci.deleted_flag=0
                WHERE l.tenant_id=? AND l.batch_id=? ORDER BY l.id
                """, tenantId(), originalId);
        lines.stream().map(line -> longValue(line.get("project_id"))).distinct().forEach(this::requireProject);
        for (Map<String, Object> line : lines) {
            CostItem current = costFactLineageResolver.requireCurrentLeaf(
                    tenantId(), longValue(line.get("cost_item_id")));
            if (current.getAmountWithoutTax() == null
                    || current.getAmountWithoutTax().compareTo(money(line.get("allocated_amount"))) != 0) {
                throw new BusinessException("FINANCE_COST_REVERSAL_SOURCE_DRIFT", "财务分摊成本事实金额已变化，不能冲销");
            }
            line.put("current_cost_item_id", current.getId());
            line.put("cost_subject_id", current.getCostSubjectId());
            line.put("classification_status", current.getClassificationStatus());
            line.put("classification_business_category", current.getClassificationBusinessCategory());
            line.put("recognition_role", current.getRecognitionRole());
            line.put("root_source_type", current.getRootSourceType() == null
                    ? current.getSourceType() : current.getRootSourceType());
            line.put("mapping_version_id", current.getMappingVersionId());
            line.put("assignment_rule_id", current.getAssignmentRuleId());
            line.put("original_cost_subject_id", current.getOriginalCostSubjectId());
            line.put("classification_override_id", current.getClassificationOverrideId());
            line.put("classification_snapshot_id", current.getClassificationSnapshotId());
            line.put("cost_type", current.getCostType());
            line.put("amount", current.getAmount());
            line.put("tax_amount", current.getTaxAmount());
            line.put("amount_without_tax", current.getAmountWithoutTax());
        }
        requireApprovedWorkflow(approvalInstanceId, WorkflowBusinessTypes.COST_REVERSAL, reversalRequestId);
        Long reversalId = IdWorker.getId();
        BigDecimal total = money(original.get("source_amount")).negate();
        try {
            jdbc.update("""
                    INSERT INTO finance_cost_allocation_batch
                    (id,tenant_id,batch_code,source_type,source_id,source_amount,allocation_basis,accounting_period,
                     cost_subject_id,idempotency_key,status,approval_instance_id,reversal_of_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'REVERSED',?,?,?,?)
                    """, reversalId, tenantId(), "FCAR-" + reversalId, original.get("source_type"), longValue(original.get("source_id")),
                    total, original.get("allocation_basis"), original.get("accounting_period"), longValue(original.get("cost_subject_id")),
                    "FCAR-IDEMP-" + reversalRequestId, approvalInstanceId, originalId, userId(), remark);
            for (Map<String, Object> line : lines) {
                BigDecimal amount = money(line.get("allocated_amount")).negate();
                Long costItemId = IdWorker.getId();
                jdbc.update("""
                        INSERT INTO cost_item
                        (id,tenant_id,project_id,cost_subject_id,classification_status,classification_business_category,recognition_role,root_source_type,mapping_version_id,
                         assignment_rule_id,original_cost_subject_id,classification_override_id,
                         classification_snapshot_id,original_cost_item_id,cost_type,amount,tax_amount,
                         amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,
                         created_by,created_at,updated_at,deleted_flag,remark)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'FINANCE_COST_ALLOCATION_REVERSAL',?,?,CURRENT_DATE,
                         'CONFIRMED',1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?)
                        """, costItemId, tenantId(), longValue(line.get("project_id")), line.get("cost_subject_id"),
                        "REVERSAL", line.get("classification_business_category"), line.get("recognition_role"), line.get("root_source_type"), line.get("mapping_version_id"), line.get("assignment_rule_id"),
                        line.get("original_cost_subject_id"), line.get("classification_override_id"),
                        line.get("classification_snapshot_id"), line.get("current_cost_item_id"), line.get("cost_type"),
                        money(line.get("amount")).negate(),
                        money(line.get("tax_amount")).negate(), money(line.get("amount_without_tax")).negate(),
                        reversalId, line.get("current_cost_item_id"), userId(), remark);
                jdbc.update("""
                        INSERT INTO finance_cost_allocation_line
                        (id,tenant_id,batch_id,project_id,basis_value,allocated_amount,cost_item_id)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), reversalId, longValue(line.get("project_id")),
                        money(line.get("basis_value")), amount, costItemId);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("FINANCE_COST_ALREADY_REVERSED", "该分摊已冲销或幂等键重复", ex);
        }
        return reversalId;
    }

    Map<String, Object> reconciliation(Long projectId) {
        requireProject(projectId);
        return jdbc.queryForMap("""
                SELECT ? project_id,
                  COALESCE((SELECT SUM(ci.amount_without_tax) FROM cost_item ci
                    JOIN cost_subject cs ON cs.tenant_id=ci.tenant_id AND cs.id=ci.cost_subject_id
                      AND cs.deleted_flag=0 AND cs.account_category='COST'
                    WHERE ci.tenant_id=? AND ci.project_id=? AND ci.deleted_flag=0
                      AND ci.cost_status IN ('CONFIRMED','POSTED')
                      AND ci.classification_status<>'UNCLASSIFIED'
                      AND ci.recognition_role='ACTUAL'),0) actual_cost,
                  COALESCE((SELECT SUM(target_amount) FROM cost_target_item WHERE tenant_id=? AND project_id=? AND deleted_flag=0),0) target_cost,
                  COALESCE((SELECT SUM(l.amount) FROM bid_cost_target_transfer_line l JOIN bid_cost_target_transfer h ON h.id=l.transfer_id WHERE h.tenant_id=? AND h.project_id=?),0) bid_transferred,
                  COALESCE((SELECT SUM(l.allocated_amount) FROM finance_cost_allocation_line l JOIN finance_cost_allocation_batch h ON h.id=l.batch_id WHERE h.tenant_id=? AND l.project_id=?),0) finance_allocated,
                  COALESCE((SELECT COUNT(*) FROM cost_item WHERE tenant_id=? AND project_id=? AND deleted_flag=0
                    AND cost_status IN ('CONFIRMED','POSTED') AND classification_status='UNCLASSIFIED'),0) unclassified_count,
                  COALESCE((SELECT COUNT(*) FROM cost_subject_assignment_rule r WHERE r.tenant_id=? AND r.status='ACTIVE' AND EXISTS (SELECT 1 FROM cost_subject s WHERE s.tenant_id=r.tenant_id AND s.parent_id=r.cost_subject_id AND s.deleted_flag=0)),0) active_non_leaf_rule_count
                """, projectId, tenantId(), projectId, tenantId(), projectId, tenantId(), projectId,
                tenantId(), projectId, tenantId(), projectId, tenantId());
    }

    Map<String, Object> financeDetail(Map<String, Object> main) {
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT l.id,l.project_id projectId,p.project_code projectCode,p.project_name projectName,
                 l.basis_value basisValue,l.allocated_amount allocatedAmount
                FROM finance_cost_allocation_line l
                JOIN pm_project p ON p.id=l.project_id AND p.tenant_id=l.tenant_id AND p.deleted_flag=0
                WHERE l.tenant_id=? AND l.batch_id=? ORDER BY p.project_code,l.id
                """, tenantId(), main.get("id"));
        if (items.isEmpty()) throw new BusinessException("BUSINESS_SOURCE_NOT_FOUND", "业务来源不存在或不可用");
        items.stream().map(item -> longValue(item.get("projectId"))).distinct().forEach(this::requireProject);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("main", main);
        result.put("items", items);
        return result;
    }

    void validateFinanceAllocationCommand(FinanceAllocationCommand command) {
        requireText(command.accountingPeriod(), "会计期间不能为空");
        if (!command.accountingPeriod().matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new BusinessException("FINANCE_COST_PERIOD_INVALID", "会计期间必须为YYYY-MM");
        }
        if (!List.of("DIRECT_PROJECT", "BENEFIT_AMOUNT", "OCCUPIED_DAYS", "CONTRACT_AMOUNT_EXCEPTION")
                .contains(command.allocationBasis())) {
            throw new BusinessException("FINANCE_COST_BASIS_INVALID", "不支持的财务费用分摊依据");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new BusinessException("FINANCE_COST_LINES_EMPTY", "财务费用分摊至少包含一个项目");
        }
        if (command.lines().size() > APPROVAL_DETAIL_ROW_LIMIT) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_LINE_LIMIT_EXCEEDED", "财务费用分摊明细最多1000行，请拆分后提交");
        }
        if (command.lines().stream().anyMatch(line -> line.projectId() == null || line.basisValue() == null
                || line.basisValue().signum() < 0)) {
            throw new BusinessException("FINANCE_COST_BASIS_INVALID", "项目和分摊依据必须有效");
        }
        if (command.lines().stream().map(AllocationLine::projectId).distinct().count() != command.lines().size()) {
            throw new BusinessException("FINANCE_COST_PROJECT_DUPLICATE", "同一分摊批次不能重复选择项目");
        }
        if ("DIRECT_PROJECT".equals(command.allocationBasis()) && command.lines().size() != 1) {
            throw new BusinessException("FINANCE_COST_DIRECT_PROJECT_INVALID", "直接归属只能选择一个项目");
        }
        if ("CONTRACT_AMOUNT_EXCEPTION".equals(command.allocationBasis())
                && (command.remark() == null || command.remark().isBlank())) {
            throw new BusinessException("FINANCE_COST_EXCEPTION_REASON_REQUIRED", "合同额例外分摊必须说明原因");
        }
    }

    private static LocalDate allocationBusinessDate(String accountingPeriod) {
        return YearMonth.parse(accountingPeriod).atEndOfMonth();
    }

    List<BigDecimal> calculateAllocation(BigDecimal total, List<AllocationLine> lines, BigDecimal basisTotal) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            BigDecimal amount = i == lines.size() - 1
                    ? total.subtract(assigned)
                    : total.multiply(lines.get(i).basisValue()).divide(basisTotal, 2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) throw new BusinessException("FINANCE_COST_LINE_AMOUNT_INVALID", "每个项目分摊金额必须大于零");
            result.add(amount);
            assigned = assigned.add(amount);
        }
        return result;
    }

    Map<String, Object> financeAllocationRequestForUpdate(Long id) {
        return one("""
                SELECT id,request_code requestCode,project_id projectId,source_type sourceType,source_id sourceId,
                       source_amount sourceAmount,allocation_basis allocationBasis,accounting_period accountingPeriod,
                       cost_subject_id costSubjectId,status,approval_instance_id approvalInstanceId,
                       final_batch_id finalBatchId,source_snapshot_hash sourceSnapshotHash,
                       created_by createdBy,created_at createdAt,remark
                FROM finance_cost_allocation_request
                WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, id);
    }

    void requireNoCompetingFinanceAllocation(String sourceType, Long sourceId, Long excludedId) {
        List<Long> competing = jdbc.queryForList("""
                SELECT id FROM finance_cost_allocation_request
                WHERE tenant_id=? AND source_type=? AND source_id=? AND deleted_flag=0
                  AND status IN ('DRAFT','SUBMITTED') AND (? IS NULL OR id<>?)
                ORDER BY id FOR UPDATE
                """, Long.class, tenantId(), sourceType, sourceId, excludedId, excludedId);
        if (!competing.isEmpty()) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_REQUEST_ACTIVE", "同一财务来源已有活动分摊申请");
        }
    }

    BigDecimal sourceAmount(String sourceType, Long sourceId) {
        if ("ACCOUNTING_ENTRY_LINE".equals(sourceType)) {
            return money(one("""
                    SELECT l.amount FROM accounting_entry_line l
                    JOIN accounting_entry e ON e.id=l.entry_id AND e.tenant_id=l.tenant_id
                    JOIN cost_subject s ON s.id=l.cost_subject_id AND s.tenant_id=l.tenant_id
                    WHERE l.tenant_id=? AND l.id=? AND e.deleted_flag=0 AND e.entry_status='POSTED'
                      AND e.source_type='MANUAL' AND l.direction='DEBIT'
                      AND s.deleted_flag=0 AND s.status='ENABLE' AND s.account_category='COST'
                    FOR UPDATE
                    """, sourceId).get("amount"));
        }
        if ("EXPENSE_APPLICATION".equals(sourceType)) {
            return money(one("SELECT amount FROM expense_application WHERE tenant_id=? AND id=? AND deleted_flag=0 AND approval_status='APPROVED' FOR UPDATE", sourceId).get("amount"));
        }
        throw new BusinessException("FINANCE_COST_SOURCE_INVALID", "财务费用来源仅支持已过账借方凭证明细或已审批费用申请");
    }

    private void validateFrozenRequest(Long requestId, Map<String, Object> request) {
        BigDecimal sourceTotal = sourceAmount(String.valueOf(request.get("sourceType")), longValue(request.get("sourceId")));
        BigDecimal allocated = jdbc.queryForObject("""
                SELECT COALESCE(SUM(source_amount),0) FROM finance_cost_allocation_batch
                WHERE tenant_id=? AND source_type=? AND source_id=?
                """, BigDecimal.class, tenantId(), request.get("sourceType"), request.get("sourceId"));
        BigDecimal remaining = sourceTotal.subtract(allocated == null ? BigDecimal.ZERO : allocated);
        if (remaining.compareTo(money(request.get("sourceAmount"))) != 0
                || !Objects.equals(sourceSnapshotHash(String.valueOf(request.get("sourceType")),
                longValue(request.get("sourceId")), remaining), request.get("sourceSnapshotHash"))) {
            throw new BusinessException("FINANCE_COST_SOURCE_DRIFT", "财务费用来源金额已变化，请重新创建分摊申请");
        }
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT project_id,allocated_amount,selected_cost_subject_id,classification_status,
                       mapping_version_id,assignment_rule_id,source_snapshot_hash
                FROM finance_cost_allocation_request_line WHERE tenant_id=? AND request_id=?
                """, tenantId(), requestId);
        BigDecimal lineTotal = lines.stream().map(line -> money(line.get("allocated_amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lines.isEmpty() || lineTotal.compareTo(remaining) != 0) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_NOT_CONSERVED", "分摊明细合计与来源金额不一致");
        }
        for (Map<String, Object> line : lines) {
            if (line.get("selected_cost_subject_id") == null || "UNCLASSIFIED".equals(line.get("classification_status"))
                    || !Objects.equals(line.get("source_snapshot_hash"), request.get("sourceSnapshotHash"))) {
                throw new BusinessException("FINANCE_COST_UNCLASSIFIED", "分摊明细归类快照不完整");
            }
            requireProjectOpenForNormalCostGovernance(longValue(line.get("project_id")));
            requireScope(longValue(line.get("project_id")), longValue(line.get("selected_cost_subject_id")));
        }
    }

    private static String sourceSnapshotHash(String sourceType, Long sourceId, BigDecimal amount) {
        try {
            String value = sourceType + "|" + sourceId + "|" + amount.setScale(2, RoundingMode.HALF_UP);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
