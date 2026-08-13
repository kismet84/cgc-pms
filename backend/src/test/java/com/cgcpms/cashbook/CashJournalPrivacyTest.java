package com.cgcpms.cashbook;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.mapper.BidDepositMapper;
import com.cgcpms.budget.service.ContractBudgetAllocationService;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.dto.CashJournalUpdateRequest;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.mapper.CashJournalChangeLogMapper;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.service.CashJournalAlertService;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.cashbook.service.FundAccountService;
import com.cgcpms.cashbook.service.PaymentArchiveEvidenceService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashJournalPrivacyTest {

    private static final long TENANT_ID = 934067L;
    private static final long BID_COST_ID = 67L;

    @Mock private CashJournalEntryMapper entryMapper;
    @Mock private FundAccountMapper fundAccountMapper;
    @Mock private FundAccountService fundAccountService;
    @Mock private CtContractMapper contractMapper;
    @Mock private ProjectAccessChecker projectAccessChecker;
    @Mock private CashJournalChangeLogMapper changeLogMapper;
    @Mock private SysFileMapper sysFileMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private CashJournalAlertService alertService;
    @Mock private AccountingPeriodGuard periodGuard;
    @Mock private PayRecordMapper payRecordMapper;
    @Mock private PayApplicationMapper payApplicationMapper;
    @Mock private PaymentApplicationSourceService paymentSourceService;
    @Mock private ContractBudgetAllocationService contractBudgetAllocationService;
    @Mock private PaymentArchiveEvidenceService paymentArchiveEvidenceService;
    @Mock private BidCostMapper bidCostMapper;
    @Mock private BidDepositMapper bidDepositMapper;
    @Mock private CostSubjectMapper costSubjectMapper;

    @InjectMocks
    private CashJournalService service;

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void bidOnlyQueryWithoutBidCostFailsClosed() {
        authenticate("bid:cost:query");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.page(new CashJournalQuery()));

        assertEquals("BID_COST_QUERY_SCOPE_REQUIRED", error.getCode());
        verify(entryMapper, never()).selectPageWithBalance(any(), any(), any(), any());
    }

    @Test
    void bidOnlyQueryAllowsServerScopedBidLedger() {
        authenticate("bid:cost:query");
        CashJournalQuery query = new CashJournalQuery();
        query.setCostSubjectRootCode("5401.01");
        Page<CashJournalEntryVO> selected = new Page<>(1, 20);
        when(entryMapper.selectPageWithBalance(any(), eq(TENANT_ID), same(query), any()))
                .thenReturn(selected);

        service.page(query);

        verify(entryMapper).selectPageWithBalance(any(), eq(TENANT_ID), same(query), any());
    }

    @Test
    void bidOnlySummaryReturnsOnlyBidMetrics() {
        authenticate("bid:cost:query");
        CashJournalQuery query = bidQuery();
        when(bidCostMapper.selectById(BID_COST_ID)).thenReturn(bidCost());
        when(entryMapper.selectSummaryAggregate(eq(TENANT_ID), any())).thenReturn(
                new CashJournalEntryMapper.CashJournalAggregate(
                        new BigDecimal("30.00"), new BigDecimal("100.00"), BigDecimal.ZERO, 0L));
        when(bidDepositMapper.selectOutstandingTotal(TENANT_ID, BID_COST_ID)).thenReturn(BigDecimal.ZERO);

        var summary = service.summary(query);

        assertNull(summary.getCashBalance());
        assertNull(summary.getBankBalance());
        assertNull(summary.getIncome());
        assertNull(summary.getExpense());
        assertEquals(0L, summary.getPendingCount());
        assertEquals("100.00", summary.getCumulativeCashOut());
        assertEquals("30.00", summary.getCumulativeCashIn());
        assertEquals("70.00", summary.getCashNetOutflow());
        assertEquals("0.00", summary.getActualBidExpense());
        assertEquals("0.00", summary.getOutstandingDeposit());
        verify(fundAccountMapper, never()).selectBalancesByType(any(), any(), anyBoolean());
        verify(projectAccessChecker, never()).checkAccess(anyLong(), anyString());
    }

    @Test
    void inaccessibleBoundBidSummaryFailsBeforeAmountsAreRead() {
        authenticate("bid:cost:query");
        BidCost bid = bidCost();
        bid.setProjectId(9L);
        when(bidCostMapper.selectById(BID_COST_ID)).thenReturn(bid);
        doThrow(new BusinessException("PROJECT_ACCESS_DENIED", "无权访问投标项目"))
                .when(projectAccessChecker).checkAccess(9L, "访问投标成本");

        BusinessException error = assertThrows(BusinessException.class, () -> service.summary(bidQuery()));

        assertEquals("PROJECT_ACCESS_DENIED", error.getCode());
        verify(entryMapper, never()).selectSummaryAggregate(any(), any());
        verify(bidDepositMapper, never()).selectOutstandingTotal(any(), any());
    }

    @Test
    void accessibleBoundBidSummaryUsesSharedProjectGate() {
        authenticate("bid:cost:query");
        BidCost bid = bidCost();
        bid.setProjectId(9L);
        when(bidCostMapper.selectById(BID_COST_ID)).thenReturn(bid);
        when(entryMapper.selectSummaryAggregate(eq(TENANT_ID), any())).thenReturn(
                CashJournalEntryMapper.CashJournalAggregate.empty());
        when(bidDepositMapper.selectOutstandingTotal(TENANT_ID, BID_COST_ID)).thenReturn(BigDecimal.ZERO);

        service.summary(bidQuery());

        verify(projectAccessChecker).checkAccess(9L, "访问投标成本");
    }

    @Test
    void generalQueriesScopeHistoricalNullProjectBidRowsBeforeRead() throws Exception {
        authenticate("cashbook:journal:query");
        if (TableInfoHelper.getTableInfo(CashJournalEntry.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), CashJournalEntry.class);
        }
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(List.of(9L));
        List<String> wrapperSql = new ArrayList<>();
        when(entryMapper.selectSummaryAggregate(eq(TENANT_ID), any())).thenAnswer(invocation -> {
            Wrapper<CashJournalEntry> wrapper = invocation.getArgument(1);
            wrapperSql.add(wrapper.getSqlSegment());
            return CashJournalEntryMapper.CashJournalAggregate.empty();
        });
        when(fundAccountMapper.selectBalancesByType(TENANT_ID, null, false)).thenReturn(List.of());

        service.summary(new CashJournalQuery());

        assertEquals(1, wrapperSql.size());
        assertTrue(wrapperSql.stream().allMatch(sql -> sql.contains("bid_cost_id IS NULL")
                && sql.contains("b.project_id IS NULL OR b.project_id IN (")));
        verify(projectAccessChecker, times(1)).accessibleProjectIds();

        MybatisConfiguration configuration = new MybatisConfiguration();
        String resource = "mapper/cashbook/CashJournalEntryMapper.xml";
        try (var input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(
                "com.cgcpms.cashbook.mapper.CashJournalEntryMapper.selectPageWithBalance");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", TENANT_ID);
        parameters.put("query", new CashJournalQuery());
        parameters.put("accessibleProjectIds", List.of(9L));
        String scopedSql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
        parameters.put("accessibleProjectIds", List.of());
        String emptyScopeSql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");

        assertTrue(scopedSql.contains("e.project_id IN"));
        assertTrue(scopedSql.contains("e.bid_cost_id IS NULL OR EXISTS"));
        assertTrue(scopedSql.contains("b.project_id IS NULL OR b.project_id IN"));
        assertFalse(emptyScopeSql.contains("e.project_id IN"));
        assertFalse(emptyScopeSql.contains("b.project_id IN"));
        assertTrue(emptyScopeSql.contains("e.bid_cost_id IS NULL OR EXISTS"));
    }

    @Test
    void summaryUsesOneJournalAndOneAccountAggregate() {
        authenticate("cashbook:journal:query");
        when(projectAccessChecker.accessibleProjectIds()).thenReturn(List.of(9L));
        when(entryMapper.selectSummaryAggregate(eq(TENANT_ID), any())).thenReturn(
                new CashJournalEntryMapper.CashJournalAggregate(
                        new BigDecimal("10.00"), new BigDecimal("4.00"),
                        new BigDecimal("3.00"), 2L));
        when(fundAccountMapper.selectBalancesByType(TENANT_ID, null, false)).thenReturn(List.of(
                new FundAccountMapper.AccountTypeBalance(CashbookConstants.AccountType.CASH,
                        new BigDecimal("101.00")),
                new FundAccountMapper.AccountTypeBalance(CashbookConstants.AccountType.BANK,
                        new BigDecimal("202.00"))));

        var summary = service.summary(new CashJournalQuery());

        assertEquals("101.00", summary.getCashBalance());
        assertEquals("202.00", summary.getBankBalance());
        assertEquals("10.00", summary.getIncome());
        assertEquals("4.00", summary.getExpense());
        assertEquals(2L, summary.getPendingCount());
        assertEquals("3.00", summary.getActualBidExpense());
        verify(projectAccessChecker, times(1)).accessibleProjectIds();
        verify(entryMapper, times(1)).selectSummaryAggregate(eq(TENANT_ID), any());
        verify(fundAccountMapper, times(1)).selectBalancesByType(TENANT_ID, null, false);
        verify(entryMapper, never()).selectList(any());
        verify(fundAccountMapper, never()).selectList(any());
        verify(fundAccountMapper, never()).selectCurrentBalance(any(), any());
        verify(costSubjectMapper, never()).selectBatchIds(any());
        verify(bidDepositMapper, never()).selectList(any());
    }

    @Test
    void bidOnlyPageRedactsGlobalRunningBalance() {
        authenticate("bid:cost:query");
        CashJournalQuery query = bidQuery();
        when(bidCostMapper.selectById(BID_COST_ID)).thenReturn(bidCost());
        CashJournalEntryVO row = new CashJournalEntryVO();
        row.setRunningBalance("999999.99");
        Page<CashJournalEntryVO> selected = new Page<>(1, 20);
        selected.setRecords(new ArrayList<>(List.of(row)));
        when(entryMapper.selectPageWithBalance(any(), eq(TENANT_ID), same(query), any())).thenReturn(selected);

        var result = service.page(query);

        assertNull(result.getRecords().getFirst().getRunningBalance());
    }

    @Test
    void cashbookQueryKeepsExistingBalancesForBidFilter() {
        authenticate("cashbook:journal:query");
        CashJournalQuery query = bidQuery();
        when(bidCostMapper.selectById(BID_COST_ID)).thenReturn(bidCost());
        CashJournalEntryVO row = new CashJournalEntryVO();
        row.setRunningBalance("321.00");
        Page<CashJournalEntryVO> selected = new Page<>(1, 20);
        selected.setRecords(new ArrayList<>(List.of(row)));
        when(entryMapper.selectPageWithBalance(any(), eq(TENANT_ID), same(query), any())).thenReturn(selected);

        assertEquals("321.00", service.page(query).getRecords().getFirst().getRunningBalance());
    }

    @Test
    void bidEntryRejectsClientProjectThatDoesNotMatchBidFact() {
        authenticate("bid:cost:maintain");
        BidCost bid = bidCost();
        bid.setProjectId(9L);
        when(bidCostMapper.selectById(BID_COST_ID)).thenReturn(bid);
        CostSubject subject = subject(101L, 100L, "5401.01.01", "COST");
        when(costSubjectMapper.selectById(101L)).thenReturn(subject);
        when(costSubjectMapper.selectById(100L)).thenReturn(subject(100L, null, "5401.01", "COST"));
        CashJournalCreateRequest request = new CashJournalCreateRequest();
        request.setDirection(CashbookConstants.Direction.OUT);
        request.setAmount(new BigDecimal("1.00"));
        request.setBusinessDate(LocalDate.now());
        request.setSummary("投标费");
        request.setProjectId(8L);
        request.setBidCostId(BID_COST_ID);
        request.setCostSubjectId(101L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createManual(request));

        assertEquals("BID_COST_PROJECT_MISMATCH", error.getCode());
        verify(entryMapper, never()).insert(any(CashJournalEntry.class));
    }

    @Test
    void cashbookMaintainerCannotAttachBidWithoutBidMaintainAuthority() {
        authenticate("cashbook:journal:maintain");
        CashJournalEntry entry = new CashJournalEntry();
        entry.setId(501L);
        entry.setTenantId(TENANT_ID);
        entry.setSourceType(CashbookConstants.SourceType.MANUAL);
        entry.setStatus(CashbookConstants.Status.DRAFT);
        when(entryMapper.selectByIdForUpdate(501L, TENANT_ID)).thenReturn(entry);
        CashJournalUpdateRequest request = new CashJournalUpdateRequest();
        request.setBidCostId(BID_COST_ID);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateDraft(501L, request));

        assertEquals("BID_COST_ACCESS_DENIED", error.getCode());
        verify(bidCostMapper, never()).selectById(any());
    }

    private void authenticate(String... authorities) {
        TestUserContext.setUser(TENANT_ID, 7L, "privacy-test", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "privacy-test", "n/a", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private CashJournalQuery bidQuery() {
        CashJournalQuery query = new CashJournalQuery();
        query.setBidCostId(BID_COST_ID);
        return query;
    }

    private BidCost bidCost() {
        BidCost bid = new BidCost();
        bid.setId(BID_COST_ID);
        bid.setTenantId(TENANT_ID);
        return bid;
    }

    private CostSubject subject(Long id, Long parentId, String code, String category) {
        CostSubject subject = new CostSubject();
        subject.setId(id);
        subject.setTenantId(TENANT_ID);
        subject.setParentId(parentId);
        subject.setSubjectCode(code);
        subject.setSubjectName(code);
        subject.setAccountCategory(category);
        subject.setStatus("ENABLE");
        return subject;
    }

    private CashJournalEntry entry(String direction, String amount) {
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TENANT_ID);
        entry.setBidCostId(BID_COST_ID);
        entry.setDirection(direction);
        entry.setAmount(new BigDecimal(amount));
        return entry;
    }
}
