package com.cgcpms.bid;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.bid.dto.BidCostOption;
import com.cgcpms.bid.dto.BidOwnerOption;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.bid.service.BidDocumentVersionService;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostFactLineageResolver;
import com.cgcpms.cost.strategy.CostSubjectResolver;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidCostServiceTest {

    @Mock BidCostMapper mapper;
    @Mock CostItemMapper costItemMapper;
    @Mock CostSubjectResolver costSubjectResolver;
    @Mock CostFactLineageResolver costFactLineageResolver;
    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock CodeGenerationService codeGenerationService;
    @Mock BidDocumentVersionService documentService;
    @Mock AccountingPeriodGuard accountingPeriodGuard;

    private BidCostService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        if (TableInfoHelper.getTableInfo(BidCost.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("BidCostServiceTest");
            TableInfoHelper.initTableInfo(assistant, BidCost.class);
        }
        service = new BidCostService(mapper, costItemMapper, costSubjectResolver, costFactLineageResolver,
                projectAccessChecker, codeGenerationService,
                documentService, Optional.empty(), accountingPeriodGuard);
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

    @Test
    void ownerCandidatesRequireAnEnabledRoleWithBidWritePermission() throws NoSuchMethodException {
        Select select = BidCostMapper.class
                .getMethod("selectOwnerOptions", Long.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertTrue(sql.contains("FROM sys_user_role"));
        assertTrue(sql.contains("FROM sys_role_menu"));
        assertTrue(sql.contains("m.perms IN ('bid:add','bid:edit')"));
        assertTrue(sql.contains("r.status='ENABLE'"));
        assertTrue(sql.contains("u.status IN ('ACTIVE', 'ENABLE')"));
        assertTrue(sql.contains("r.role_code IN ('ADMIN','SUPER_ADMIN')"));
    }

    @Test
    void changedOwnerMustRemainInAuthoritativeCandidates() {
        BidCost existing = new BidCost();
        existing.setId(12L);
        existing.setTenantId(TestUserContext.TENANT_0);
        existing.setBidStatus("PREPARING");
        when(mapper.selectById(12L)).thenReturn(existing);
        when(mapper.selectOwnerOptions(TestUserContext.TENANT_0))
                .thenReturn(List.of(new BidOwnerOption(21L, "投标负责人")));

        BidCost accepted = new BidCost();
        accepted.setId(12L);
        accepted.setOwnerId(21L);
        when(mapper.update(any(), any())).thenReturn(1);
        assertDoesNotThrow(() -> service.update(accepted));

        BidCost rejected = new BidCost();
        rejected.setId(12L);
        rejected.setOwnerId(22L);
        assertEquals("BID_OWNER_INVALID", assertThrows(
                com.cgcpms.common.exception.BusinessException.class,
                () -> service.update(rejected)).getCode());
    }

    @Test
    void costOptionsUseAccessibleProjectScope() {
        List<Long> projectIds = List.of(11L, 12L);
        List<BidCostOption> options = List.of(new BidCostOption(21L, "BID-021", "授权投标"));
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(projectIds);
        when(mapper.selectCostOptions(TestUserContext.TENANT_0, projectIds)).thenReturn(options);

        assertEquals(options, service.listCostOptions());

        verify(mapper).selectCostOptions(TestUserContext.TENANT_0, projectIds);
    }

    @Test
    void costOptionsKeepEmptyProjectScopeFailClosed() throws NoSuchMethodException {
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(List.of());
        when(mapper.selectCostOptions(TestUserContext.TENANT_0, List.of())).thenReturn(List.of());

        assertEquals(List.of(), service.listCostOptions());

        verify(mapper).selectCostOptions(TestUserContext.TENANT_0, List.of());

        Select select = BidCostMapper.class
                .getMethod("selectCostOptions", Long.class, List.class)
                .getAnnotation(Select.class);
        var sqlSource = new XMLLanguageDriver().createSqlSource(
                new MybatisConfiguration(), String.join(" ", select.value()), Map.class);
        String emptySql = sqlSource.getBoundSql(Map.of(
                "tenantId", TestUserContext.TENANT_0,
                "accessibleProjectIds", List.of())).getSql();
        String scopedSql = sqlSource.getBoundSql(Map.of(
                "tenantId", TestUserContext.TENANT_0,
                "accessibleProjectIds", List.of(11L))).getSql();

        assertTrue(emptySql.contains("project_id IS NULL"));
        assertFalse(emptySql.contains("project_id IN"));
        assertTrue(scopedSql.contains("project_id IN"));
    }
}
