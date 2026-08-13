package com.cgcpms.cost.service;

import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class CostSubjectV2Service {

    private final CostSubjectMappingOperations mappingOperations;
    private final BidCostTransferOperations bidTransferOperations;
    private final FinanceCostAllocationOperations financeAllocationOperations;

    public CostSubjectV2Service(JdbcTemplate jdbc,
                                ProjectAccessChecker projectAccessChecker,
                                ObjectProvider<WorkflowEngine> workflowEngineProvider) {
        this.mappingOperations = new CostSubjectMappingOperations(jdbc, projectAccessChecker);
        this.bidTransferOperations = new BidCostTransferOperations(
                jdbc, projectAccessChecker, workflowEngineProvider);
        this.financeAllocationOperations = new FinanceCostAllocationOperations(
                jdbc, projectAccessChecker, workflowEngineProvider);
    }

    public record MappingItem(Long sourceSubjectId, String targetGroupCode, Long targetSubjectId,
                              String historicalDisplayName, String mappingReason) {}

    public record MappingVersionCommand(String versionCode, String versionName, LocalDate effectiveDate,
                                        String remark, List<MappingItem> items) {}

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
        return mappingOperations.createMappingVersion(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public void activateMappingVersion(Long id, Long approvalInstanceId) {
        mappingOperations.activateMappingVersion(id, approvalInstanceId);
    }

    public List<Map<String, Object>> rules() {
        return mappingOperations.rules();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRule(RuleCommand command) {
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
        return bidTransferOperations.createBidTransferRequest(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitBidTransferRequest(Long id) {
        return bidTransferOperations.submitBidTransferRequest(id);
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
        return bidTransferOperations.transferBidCost(command);
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
        return financeAllocationOperations.createFinanceAllocationRequest(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitFinanceAllocationRequest(Long id) {
        return financeAllocationOperations.submitFinanceAllocationRequest(id);
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
}
