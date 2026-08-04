package com.cgcpms.settlement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.mapper.StlSettlementItemMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.mapper.SettlementSubMeasureMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.file.service.FileLifecycleGateway;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StlSettlementWriteServiceAmountTest {

    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long NEW_CONTRACT_ID = 30002L;

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
    @Mock private SubMeasureItemMapper subMeasureItemMapper;
    @Mock private CtContractItemMapper contractItemMapper;
    @Mock private FileLifecycleGateway fileLifecycleGateway;
    @Mock private SysFileMapper fileMapper;
    @Mock private PmProjectMapper projectMapper;
    @Mock private ProjectAccessChecker projectAccessChecker;
    @Mock private WfInstanceMapper wfInstanceMapper;
    @Mock private VarOrderMapper varOrderMapper;
    @Mock private PayRecordMapper payRecordMapper;

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
        contract.setContractType("SUB");
        contract.setApprovalStatus("APPROVED");
        contract.setContractStatus("PERFORMING");
        contract.setCurrentAmount(new BigDecimal("1000.00"));
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(contract);
        when(settlementMapper.selectCount(any())).thenReturn(0L);
        when(settlementMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(0, 1));
        when(queryService.sumVarOrderConfirmed(TENANT_ID, PROJECT_ID, CONTRACT_ID))
                .thenReturn(new BigDecimal("100.00"));
        when(queryService.sumSubMeasureApproved(TENANT_ID, PROJECT_ID, CONTRACT_ID))
                .thenReturn(new BigDecimal("200.00"));
        when(queryService.sumPaidAmount(TENANT_ID, PROJECT_ID, CONTRACT_ID))
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

        assertEquals(42L, service().create(settlement));
        assertEquals(new BigDecimal("1000.00"), settlement.getContractAmount());
        assertEquals(new BigDecimal("100.00"), settlement.getChangeAmount());
        assertEquals(new BigDecimal("200.00"), settlement.getMeasuredAmount());
        assertEquals(new BigDecimal("300.00"), settlement.getPaidAmount());
        assertEquals(new BigDecimal("50.00"), settlement.getDeductionAmount());
        assertEquals(new BigDecimal("250.00"), settlement.getFinalAmount());
        assertEquals(new BigDecimal("12.50"), settlement.getWarrantyAmount());
        assertEquals(new BigDecimal("-62.50"), settlement.getUnpaidAmount());
        assertEquals(SettlementAmountPolicy.FORMULA_VERSION, settlement.getAmountFormulaVersion());
        verify(queryService).sumVarOrderConfirmed(TENANT_ID, PROJECT_ID, CONTRACT_ID);
        verify(queryService).sumSubMeasureApproved(TENANT_ID, PROJECT_ID, CONTRACT_ID);
        verify(queryService).sumPaidAmount(TENANT_ID, PROJECT_ID, CONTRACT_ID);
    }

    @Test
    void updateUsesTheNewContractForTheAmountSnapshot() {
        setContext();
        StlSettlement existing = draftSettlement(77L, CONTRACT_ID, 10L);
        when(settlementMapper.selectByIdForUpdate(77L, TENANT_ID)).thenReturn(existing);
        CtContract contract = contract(NEW_CONTRACT_ID, 20L, "2000.00");
        when(contractMapper.selectById(NEW_CONTRACT_ID)).thenReturn(contract);
        when(queryService.sumVarOrderConfirmed(TENANT_ID, PROJECT_ID, NEW_CONTRACT_ID))
                .thenReturn(new BigDecimal("200.00"));
        when(queryService.sumSubMeasureApproved(TENANT_ID, PROJECT_ID, NEW_CONTRACT_ID))
                .thenReturn(new BigDecimal("500.00"));
        when(queryService.sumPaidAmount(TENANT_ID, PROJECT_ID, NEW_CONTRACT_ID))
                .thenReturn(new BigDecimal("100.00"));
        when(settlementMapper.updateById(existing)).thenReturn(1);

        StlSettlement command = new StlSettlement();
        command.setId(77L);
        command.setContractId(NEW_CONTRACT_ID);
        command.setDeductionAmount(new BigDecimal("50.00"));
        service().update(command);

        assertEquals(NEW_CONTRACT_ID, existing.getContractId());
        assertEquals(20L, existing.getPartnerId());
        assertEquals(new BigDecimal("2000.00"), existing.getContractAmount());
        assertEquals(new BigDecimal("200.00"), existing.getChangeAmount());
        assertEquals(new BigDecimal("500.00"), existing.getMeasuredAmount());
        assertEquals(new BigDecimal("650.00"), existing.getFinalAmount());
    }

    @Test
    void saveItemsRebuildsClientAmountsFromApprovedMeasureAndContractItem() {
        setContext();
        StlSettlement settlement = draftSettlement(77L, CONTRACT_ID, 10L);
        when(settlementMapper.selectByIdForUpdate(77L, TENANT_ID)).thenReturn(settlement);
        SubMeasure measure = approvedMeasure(91L, CONTRACT_ID, 10L, "190.00");
        measure.setApprovedAmount(new BigDecimal("200.00"));
        when(subMeasureMapper.selectList(any())).thenReturn(java.util.List.of(measure));
        SubMeasureItem measuredItem = measuredItem(91L, 900L, "2.5000");
        when(subMeasureItemMapper.selectList(any())).thenReturn(java.util.List.of(measuredItem));
        when(contractItemMapper.selectByIdForUpdate(900L, TENANT_ID))
                .thenReturn(contractItem(900L, CONTRACT_ID, "80.00"));

        StlSettlementItem command = new StlSettlementItem();
        command.setSourceType("CT_CONTRACT");
        command.setSourceId(900L);
        command.setItemName("客户端伪造名称");
        command.setQuantity(new BigDecimal("999"));
        command.setUnitPrice(new BigDecimal("0.01"));
        command.setAmount(new BigDecimal("9.99"));
        service().saveItems(77L, java.util.List.of(command));

        ArgumentCaptor<StlSettlementItem> captor = ArgumentCaptor.forClass(StlSettlementItem.class);
        verify(settlementItemMapper).insert(captor.capture());
        StlSettlementItem saved = captor.getValue();
        assertEquals("合同清单-900", saved.getItemName());
        assertEquals(new BigDecimal("2.5000"), saved.getQuantity());
        assertEquals(new BigDecimal("80.00"), saved.getUnitPrice());
        assertEquals(new BigDecimal("200.00"), saved.getAmount());
        assertEquals("CT_CONTRACT", saved.getSourceType());
        assertEquals(900L, saved.getSourceId());
    }

    @Test
    void saveItemsRejectsContractItemFromAnotherContractBeforeReplacingRows() {
        setContext();
        when(settlementMapper.selectByIdForUpdate(77L, TENANT_ID))
                .thenReturn(draftSettlement(77L, CONTRACT_ID, 10L));
        when(subMeasureMapper.selectList(any())).thenReturn(java.util.List.of(
                approvedMeasure(91L, CONTRACT_ID, 10L, "190.00")));
        when(subMeasureItemMapper.selectList(any())).thenReturn(java.util.List.of(
                measuredItem(91L, 901L, "1.0000")));
        when(contractItemMapper.selectByIdForUpdate(901L, TENANT_ID))
                .thenReturn(contractItem(901L, NEW_CONTRACT_ID, "190.00"));
        StlSettlementItem command = new StlSettlementItem();
        command.setSourceType("CT_CONTRACT");
        command.setSourceId(901L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().saveItems(77L, java.util.List.of(command)));

        assertEquals("STL_SETTLEMENT_SOURCE_SCOPE_INVALID", exception.getCode());
        verify(settlementItemMapper, never()).delete(any());
        verify(settlementItemMapper, never()).insert(any(StlSettlementItem.class));
    }

    @Test
    void createRejectsNegativeDeductionWithoutWriting() {
        setContext();
        when(contractMapper.selectById(CONTRACT_ID))
                .thenReturn(contract(CONTRACT_ID, 10L, "1000.00"));
        StlSettlement command = new StlSettlement();
        command.setContractId(CONTRACT_ID);
        command.setDeductionAmount(new BigDecimal("-0.01"));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service().create(command));

        assertEquals("STL_SETTLEMENT_DEDUCTION_INVALID", exception.getCode());
        verify(settlementMapper, never()).insert(any(StlSettlement.class));
    }

    @Test
    void createRejectsNonPerformingContractWithoutWriting() {
        setContext();
        CtContract contract = contract(CONTRACT_ID, 10L, "1000.00");
        contract.setContractStatus("TERMINATED");
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(contract);
        StlSettlement command = new StlSettlement();
        command.setContractId(CONTRACT_ID);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service().create(command));

        assertEquals("SETTLEMENT_CONTRACT_INVALID", exception.getCode());
        verify(settlementMapper, never()).insert(any(StlSettlement.class));
    }

    private void setContext() {
        UserContext.set(Jwts.claims()
                .add("userId", 1L)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .build());
    }

    private StlSettlement draftSettlement(long id, long contractId, long partnerId) {
        StlSettlement settlement = new StlSettlement();
        settlement.setId(id);
        settlement.setTenantId(TENANT_ID);
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(contractId);
        settlement.setPartnerId(partnerId);
        settlement.setApprovalStatus("DRAFT");
        settlement.setSettlementStatus("DRAFT");
        settlement.setSettlementType("FINAL");
        return settlement;
    }

    private CtContract contract(long id, long partnerId, String amount) {
        CtContract contract = new CtContract();
        contract.setId(id);
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(PROJECT_ID);
        contract.setPartyBId(partnerId);
        contract.setContractType("SUB");
        contract.setApprovalStatus("APPROVED");
        contract.setContractStatus("PERFORMING");
        contract.setCurrentAmount(new BigDecimal(amount));
        return contract;
    }

    private SubMeasure approvedMeasure(long id, long contractId, long partnerId, String amount) {
        SubMeasure measure = new SubMeasure();
        measure.setId(id);
        measure.setTenantId(TENANT_ID);
        measure.setProjectId(PROJECT_ID);
        measure.setContractId(contractId);
        measure.setPartnerId(partnerId);
        measure.setMeasureCode("SM-" + id);
        measure.setApprovalStatus("APPROVED");
        measure.setApprovedAmount(new BigDecimal(amount));
        measure.setNetAmount(new BigDecimal(amount));
        return measure;
    }

    private SubMeasureItem measuredItem(long measureId, long contractItemId, String quantity) {
        SubMeasureItem item = new SubMeasureItem();
        item.setTenantId(TENANT_ID);
        item.setMeasureId(measureId);
        item.setContractItemId(contractItemId);
        item.setCurrentQuantity(new BigDecimal(quantity));
        return item;
    }

    private CtContractItem contractItem(long id, long contractId, String unitPrice) {
        CtContractItem item = new CtContractItem();
        item.setId(id);
        item.setTenantId(TENANT_ID);
        item.setContractId(contractId);
        item.setItemName("合同清单-" + id);
        item.setUnit("m³");
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }

    private StlSettlementWriteService service() {
        return new StlSettlementWriteService(
                settlementMapper, settlementItemMapper, contractMapper, workflowEngine, queryService,
                settlementSubMeasureMapper, subMeasureMapper, subMeasureItemMapper, contractItemMapper,
                fileLifecycleGateway, fileMapper, projectMapper,
                projectAccessChecker, wfInstanceMapper, varOrderMapper, payRecordMapper);
    }
}
