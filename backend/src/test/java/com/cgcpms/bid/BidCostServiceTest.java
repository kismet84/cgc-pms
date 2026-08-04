package com.cgcpms.bid;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.bid.service.BidDocumentVersionService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidCostServiceTest {

    @Mock BidCostMapper mapper;
    @Mock CostItemMapper costItemMapper;
    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock CodeGenerationService codeGenerationService;
    @Mock BidDocumentVersionService documentService;

    private BidCostService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        if (TableInfoHelper.getTableInfo(BidCost.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("BidCostServiceTest");
            TableInfoHelper.initTableInfo(assistant, BidCost.class);
        }
        service = new BidCostService(mapper, costItemMapper, projectAccessChecker, codeGenerationService,
                documentService, Optional.empty());
    }

    @AfterEach
    void clear() {
        TestUserContext.clear();
    }

    @Test
    void createPersistsExtendedFieldsAndOverwritesClientOwnedState() {
        when(codeGenerationService.nextCode(any(), any(), anyString(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn("BID-20260803-001");
        doAnswer(invocation -> {
            BidCost value = invocation.getArgument(0);
            value.setId(10L);
            return 1;
        }).when(mapper).insert(any(BidCost.class));

        BidCost bid = new BidCost();
        bid.setTenantId(999L);
        bid.setProjectId(88L);
        bid.setBidStatus("WON");
        bid.setBidProjectName("工程投标");
        bid.setBidSectionName("一标段");
        bid.setTendereeName("招标人");
        bid.setFinalBidPrice(new BigDecimal("1000000.00"));

        assertEquals(10L, service.create(bid));
        assertEquals(TestUserContext.TENANT_0, bid.getTenantId());
        assertEquals("PREPARING", bid.getBidStatus());
        assertNull(bid.getProjectId());
        assertEquals("一标段", bid.getBidSectionName());
        assertEquals(new BigDecimal("1000000.00"), bid.getFinalBidPrice());
    }

    @Test
    void evaluationBidRemainsEditableUntilResult() {
        BidCost existing = new BidCost();
        existing.setId(11L);
        existing.setTenantId(TestUserContext.TENANT_0);
        existing.setBidStatus("EVALUATING");
        when(mapper.selectById(11L)).thenReturn(existing);
        when(mapper.update(any(), any())).thenReturn(1);

        BidCost command = new BidCost();
        command.setId(11L);
        command.setBidProjectName("待中标工程");

        assertDoesNotThrow(() -> service.update(command));
    }
}
