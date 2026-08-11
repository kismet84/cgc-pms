package com.cgcpms.security;

import com.cgcpms.cashbook.controller.CashJournalController;
import com.cgcpms.financeops.controller.FinanceOperationsController;
import com.cgcpms.revenue.controller.RevenueOperationsController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessAmountExportAuthorizationTest {

    @Test
    void everyPriceBearingCsvExportRequiresAmountPermission() {
        for (Class<?> controller : List.of(CashJournalController.class,
                FinanceOperationsController.class, RevenueOperationsController.class)) {
            var export = List.of(controller.getDeclaredMethods()).stream()
                    .filter(method -> method.getName().equals("export"))
                    .findFirst().orElseThrow();
            assertTrue(export.getAnnotation(PreAuthorize.class).value().contains(BusinessAmountAccess.PERMISSION),
                    controller.getSimpleName());
        }
    }
}
