package com.cgcpms.bid;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.service.BidAwardProjectCreator;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.bid.service.BidDocumentVersionService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class BidWorkflowServiceTest {

    @Mock BidCostMapper mapper;
    @Mock CostItemMapper costItemMapper;
    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock CodeGenerationService codeGenerationService;
    @Mock BidDocumentVersionService documentService;
    @Mock BidAwardProjectCreator projectCreator;

    private BidCostService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        if (TableInfoHelper.getTableInfo(BidCost.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("BidWorkflowServiceTest");
            TableInfoHelper.initTableInfo(assistant, BidCost.class);
        }
        if (TableInfoHelper.getTableInfo(CostItem.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("BidWorkflowServiceTestCostItem");
            TableInfoHelper.initTableInfo(assistant, CostItem.class);
        }
        service = new BidCostService(mapper, costItemMapper, projectAccessChecker, codeGenerationService,
                documentService, Optional.of(projectCreator));
    }

    @AfterEach
    void clear() {
        TestUserContext.clear();
    }

    @Test
    void rejectsIllegalTransition() {
        when(mapper.selectById(1L)).thenReturn(bid(1L, "PREPARING"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(1L, "PREPARING", "WON", null));

        assertEquals("BID_STATUS_TRANSITION_INVALID", error.getCode());
        verify(projectCreator, never()).createOrGet(any());
    }

    @Test
    void wonRequiresCurrentFinalAwardNotice() {
        when(mapper.selectById(2L)).thenReturn(bid(2L, "EVALUATING"));
        when(documentService.hasCurrentFinal(2L, "RESULT", "AWARD_NOTICE")).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(2L, "EVALUATING", "WON", null));

        assertEquals("BID_AWARD_NOTICE_REQUIRED", error.getCode());
        verify(projectCreator, never()).createOrGet(any());
    }

    @Test
    void wonCreatesProjectAndUsesStateCas() {
        when(mapper.selectById(3L)).thenReturn(bid(3L, "EVALUATING"));
        when(documentService.hasCurrentFinal(3L, "RESULT", "AWARD_NOTICE")).thenReturn(true);
        when(projectCreator.createOrGet(any())).thenReturn(99L);
        when(mapper.update(isNull(), any())).thenReturn(1);

        assertEquals(99L, service.changeStatus(3L, "EVALUATING", "WON", null));
        ArgumentCaptor<BidAwardProjectCreator.BidAwardProjectCommand> command =
                ArgumentCaptor.forClass(BidAwardProjectCreator.BidAwardProjectCommand.class);
        verify(projectCreator).createOrGet(command.capture());
        assertEquals("招标人", command.getValue().ownerUnit());
        assertEquals("建设地点", command.getValue().projectAddress());
        assertEquals(new BigDecimal("100.00"), command.getValue().contractAmount());
        assertEquals(LocalDate.of(2026, 8, 1), command.getValue().plannedStartDate());
        assertEquals(LocalDate.of(2027, 7, 31), command.getValue().plannedEndDate());
    }

    @Test
    void wonRejectsMissingOrZeroProjectMappingBeforeCreation() {
        BidCost bid = bid(31L, "EVALUATING");
        bid.setFinalBidPrice(BigDecimal.ZERO);
        when(mapper.selectById(31L)).thenReturn(bid);
        when(documentService.hasCurrentFinal(31L, "RESULT", "AWARD_NOTICE")).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(31L, "EVALUATING", "WON", null));

        assertEquals("BID_AWARD_PROJECT_INVALID", error.getCode());
        verify(projectCreator, never()).createOrGet(any());
    }

    @Test
    void wonRejectsReversedProjectPlanDates() {
        BidCost bid = bid(311L, "EVALUATING");
        bid.setPlannedStartDate(LocalDate.of(2027, 8, 1));
        bid.setPlannedEndDate(LocalDate.of(2027, 7, 31));
        when(mapper.selectById(311L)).thenReturn(bid);
        when(documentService.hasCurrentFinal(311L, "RESULT", "AWARD_NOTICE")).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(311L, "EVALUATING", "WON", null));

        assertEquals("BID_AWARD_PROJECT_INVALID", error.getCode());
        verify(projectCreator, never()).createOrGet(any());
    }

    @Test
    void legacyWonFromBiddingStillRequiresAwardNotice() {
        when(mapper.selectById(32L)).thenReturn(bid(32L, "BIDDING"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.markAsWon(32L, 999L));

        assertEquals("BID_AWARD_NOTICE_REQUIRED", error.getCode());
        verify(projectCreator, never()).createOrGet(any());
    }

    @Test
    void legacyLostFromPreparingUsesUnifiedStateWrite() {
        BidCost bid = bid(33L, "PREPARING");
        bid.setSourceUrl("https://example.test/result");
        when(mapper.selectById(33L)).thenReturn(bid);
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.markAsLost(33L);

        verify(mapper).update(isNull(), any());
        verify(costItemMapper).update(isNull(), any());
    }

    @Test
    void crossTenantBidIsHiddenBeforeStateEvaluation() {
        BidCost bid = bid(34L, "EVALUATING");
        bid.setTenantId(999L);
        when(mapper.selectById(34L)).thenReturn(bid);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(34L, "EVALUATING", "LOST", "未中标"));

        assertEquals("BID_COST_NOT_FOUND", error.getCode());
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void deleteRejectsBidWithDocumentFacts() {
        when(mapper.selectById(35L)).thenReturn(bid(35L, "PREPARING"));
        when(mapper.countDocumentVersions(TestUserContext.TENANT_0, 35L)).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(35L));

        assertEquals("BID_COST_HAS_FACTS", error.getCode());
        verify(mapper, never()).delete(any());
    }

    @Test
    void legacyBiddingInputNormalizesBeforeCas() {
        when(mapper.selectById(4L)).thenReturn(bid(4L, "BIDDING"));
        when(documentService.hasCurrentFinalGroup(4L, "TENDER")).thenReturn(true);
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.changeStatus(4L, "BIDDING", "SUBMITTED", null);

        verify(mapper).update(isNull(), any());
    }

    @Test
    void finalizedTenderAutomaticallyAdvancesRegisteredBid() {
        when(mapper.selectById(41L)).thenReturn(bid(41L, "PREPARING"));
        when(documentService.hasCurrentFinalGroup(41L, "TENDER")).thenReturn(true);
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.advanceStatus(new BidDocumentVersionService.BidDocumentFinalizedEvent(41L));

        verify(mapper).update(isNull(), any());
    }

    @Test
    void finalizedAwardNoticeAutomaticallyCreatesProjectAndWins() {
        when(mapper.selectById(42L)).thenReturn(bid(42L, "EVALUATING"));
        when(documentService.hasCurrentFinal(42L, "RESULT", "AWARD_NOTICE")).thenReturn(true);
        when(projectCreator.createOrGet(any())).thenReturn(420L);
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.advanceStatus(new BidDocumentVersionService.BidDocumentFinalizedEvent(42L));

        verify(projectCreator).createOrGet(any());
        verify(mapper).update(isNull(), any());
    }

    @Test
    void adverseResultRequiresEvidenceOrExternalSource() {
        when(mapper.selectById(5L)).thenReturn(bid(5L, "EVALUATING"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(5L, "EVALUATING", "LOST", "未中标"));

        assertEquals("BID_RESULT_EVIDENCE_REQUIRED", error.getCode());
    }

    private BidCost bid(Long id, String status) {
        BidCost bid = new BidCost();
        bid.setId(id);
        bid.setTenantId(TestUserContext.TENANT_0);
        bid.setBidCode("BID-TEST");
        bid.setBidProjectName("测试投标");
        bid.setTendereeName("招标人");
        bid.setProjectLocation("建设地点");
        bid.setFinalBidPrice(new BigDecimal("100.00"));
        bid.setOpeningAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        bid.setPlannedStartDate(LocalDate.of(2026, 8, 1));
        bid.setPlannedEndDate(LocalDate.of(2027, 7, 31));
        bid.setBidStatus(status);
        return bid;
    }
}
