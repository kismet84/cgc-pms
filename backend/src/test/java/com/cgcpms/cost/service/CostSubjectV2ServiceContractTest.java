package com.cgcpms.cost.service;

import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CostSubjectV2ServiceContractTest {

    private static final Set<String> TRANSACTIONAL_METHODS = Set.of(
            "createMappingVersion", "activateMappingVersion", "createRule", "upsertScope",
            "createBidTransferRequest", "submitBidTransferRequest", "postBidTransferRequest",
            "transferBidCost", "reverseBidTransfer",
            "createFinanceAllocationRequest", "submitFinanceAllocationRequest", "postFinanceAllocationRequest",
            "allocateFinanceCost", "reverseFinanceAllocation");

    @Test
    void keepsFacadeConstructorPublicMethodsAndTransactionBoundary() throws Exception {
        assertNotNull(CostSubjectV2Service.class.getConstructor(
                JdbcTemplate.class, ProjectAccessChecker.class, ObjectProvider.class));

        Set<String> publicMethods = Arrays.stream(CostSubjectV2Service.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(CostSubjectV2ServiceContractTest::signature)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "mappingVersions()", "mappingItems(Long)", "mappingVersionDetail(Long)",
                "createMappingVersion(MappingVersionCommand)", "activateMappingVersion(Long,Long)",
                "rules()", "createRule(RuleCommand)", "resolveRule(String,String,Long)",
                "scopes(Long)", "upsertScope(ScopeCommand)", "impact(Long)",
                "bidTransferRequests()", "createBidTransferRequest(BidTransferRequestCommand)",
                "submitBidTransferRequest(Long)", "bidTransferRequest(Long)",
                "markBidTransferRequestSubmitted(Long,Long)",
                "markBidTransferRequestRejected(Long,Long,String)",
                "postBidTransferRequest(Long,Long)", "transfers()", "bidCostTransferDetail(Long)",
                "bidCostTransferReversalDetail(Long)", "transferBidCost(TransferCommand)",
                "reverseBidTransfer(Long,Long,String,String)",
                "financeAllocationRequests()", "createFinanceAllocationRequest(FinanceAllocationCommand)",
                "submitFinanceAllocationRequest(Long)", "financeAllocationRequest(Long)",
                "markFinanceAllocationRequestSubmitted(Long,Long)",
                "markFinanceAllocationRequestRejected(Long,Long,String)",
                "postFinanceAllocationRequest(Long,Long)", "financeAllocations()",
                "financeAllocationDetail(Long)", "financeAllocationReversalDetail(Long)",
                "allocateFinanceCost(FinanceAllocationCommand)",
                "reverseFinanceAllocation(Long,Long,String,String)", "reconciliation(Long)"), publicMethods);

        Set<String> transactionalMethods = Arrays.stream(CostSubjectV2Service.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Transactional.class) != null)
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertEquals(TRANSACTIONAL_METHODS, transactionalMethods);
        for (String methodName : TRANSACTIONAL_METHODS) {
            Method method = Arrays.stream(CostSubjectV2Service.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            assertArrayEquals(new Class<?>[]{Exception.class},
                    method.getAnnotation(Transactional.class).rollbackFor());
        }
    }

    @Test
    void keepsNestedCommandRecordsStable() {
        assertRecord(CostSubjectV2Service.MappingItem.class,
                "sourceSubjectId:Long", "targetGroupCode:String", "targetSubjectId:Long",
                "historicalDisplayName:String", "mappingReason:String");
        assertRecord(CostSubjectV2Service.MappingVersionCommand.class,
                "versionCode:String", "versionName:String", "effectiveDate:LocalDate",
                "remark:String", "items:List");
        assertRecord(CostSubjectV2Service.RuleCommand.class,
                "ruleCode:String", "mappingVersionId:Long", "sourceType:String",
                "businessCategory:String", "projectId:Long", "costSubjectId:Long",
                "priority:Integer", "effectiveFrom:LocalDate", "effectiveTo:LocalDate", "remark:String");
        assertRecord(CostSubjectV2Service.ScopeCommand.class,
                "projectId:Long", "costSubjectId:Long", "enabled:Boolean",
                "effectiveFrom:LocalDate", "effectiveTo:LocalDate", "remark:String");
        assertRecord(CostSubjectV2Service.TransferCommand.class,
                "bidCostId:Long", "projectId:Long", "targetId:Long", "mappingVersionId:Long",
                "approvalInstanceId:Long", "idempotencyKey:String", "remark:String");
        assertRecord(CostSubjectV2Service.BidTransferRequestCommand.class,
                "bidCostId:Long", "projectId:Long", "targetId:Long", "mappingVersionId:Long",
                "idempotencyKey:String", "remark:String");
        assertRecord(CostSubjectV2Service.AllocationLine.class,
                "projectId:Long", "basisValue:BigDecimal");
        assertRecord(CostSubjectV2Service.FinanceAllocationCommand.class,
                "sourceType:String", "sourceId:Long", "allocationBasis:String",
                "accountingPeriod:String", "costSubjectId:Long", "approvalInstanceId:Long",
                "idempotencyKey:String", "remark:String", "lines:List");
    }

    @Test
    void keepsCollaboratorsInternalAndOutsideSpringTransactionProxies() {
        for (Class<?> collaborator : List.of(
                CostSubjectMappingOperations.class,
                BidCostTransferOperations.class,
                FinanceCostAllocationOperations.class,
                CostSubjectV2Support.class)) {
            assertEquals(false, Modifier.isPublic(collaborator.getModifiers()));
            assertNull(collaborator.getAnnotation(Component.class));
            assertEquals(Set.of(), Arrays.stream(collaborator.getDeclaredMethods())
                    .filter(method -> method.getAnnotation(Transactional.class) != null)
                    .map(Method::getName)
                    .collect(Collectors.toSet()));
        }
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    private static void assertRecord(Class<?> type, String... components) {
        assertEquals(Arrays.asList(components), Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName() + ":" + component.getType().getSimpleName())
                .toList());
    }
}
