package com.cgcpms.route;

import com.cgcpms.contract.controller.CtContractController;
import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.settlement.controller.StlSettlementController;
import com.cgcpms.settlement.service.StlSettlementQueryService;
import com.cgcpms.settlement.service.StlSettlementWriteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("KPI static routes do not conflict with numeric id routes")
class ContractSettlementKpiRouteTest {

    @Test
    @DisplayName("GET /contracts/kpi routes to contract KPI endpoint")
    void contractKpiRouteIsStaticEndpoint() throws Exception {
        CtContractService contractService = mock(CtContractService.class);
        when(contractService.getKpi(null, null, null, null, null, null, null, null))
                .thenReturn(Map.of(
                        "totalCount", 0L,
                        "totalAmount", "0",
                        "paidAmount", "0",
                        "unpaidAmount", "0",
                        "overdueCount", 0L));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new CtContractController(contractService))
                .build();

        mockMvc.perform(get("/contracts/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value("0"))
                .andExpect(jsonPath("$.data.paidAmount").value("0"))
                .andExpect(jsonPath("$.data.unpaidAmount").value("0"))
                .andExpect(jsonPath("$.data.overdueCount").value(0));
    }

    @Test
    @DisplayName("GET /settlements/kpi routes to settlement KPI endpoint")
    void settlementKpiRouteIsStaticEndpoint() throws Exception {
        StlSettlementQueryService settlementService = mock(StlSettlementQueryService.class);
        when(settlementService.getKpi(null, null, null, null, null))
                .thenReturn(Map.of(
                        "totalCount", 0L,
                        "totalContractAmount", "0",
                        "totalFinalAmount", "0",
                        "totalChangeAmount", "0",
                        "totalPaidAmount", "0",
                        "totalUnpaidAmount", "0",
                        "draftCount", 0L,
                        "finalizedCount", 0L));

        StlSettlementWriteService writeService = mock(StlSettlementWriteService.class);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new StlSettlementController(settlementService, writeService))
                .build();

        mockMvc.perform(get("/settlements/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.totalContractAmount").value("0"))
                .andExpect(jsonPath("$.data.totalFinalAmount").value("0"))
                .andExpect(jsonPath("$.data.totalChangeAmount").value("0"))
                .andExpect(jsonPath("$.data.totalPaidAmount").value("0"))
                .andExpect(jsonPath("$.data.totalUnpaidAmount").value("0"))
                .andExpect(jsonPath("$.data.draftCount").value(0))
                .andExpect(jsonPath("$.data.finalizedCount").value(0));
    }
}
