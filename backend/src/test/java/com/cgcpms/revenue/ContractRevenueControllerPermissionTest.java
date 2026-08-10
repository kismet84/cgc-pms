package com.cgcpms.revenue;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.revenue.controller.ContractRevenueController;
import com.cgcpms.revenue.controller.RevenueOperationsController;
import com.cgcpms.revenue.service.ContractRevenueService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractRevenueControllerPermissionTest {

    @Test
    void revenueOperationsQueryCanReadOnlyApprovedSettlementCandidates() throws Exception {
        PreAuthorize candidate = RevenueOperationsController.class
                .getMethod("settlementRevenueOptions", Long.class, Long.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize legacyList = ContractRevenueController.class
                .getMethod("getPage", long.class, long.class, Long.class, Long.class,
                        String.class, String.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertTrue(candidate.value().contains("revenue:operations:query"));
        assertFalse(legacyList.value().contains("revenue:operations:query"));

        ContractRevenueService service = mock(ContractRevenueService.class);
        when(service.getPage(1, 200, 11L, 22L, null, null, "APPROVED"))
                .thenReturn(new Page<>(1, 200));
        new RevenueOperationsController(null, null, service).settlementRevenueOptions(11L, 22L);
        verify(service).getPage(1, 200, 11L, 22L, null, null, "APPROVED");
    }
}
