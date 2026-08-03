package com.cgcpms.bid;

import com.cgcpms.bid.controller.BidCostController;
import com.cgcpms.bid.dto.BidOwnerOption;
import com.cgcpms.bid.dto.BidStatusUpdateRequest;
import com.cgcpms.bid.service.BidCostService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidCostControllerTest {

    @Test
    void ownerOptionsUseBidScopedService() {
        BidCostService service = mock(BidCostService.class);
        BidCostController controller = new BidCostController(service);
        var options = List.of(new BidOwnerOption(269L, "管理层"));
        when(service.listOwnerOptions()).thenReturn(options);

        assertEquals(options, controller.getOwnerOptions().getData());
        verify(service).listOwnerOptions();
    }

    @Test
    void unifiedStatusEndpointDelegatesToSingleServicePath() {
        BidCostService service = mock(BidCostService.class);
        BidCostController controller = new BidCostController(service);
        when(service.changeStatus(1L, "EVALUATING", "WON", null)).thenReturn(99L);

        var response = controller.changeStatus(
                1L, new BidStatusUpdateRequest("WON", "EVALUATING", null));

        assertEquals(99L, response.getData());
        verify(service).changeStatus(1L, "EVALUATING", "WON", null);
    }

    @Test
    void legacyWonEndpointDelegatesInsteadOfOwningTransitionRules() {
        BidCostService service = mock(BidCostService.class);
        BidCostController controller = new BidCostController(service);

        controller.markAsWon(2L, 123L);

        verify(service).markAsWon(2L, 123L);
    }
}
