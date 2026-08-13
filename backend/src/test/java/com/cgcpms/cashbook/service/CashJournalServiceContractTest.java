package com.cgcpms.cashbook.service;

import com.cgcpms.payment.entity.PayRecord;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashJournalServiceContractTest {

    private static final Set<String> WRITE_TRANSACTION_SIGNATURES = Set.of(
            "createManual(CashJournalCreateRequest)->CashJournalEntryVO",
            "createPendingFromPayRecord(PayRecord,PayApplication)->CashJournalEntryVO",
            "createPendingFromPayRecord(PayRecord)->CashJournalEntryVO",
            "updateDraft(Long,CashJournalUpdateRequest)->CashJournalEntryVO",
            "archive(Long)->CashJournalEntryVO", "reverse(Long,String)->CashJournalEntryVO",
            "reverseForPayment(Long,String,Long)->CashJournalEntryVO",
            "reverseForPayment(Long,String,Long,LocalDateTime)->CashJournalEntryVO",
            "reopen(Long,String)->CashJournalEntryVO");

    @Test
    void keepsPublicConstructorAndMethodsStable() throws NoSuchMethodException {
        Constructor<?> constructor = Arrays.stream(CashJournalService.class.getDeclaredConstructors())
                .filter(candidate -> Modifier.isPublic(candidate.getModifiers()))
                .findFirst().orElseThrow();
        assertEquals(List.of(
                "CashJournalEntryMapper", "FundAccountMapper", "FundAccountService", "CtContractMapper",
                "ProjectAccessChecker", "CashJournalChangeLogMapper", "SysFileMapper", "ObjectMapper",
                "CashJournalAlertService", "AccountingPeriodGuard", "PayRecordMapper", "PayApplicationMapper",
                "PaymentApplicationSourceService", "ContractBudgetAllocationService",
                "PaymentArchiveEvidenceService", "BidCostMapper", "BidDepositMapper", "CostSubjectMapper"),
                Arrays.stream(constructor.getParameterTypes()).map(Class::getSimpleName).toList());
        assertNotNull(CashJournalService.class.getAnnotation(Service.class));

        Set<String> publicMethods = Arrays.stream(CashJournalService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(CashJournalServiceContractTest::signature)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "createManual(CashJournalCreateRequest)->CashJournalEntryVO",
                "createPendingFromPayRecord(PayRecord,PayApplication)->CashJournalEntryVO",
                "createPendingFromPayRecord(PayRecord)->CashJournalEntryVO",
                "updateDraft(Long,CashJournalUpdateRequest)->CashJournalEntryVO",
                "archive(Long)->CashJournalEntryVO", "reverse(Long,String)->CashJournalEntryVO",
                "reverseForPayment(Long,String,Long)->CashJournalEntryVO",
                "reverseForPayment(Long,String,Long,LocalDateTime)->CashJournalEntryVO",
                "reopen(Long,String)->CashJournalEntryVO", "page(CashJournalQuery)->IPage",
                "summary(CashJournalQuery)->CashJournalSummaryVO", "getById(Long)->CashJournalEntryVO",
                "exportCsv(CashJournalQuery)->byte[]", "requireEntry(Long)->CashJournalEntry"), publicMethods);

        Method legacy = CashJournalService.class.getDeclaredMethod("createPendingFromPayRecord", PayRecord.class);
        assertNotNull(legacy.getAnnotation(Deprecated.class));
    }

    @Test
    void keepsTenTransactionBoundariesStable() {
        List<Method> transactionalMethods = Arrays.stream(CashJournalService.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Transactional.class) != null)
                .toList();
        assertEquals(10, transactionalMethods.size());
        assertEquals(WRITE_TRANSACTION_SIGNATURES,
                transactionalMethods.stream()
                        .filter(method -> !"summary".equals(method.getName()))
                        .map(CashJournalServiceContractTest::signature)
                        .collect(Collectors.toSet()));

        for (Method method : transactionalMethods) {
            Transactional transactional = method.getAnnotation(Transactional.class);
            if ("summary".equals(method.getName())) {
                assertTrue(transactional.readOnly());
                assertArrayEquals(new Class<?>[0], transactional.rollbackFor());
            } else {
                assertFalse(transactional.readOnly());
                assertArrayEquals(new Class<?>[]{Exception.class}, transactional.rollbackFor());
            }
        }
    }

    @Test
    void keepsReadOperationsInternalAndOutsideSpringTransactionProxy() {
        for (Class<?> collaborator : List.of(
                CashJournalReadOperations.class, CashJournalViewAssembler.class)) {
            assertFalse(Modifier.isPublic(collaborator.getModifiers()));
            assertTrue(Modifier.isFinal(collaborator.getModifiers()));
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
