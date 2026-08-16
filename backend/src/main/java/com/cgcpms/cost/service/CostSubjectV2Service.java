package com.cgcpms.cost.service;

import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.cost.strategy.CostSubjectResolver;
import com.cgcpms.system.role.SystemRoleContract;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CostSubjectV2Service {

    private final JdbcTemplate jdbc;
    private final CostSubjectMappingOperations mappingOperations;
    private final BidCostTransferOperations bidTransferOperations;
    private final FinanceCostAllocationOperations financeAllocationOperations;
    private final CostGovernanceOperations governanceOperations;
    private final CostClassificationGuard classificationGuard;
    private final ObjectProvider<WorkflowEngine> workflowEngineProvider;

    public CostSubjectV2Service(JdbcTemplate jdbc,
                                ProjectAccessChecker projectAccessChecker,
                                CostSubjectResolver costSubjectResolver,
                                CostFactLineageResolver costFactLineageResolver,
                                CostClassificationGuard classificationGuard,
                                AccountingPeriodGuard accountingPeriodGuard,
                                ObjectProvider<WorkflowEngine> workflowEngineProvider) {
        this.jdbc = jdbc;
        this.mappingOperations = new CostSubjectMappingOperations(jdbc, projectAccessChecker);
        this.bidTransferOperations = new BidCostTransferOperations(
                jdbc, projectAccessChecker, costFactLineageResolver, workflowEngineProvider);
        this.financeAllocationOperations = new FinanceCostAllocationOperations(
                jdbc, projectAccessChecker, costSubjectResolver, costFactLineageResolver,
                accountingPeriodGuard, workflowEngineProvider);
        this.governanceOperations = new CostGovernanceOperations(
                jdbc, projectAccessChecker, costSubjectResolver, accountingPeriodGuard, workflowEngineProvider);
        this.classificationGuard = classificationGuard;
        this.workflowEngineProvider = workflowEngineProvider;
    }

    public record MappingItem(Long sourceSubjectId, String targetGroupCode, Long targetSubjectId,
                              String historicalDisplayName, String mappingReason) {}

    public record MappingVersionCommand(String versionCode, String versionName, LocalDate effectiveDate,
                                        String remark, List<MappingItem> items, List<MappingRule> rules) {
        public MappingVersionCommand(String versionCode, String versionName, LocalDate effectiveDate,
                                     String remark, List<MappingItem> items) {
            this(versionCode, versionName, effectiveDate, remark, items, null);
        }
    }

    public record MappingRule(String ruleCode, String sourceType, String businessCategory,
                              Long projectId, Long costSubjectId, Integer priority,
                              LocalDate effectiveFrom, LocalDate effectiveTo, String remark) {}

    public record RuleCommand(String ruleCode, Long mappingVersionId, String sourceType,
                              String businessCategory, Long projectId, Long costSubjectId,
                              Integer priority, LocalDate effectiveFrom, LocalDate effectiveTo,
                              String remark) {}

    public record ScopeCommand(Long projectId, Long costSubjectId, Boolean enabled,
                               LocalDate effectiveFrom, LocalDate effectiveTo, String remark) {}

    public record TransferCommand(Long bidCostId, Long projectId, Long targetId, Long mappingVersionId,
                                  Long approvalInstanceId, String idempotencyKey, String remark) {}

    public record BidTransferRequestCommand(Long bidCostId, Long projectId, Long targetId,
                                            Long mappingVersionId, String idempotencyKey,
                                            String remark) {}

    public record AllocationLine(Long projectId, BigDecimal basisValue) {}

    public record FinanceAllocationCommand(String sourceType, Long sourceId, String allocationBasis,
                                           String accountingPeriod, Long costSubjectId,
                                           Long approvalInstanceId, String idempotencyKey,
                                           String remark, List<AllocationLine> lines) {}

    public record ProjectConfigLine(Long costSubjectId, Boolean enabled,
                                    LocalDate effectiveFrom, LocalDate effectiveTo) {}

    public record ProjectConfigCommand(Long projectId, String reason, List<ProjectConfigLine> lines) {}

    public record RecalculationCommand(Long projectId, Long ruleVersionId, LocalDateTime cutoffAt,
                                       String batchType, String reason, String idempotencyKey) {}

    public record ReversalCommand(String targetType, Long targetId, String reason) {}

    public record ClassificationOverrideCommand(Long caseId, Long snapshotId,
                                                 Long costSubjectId, String reason) {}

    public List<Map<String, Object>> mappingVersions() {
        return mappingOperations.mappingVersions();
    }

    public List<Map<String, Object>> mappingItems(Long versionId) {
        return mappingOperations.mappingItems(versionId);
    }

    public Map<String, Object> mappingVersionDetail(Long versionId) {
        return mappingOperations.mappingVersionDetail(versionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createMappingVersion(MappingVersionCommand command) {
        requireCompanyFinanceOperator();
        return mappingOperations.createMappingVersion(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateInitialPlan() {
        requireCompanyFinanceOperator();
        return mappingOperations.generateInitialPlan();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> validateMappingVersion(Long id) {
        requireCompanyFinanceOperator();
        return mappingOperations.validateMappingVersion(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitMappingVersion(Long id) {
        requireCompanyFinanceOperator();
        mappingOperations.requireRulePlanCreator(id);
        Map<String, Object> version = mappingOperations.mappingVersionDetail(id);
        Map<String, Object> main = castMap(version.get("main"));
        if (!"VALIDATED".equals(main.get("status"))) {
            throw new com.cgcpms.common.exception.BusinessException("COST_RULE_PLAN_NOT_SUBMITTABLE", "仅通过系统校验的方案可提交");
        }
        Long instanceId = valueAsLong(main.get("approvalInstanceId"));
        if (instanceId == null) {
            workflowEngineProvider.getObject().submitCostGovernance(
                    com.cgcpms.auth.context.UserContext.getCurrentUserId(),
                    com.cgcpms.auth.context.UserContext.getCurrentUsername(),
                    com.cgcpms.auth.context.UserContext.getCurrentTenantId(),
                    com.cgcpms.workflow.WorkflowBusinessTypes.COST_RULE_PLAN, id,
                    "成本规则方案 " + main.get("versionCode"), java.math.BigDecimal.ZERO,
                    null, null, String.valueOf(main.get("versionName")), null, null);
        } else {
            workflowEngineProvider.getObject().resubmitCostGovernance(instanceId,
                    com.cgcpms.auth.context.UserContext.getCurrentUserId(),
                    com.cgcpms.auth.context.UserContext.getCurrentUsername());
        }
        return mappingOperations.mappingVersionDetail(id);
    }

    public Map<String, Object> mappingVersionDiff(Long id, Long baseId) {
        return mappingOperations.mappingVersionDiff(id, baseId);
    }

    public Map<String, Object> trialMappingVersion(Long id, String sourceType,
                                                   String businessCategory, Long projectId) {
        return mappingOperations.trialMappingVersion(id, sourceType, businessCategory, projectId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void activateMappingVersion(Long id, Long approvalInstanceId) {
        mappingOperations.activateMappingVersion(id, approvalInstanceId);
    }

    public void markRulePlanSubmitted(Long id, Long instanceId) {
        mappingOperations.markRulePlanSubmitted(id, instanceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveRulePlan(Long id, Long instanceId) {
        mappingOperations.approveRulePlan(id, instanceId);
    }

    public void rejectRulePlan(Long id, Long instanceId, String status) {
        mappingOperations.rejectRulePlan(id, instanceId, status);
    }

    public List<Map<String, Object>> rules() {
        return mappingOperations.rules();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRule(RuleCommand command) {
        requireCompanyFinanceOperator();
        return mappingOperations.createRule(command);
    }

    public Long resolveRule(String sourceType, String businessCategory, Long projectId) {
        return mappingOperations.resolveRule(sourceType, businessCategory, projectId);
    }

    public List<Map<String, Object>> scopes(Long projectId) {
        return mappingOperations.scopes(projectId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long upsertScope(ScopeCommand command) {
        requireCompanyFinanceOperator();
        return mappingOperations.upsertScope(command);
    }

    public Map<String, Object> impact(Long subjectId) {
        return mappingOperations.impact(subjectId);
    }

    public List<Map<String, Object>> bidTransferRequests() {
        return bidTransferOperations.bidTransferRequests();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createBidTransferRequest(BidTransferRequestCommand command) {
        requireCompanyFinanceOperator();
        return bidTransferOperations.createBidTransferRequest(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitBidTransferRequest(Long id) {
        requireCompanyFinanceOperator();
        return bidTransferOperations.submitBidTransferRequest(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelBidTransferRequest(Long id) {
        requireCompanyFinanceOperator();
        return bidTransferOperations.cancelBidTransferRequest(id);
    }

    public Map<String, Object> bidTransferRequest(Long id) {
        return bidTransferOperations.bidTransferRequest(id);
    }

    public void markBidTransferRequestSubmitted(Long id, Long instanceId) {
        bidTransferOperations.markBidTransferRequestSubmitted(id, instanceId);
    }

    public void markBidTransferRequestRejected(Long id, Long instanceId, String status) {
        bidTransferOperations.markBidTransferRequestRejected(id, instanceId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long postBidTransferRequest(Long requestId, Long instanceId) {
        return bidTransferOperations.postBidTransferRequest(requestId, instanceId);
    }

    public List<Map<String, Object>> transfers() {
        return bidTransferOperations.transfers();
    }

    public Map<String, Object> bidCostTransferDetail(Long businessId) {
        return bidTransferOperations.bidCostTransferDetail(businessId);
    }

    public Map<String, Object> bidCostTransferReversalDetail(Long businessId) {
        return bidTransferOperations.bidCostTransferReversalDetail(businessId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long transferBidCost(TransferCommand command) {
        throw new com.cgcpms.common.exception.BusinessException(
                "WORKFLOW_REQUIRED", "投标成本转入必须通过申请、试算和财务负责人审批");
    }

    @Transactional(rollbackFor = Exception.class)
    public Long reverseBidTransfer(Long originalId, Long approvalInstanceId,
                                   String idempotencyKey, String remark) {
        return bidTransferOperations.reverseBidTransfer(
                originalId, approvalInstanceId, idempotencyKey, remark);
    }

    public List<Map<String, Object>> financeAllocationRequests() {
        return financeAllocationOperations.financeAllocationRequests();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createFinanceAllocationRequest(FinanceAllocationCommand command) {
        requireCompanyFinanceOperator();
        return financeAllocationOperations.createFinanceAllocationRequest(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitFinanceAllocationRequest(Long id) {
        requireCompanyFinanceOperator();
        return financeAllocationOperations.submitFinanceAllocationRequest(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelFinanceAllocationRequest(Long id) {
        requireCompanyFinanceOperator();
        return financeAllocationOperations.cancelFinanceAllocationRequest(id);
    }

    public Map<String, Object> financeAllocationRequest(Long id) {
        return financeAllocationOperations.financeAllocationRequest(id);
    }

    public void markFinanceAllocationRequestSubmitted(Long id, Long instanceId) {
        financeAllocationOperations.markFinanceAllocationRequestSubmitted(id, instanceId);
    }

    public void markFinanceAllocationRequestRejected(Long id, Long instanceId, String status) {
        financeAllocationOperations.markFinanceAllocationRequestRejected(id, instanceId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long postFinanceAllocationRequest(Long requestId, Long instanceId) {
        return financeAllocationOperations.postFinanceAllocationRequest(requestId, instanceId);
    }

    public List<Map<String, Object>> financeAllocations() {
        return financeAllocationOperations.financeAllocations();
    }

    public Map<String, Object> financeAllocationDetail(Long businessId) {
        return financeAllocationOperations.financeAllocationDetail(businessId);
    }

    public Map<String, Object> financeAllocationReversalDetail(Long businessId) {
        return financeAllocationOperations.financeAllocationReversalDetail(businessId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long allocateFinanceCost(FinanceAllocationCommand command) {
        return financeAllocationOperations.allocateFinanceCost(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long reverseFinanceAllocation(Long originalId, Long approvalInstanceId,
                                         String idempotencyKey, String remark) {
        return financeAllocationOperations.reverseFinanceAllocation(
                originalId, approvalInstanceId, idempotencyKey, remark);
    }

    public Map<String, Object> reconciliation(Long projectId) {
        return financeAllocationOperations.reconciliation(projectId);
    }

    public Map<String, Object> governanceFormOptions() {
        return governanceOperations.formOptions();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long overrideClassification(ClassificationOverrideCommand command) {
        requireCompanyFinanceOperator();
        if (command == null) {
            throw new BusinessException("COST_CLASSIFICATION_OVERRIDE_REQUIRED", "覆盖参数不能为空");
        }
        return classificationGuard.overrideClassification(command.caseId(), command.snapshotId(),
                command.costSubjectId(), command.reason());
    }

    public Map<String, Object> projectConfiguration(Long projectId) {
        return governanceOperations.projectConfiguration(projectId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createProjectConfig(ProjectConfigCommand command) {
        requireCompanyFinanceOperator();
        return governanceOperations.createProjectConfig(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitProjectConfig(Long id) {
        requireCompanyFinanceOperator();
        return governanceOperations.submitProjectConfig(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelProjectConfig(Long id) {
        requireCompanyFinanceOperator();
        return governanceOperations.cancelProjectConfig(id);
    }

    public void markProjectConfigSubmitted(Long id, Long instanceId) {
        governanceOperations.markProjectConfigSubmitted(id, instanceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyProjectConfig(Long id, Long instanceId) {
        governanceOperations.applyProjectConfig(id, instanceId);
    }

    public void rejectProjectConfig(Long id, Long instanceId, String status) {
        governanceOperations.rejectProjectConfig(id, instanceId, status);
    }

    public List<Map<String, Object>> recalculationBatches() {
        return governanceOperations.recalculationBatches();
    }

    public Map<String, Object> recalculationBatch(Long id) {
        return governanceOperations.recalculationBatch(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelRecalculation(Long id) {
        requireCompanyFinanceOperator();
        governanceOperations.cancelRecalculation(id);
    }

    public List<Map<String, Object>> reversalRequests() {
        return governanceOperations.reversalRequests();
    }

    public Map<String, Object> reversalRequest(Long id) {
        return governanceOperations.reversalRequest(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createReversal(ReversalCommand command) {
        requireCompanyFinanceOperator();
        return governanceOperations.createReversal(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitReversal(Long id) {
        requireCompanyFinanceOperator();
        return governanceOperations.submitReversal(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelReversal(Long id) {
        requireCompanyFinanceOperator();
        return governanceOperations.cancelReversal(id);
    }

    public void markReversalSubmitted(Long id, Long instanceId) {
        governanceOperations.markReversalSubmitted(id, instanceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void postReversal(Long id, Long instanceId) {
        Map<String, Object> request = governanceOperations.reversalRequestForPost(id, instanceId);
        String targetType = String.valueOf(request.get("targetType"));
        Long targetId = valueAsLong(request.get("targetId"));
        Long finalId = switch (targetType) {
            case "BID_TRANSFER" -> bidTransferOperations.reverseBidTransferApproved(
                    targetId, id, instanceId, String.valueOf(request.get("reason")));
            case "FINANCE_ALLOCATION" -> financeAllocationOperations.reverseFinanceAllocationApproved(
                    targetId, id, instanceId, String.valueOf(request.get("reason")));
            case "RECALCULATION" -> governanceOperations.reverseRecalculationApproved(
                    targetId, id, instanceId, String.valueOf(request.get("reason")));
            default -> throw new com.cgcpms.common.exception.BusinessException(
                    "COST_REVERSAL_TARGET_INVALID", "不支持的成本冲销对象");
        };
        governanceOperations.completeReversal(id, instanceId, finalId);
    }

    public void rejectReversal(Long id, Long instanceId, String status) {
        governanceOperations.rejectReversal(id, instanceId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createRecalculation(RecalculationCommand command) {
        requireCompanyFinanceOperator();
        return governanceOperations.createRecalculation(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitRecalculation(Long id) {
        requireCompanyFinanceOperator();
        return governanceOperations.submitRecalculation(id);
    }

    public void markRecalculationSubmitted(Long id, Long instanceId) {
        governanceOperations.markRecalculationSubmitted(id, instanceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void postRecalculation(Long id, Long instanceId) {
        governanceOperations.postRecalculation(id, instanceId);
    }

    public void rejectRecalculation(Long id, Long instanceId, String status) {
        governanceOperations.rejectRecalculation(id, instanceId, status);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Long valueAsLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private void requireCompanyFinanceOperator() {
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
                JOIN sys_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id
                WHERE u.tenant_id=? AND u.id=? AND u.status='ENABLE' AND u.deleted_flag=0
                  AND r.role_code=? AND r.status='ENABLE' AND r.deleted_flag=0
                """, Integer.class, tenantId, userId, SystemRoleContract.COMPANY_FINANCE);
        if (matches == null || matches == 0) {
            throw new BusinessException("COST_COMPANY_FINANCE_REQUIRED", "仅公司财务可维护并提交成本治理业务");
        }
    }
}
