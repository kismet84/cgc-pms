package com.cgcpms.payment.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.vo.PaymentTraceVO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTraceServiceContractTest {

    @Test
    void keepsStablePublicFacadeAndSingleReadOnlyTransactionBoundary() {
        Transactional transaction = PaymentTraceService.class.getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(transaction.readOnly());
        assertEquals(1, PaymentTraceService.class.getConstructors().length);
        assertEquals(27, PaymentTraceService.class.getConstructors()[0].getParameterCount());

        Set<String> signatures = Arrays.stream(PaymentTraceService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(PaymentTraceServiceContractTest::signature)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "byCashJournal(java.lang.Long)",
                "byPayRecord(java.lang.Long)",
                "byExpense(java.lang.Long)",
                "bySettlement(java.lang.Long)",
                "byApproval(java.lang.Long)",
                "byInvoice(java.lang.Long)",
                "byVoucher(java.lang.Long)",
                "byContract(java.lang.Long)",
                "byProject(java.lang.Long)",
                "byApplication(java.lang.Long)"), signatures);
    }

    @Test
    void validatorIsPlainPackagePrivateAndKeepsFailClosedErrorCode() {
        assertFalse(Modifier.isPublic(PaymentTraceValidator.class.getModifiers()));
        assertEquals(0, PaymentTraceValidator.class.getDeclaredFields().length);
        assertEquals(0, PaymentTraceValidator.class.getAnnotations().length);

        PayApplication application = new PayApplication();
        application.setApprovalStatus("APPROVING");
        PaymentTraceVO trace = new PaymentTraceVO();
        trace.setPaymentApplication(application);

        BusinessException error = assertThrows(BusinessException.class,
                () -> new PaymentTraceValidator().validate(trace, 91L));
        assertEquals("PAYMENT_TRACE_INCOMPLETE", error.getCode());
        assertEquals("付款申请缺少审批实例", error.getMessage());
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(",", "(", ")"));
    }
}
