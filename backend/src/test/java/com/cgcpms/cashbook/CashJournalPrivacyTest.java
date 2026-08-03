package com.cgcpms.cashbook;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.mapper.BidDepositMapper;
import com.cgcpms.budget.service.ContractBudgetAllocationService;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalQuery;
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
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
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
    void bidOnlySummaryReturnsOnlyBidMetrics() {
        authenticate("bid:cost:query");
        CashJournalQuery query = bidQuery();
        when(bidCostMapper.selectById(BID_COST_ID)).thenReturn(bidCost());
        when(entryMapper.selectList(any())).thenReturn(List.of(
                entry(CashbookConstants.Direction.OUT, "100.00"),
                entry(CashbookConstants.Direction.IN, "30.00")));
        when(bidDepositMapper.selectList(any())).thenReturn(List.of());

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
        verify(fundAccountMapper, never()).selectList(any());
        verify(fundAccountMapper, never()).selectCurrentBalance(any(), any());
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

    private CashJournalEntry entry(String direction, String amount) {
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TENANT_ID);
        entry.setBidCostId(BID_COST_ID);
        entry.setDirection(direction);
        entry.setAmount(new BigDecimal(amount));
        return entry;
    }
}
