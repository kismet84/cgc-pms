package com.cgcpms.cost.strategy;

import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.mapper.CostItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractCostStrategyTest {

    @Mock private CtContractMapper contractMapper;
    @Mock private CtContractItemMapper contractItemMapper;
    @Mock private CostItemMapper costItemMapper;
    @Mock private CostSubjectResolver costSubjectResolver;
    @InjectMocks private ContractCostStrategy strategy;

    @Test
    void mainContractIsIncomeAndMustNotGenerateLockedCost() {
        CtContract contract = new CtContract();
        contract.setId(100L);
        contract.setContractType("MAIN");
        when(contractMapper.selectById(100L)).thenReturn(contract);

        strategy.generateCost(100L);

        verify(contractItemMapper, never()).selectList(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(costItemMapper, costSubjectResolver);
    }
}
