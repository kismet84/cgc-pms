package com.cgcpms.contract;

import com.cgcpms.contract.controller.CtContractItemController;
import com.cgcpms.contract.controller.CtContractPaymentTermController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractChildResourceAuthorizationTest {

    @Test
    void contractQueryCanReadCompositeContractChildren() throws Exception {
        assertContractQuery(CtContractItemController.class);
        assertContractQuery(CtContractPaymentTermController.class);
    }

    private static void assertContractQuery(Class<?> controller) throws Exception {
        String expression = controller.getMethod("getByContractId", Long.class)
                .getAnnotation(PreAuthorize.class)
                .value();
        assertTrue(expression.contains("hasAuthority('contract:query')"), controller.getSimpleName());
    }
}
