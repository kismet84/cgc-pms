package com.cgcpms.contract.service;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
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

class CtContractServiceContractTest {

    private static final Set<String> TRANSACTIONAL_SIGNATURES = Set.of(
            "create(CtContract)->Long", "update(CtContract)->void",
            "settlePerformance(Long,Integer)->void", "submitForApproval(Long)->void",
            "submitForApproval(Long,Integer)->void", "delete(Long)->void",
            "compositeSave(ContractSaveRequest)->Long");

    @Test
    void keepsPublicConstructorMethodsAndTransactionBoundary() {
        Constructor<?> constructor = Arrays.stream(CtContractService.class.getDeclaredConstructors())
                .filter(candidate -> Modifier.isPublic(candidate.getModifiers()))
                .findFirst().orElseThrow();
        assertEquals(List.of(
                "CtContractMapper", "CtContractChangeMapper", "ContractBudgetAllocationMapper",
                "ContractBudgetAllocationService", "ProjectBudgetMapper", "PayApplicationMapper",
                "PayRecordMapper", "StlSettlementMapper", "PmProjectMapper", "MdPartnerMapper",
                "CtContractItemService", "CtContractPaymentTermService", "WorkflowEngine",
                "WfInstanceMapper", "WfRecordMapper", "CodeGenerationService",
                "ProjectAccessChecker", "SysDictDataService", "FileLifecycleGateway", "JdbcTemplate"),
                Arrays.stream(constructor.getParameterTypes()).map(Class::getSimpleName).toList());
        assertNotNull(CtContractService.class.getAnnotation(Service.class));

        Set<String> publicMethods = Arrays.stream(CtContractService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(CtContractServiceContractTest::signature)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "getProjectOptions()->List", "getPage(long,long,String,String,String,String,String,String,Long,Long,Long,LocalDate,LocalDate)->IPage",
                "getKpi(String,String,String,String,String,Long,Long,Long,LocalDate,LocalDate)->Map",
                "getPerformanceReport(Long)->ContractPerformanceReportVO", "getById(Long)->CtContractVO",
                "create(CtContract)->Long", "update(CtContract)->void",
                "settlePerformance(Long,Integer)->void", "submitForApproval(Long)->void",
                "submitForApproval(Long,Integer)->void", "delete(Long)->void",
                "compositeSave(ContractSaveRequest)->Long", "getApprovalRecords(Long)->List"), publicMethods);

        Set<String> transactionalMethods = Arrays.stream(CtContractService.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Transactional.class) != null)
                .map(CtContractServiceContractTest::signature)
                .collect(Collectors.toSet());
        assertEquals(TRANSACTIONAL_SIGNATURES, transactionalMethods);
        for (Method method : CtContractService.class.getDeclaredMethods()) {
            Transactional transactional = method.getAnnotation(Transactional.class);
            if (transactional != null) {
                assertArrayEquals(new Class<?>[]{Exception.class}, transactional.rollbackFor());
            }
        }
    }

    @Test
    void keepsProjectOptionRecordStable() {
        assertEquals(List.of(
                "id:String", "projectCode:String", "projectName:String", "status:String",
                "mainEligible:boolean", "nonMainEligible:boolean"),
                Arrays.stream(CtContractService.ContractProjectOption.class.getRecordComponents())
                        .map(component -> component.getName() + ":" + component.getType().getSimpleName())
                        .toList());
    }

    @Test
    void keepsCollaboratorsInternalAndOutsideSpringTransactionProxies() {
        for (Class<?> collaborator : List.of(
                CtContractQueryOperations.class,
                CtContractViewAssembler.class,
                CtContractPerformanceSettlement.class)) {
            assertEquals(false, Modifier.isPublic(collaborator.getModifiers()));
            assertNull(collaborator.getAnnotation(Service.class));
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
                .collect(Collectors.joining(",", "(", ")"))
                + "->" + method.getReturnType().getSimpleName();
    }
}
