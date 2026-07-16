package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementItemMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.mapper.SettlementSubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StlSettlementWriteServiceAmountTest {

    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;

    @Mock
    private StlSettlementMapper settlementMapper;

    @Mock
    private StlSettlementItemMapper settlementItemMapper;

    @Mock
    private CtContractMapper contractMapper;

    @Mock
    private WorkflowEngine workflowEngine;

    @Mock
    private StlSettlementQueryService queryService;

    @Mock private SettlementSubMeasureMapper settlementSubMeasureMapper;
    @Mock private SubMeasureMapper subMeasureMapper;
    @Mock private SysFileMapper fileMapper;
    @Mock private PmProjectMapper projectMapper;
    @Mock private ProjectAccessChecker projectAccessChecker;
    @Mock private WfInstanceMapper wfInstanceMapper;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createCapturesTheCompleteSettlementAmountSnapshot() {
        UserContext.set(Jwts.claims()
                .add("userId", 1L)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .build());

        CtContract contract = new CtContract();
        contract.setId(CONTRACT_ID);
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(PROJECT_ID);
        contract.setCurrentAmount(new BigDecimal("1000.00"));
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(contract);
        when(settlementMapper.selectCount(any())).thenReturn(0L);
        when(settlementMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(0, 1));
        when(queryService.sumVarOrderConfirmed(TENANT_ID, CONTRACT_ID))
                .thenReturn(new BigDecimal("100.00"));
        when(queryService.sumSubMeasureApproved(TENANT_ID, CONTRACT_ID))
                .thenReturn(new BigDecimal("200.00"));
        when(queryService.sumPaidAmount(TENANT_ID, CONTRACT_ID))
                .thenReturn(new BigDecimal("300.00"));
        doAnswer(invocation -> {
            StlSettlement inserted = invocation.getArgument(0);
            inserted.setId(42L);
            return 1;
        }).when(settlementMapper).insert(any(StlSettlement.class));

        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID);
        settlement.setDeductionAmount(new BigDecimal("50.00"));

        StlSettlementWriteService service = new StlSettlementWriteService(
                settlementMapper,
                settlementItemMapper,
                contractMapper,
                workflowEngine,
                queryService,
                settlementSubMeasureMapper,
                subMeasureMapper,
                fileMapper,
                projectMapper,
                projectAccessChecker,
                wfInstanceMapper);

        assertEquals(42L, service.create(settlement));
        assertEquals(new BigDecimal("1000.00"), settlement.getContractAmount());
        assertEquals(new BigDecimal("100.00"), settlement.getChangeAmount());
        assertEquals(new BigDecimal("200.00"), settlement.getMeasuredAmount());
        assertEquals(new BigDecimal("300.00"), settlement.getPaidAmount());
        assertEquals(new BigDecimal("50.00"), settlement.getDeductionAmount());
        assertEquals(new BigDecimal("250.00"), settlement.getFinalAmount());
        assertEquals(new BigDecimal("12.50"), settlement.getWarrantyAmount());
        assertEquals(new BigDecimal("-62.50"), settlement.getUnpaidAmount());
        assertEquals(SettlementAmountPolicy.FORMULA_VERSION, settlement.getAmountFormulaVersion());
        verify(queryService).sumVarOrderConfirmed(TENANT_ID, CONTRACT_ID);
        verify(queryService).sumSubMeasureApproved(TENANT_ID, CONTRACT_ID);
        verify(queryService).sumPaidAmount(TENANT_ID, CONTRACT_ID);
    }
}
