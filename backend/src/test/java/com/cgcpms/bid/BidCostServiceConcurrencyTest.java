package com.cgcpms.bid;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.bid.service.BidDocumentVersionService;
import com.cgcpms.bid.service.BidAwardProjectCreator;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidCostServiceConcurrencyTest {

    @Mock BidCostMapper mapper;
    @Mock CostItemMapper costItemMapper;
    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock CodeGenerationService codeGenerationService;
    @Mock BidDocumentVersionService documentService;
    @Mock BidAwardProjectCreator awardProjectCreator;

    private BidCostService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        if (TableInfoHelper.getTableInfo(BidCost.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("BidCostServiceConcurrencyTest");
            TableInfoHelper.initTableInfo(assistant, BidCost.class);
        }
        service = new BidCostService(mapper, costItemMapper, projectAccessChecker, codeGenerationService,
                documentService, java.util.Optional.of(awardProjectCreator));
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void updateFailsClosedWhenConditionalWriteLosesRace() {
        BidCost current = bidding(1L);
        when(mapper.selectById(1L)).thenReturn(current);
        when(mapper.update(isNull(), any())).thenReturn(0);
        BidCost command = new BidCost();
        command.setId(1L);
        command.setBidProjectName("并发修改");

        assertConcurrent(() -> service.update(command));
    }

    @Test
    void deleteFailsClosedWhenConditionalWriteLosesRace() {
        when(mapper.selectById(2L)).thenReturn(bidding(2L));
        when(mapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

        assertConcurrent(() -> service.delete(2L));
    }

    @Test
    void markWonFailsClosedWhenConditionalWriteLosesRace() {
        BidCost current = bidding(3L);
        current.setBidStatus("EVALUATING");
        when(mapper.selectById(3L)).thenReturn(current);
        when(documentService.hasCurrentFinal(3L, "RESULT", "AWARD_NOTICE")).thenReturn(true);
        when(awardProjectCreator.createOrGet(any())).thenReturn(10001L);
        when(mapper.update(isNull(), any())).thenReturn(0);

        assertConcurrent(() -> service.changeStatus(3L, "EVALUATING", "WON", null));
    }

    @Test
    void markLostFailsBeforeCostWriteWhenConditionalWriteLosesRace() {
        BidCost current = bidding(4L);
        current.setBidStatus("EVALUATING");
        when(mapper.selectById(4L)).thenReturn(current);
        when(documentService.hasCurrentFinalResult(4L)).thenReturn(true);
        when(mapper.update(isNull(), any())).thenReturn(0);

        assertConcurrent(() -> service.changeStatus(4L, "EVALUATING", "LOST", "未中标"));
        verify(costItemMapper, never()).update(any(), any());
    }

    private BidCost bidding(Long id) {
        BidCost bid = new BidCost();
        bid.setId(id);
        bid.setTenantId(TestUserContext.TENANT_0);
        bid.setBidStatus("BIDDING");
        bid.setBidProjectName("并发中标项目");
        bid.setTendereeName("招标人");
        bid.setProjectLocation("建设地点");
        bid.setFinalBidPrice(new BigDecimal("100.00"));
        bid.setPlannedStartDate(LocalDate.of(2026, 8, 1));
        bid.setPlannedEndDate(LocalDate.of(2027, 7, 31));
        return bid;
    }

    private void assertConcurrent(Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals("BID_CONCURRENT_STATE_CHANGE", error.getCode());
    }
}
