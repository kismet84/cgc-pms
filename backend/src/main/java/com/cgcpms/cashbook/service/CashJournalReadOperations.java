package com.cgcpms.cashbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.bid.mapper.BidDepositMapper;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.entity.CashJournalChangeLog;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.mapper.CashJournalChangeLogMapper;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import com.cgcpms.cashbook.vo.CashJournalSummaryVO;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.cgcpms.cashbook.service.CashJournalService.tenantId;
import static com.cgcpms.cashbook.service.CashJournalViewAssembler.money;

final class CashJournalReadOperations {

    private final CashJournalEntryMapper entryMapper;
    private final FundAccountMapper fundAccountMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final CashJournalChangeLogMapper changeLogMapper;
    private final SysFileMapper sysFileMapper;
    private final BidDepositMapper bidDepositMapper;
    private final CashJournalViewAssembler assembler;

    CashJournalReadOperations(CashJournalEntryMapper entryMapper,
                              FundAccountMapper fundAccountMapper,
                              ProjectAccessChecker projectAccessChecker,
                              CashJournalChangeLogMapper changeLogMapper,
                              SysFileMapper sysFileMapper,
                              BidDepositMapper bidDepositMapper,
                              CostSubjectMapper costSubjectMapper) {
        this.entryMapper = entryMapper;
        this.fundAccountMapper = fundAccountMapper;
        this.projectAccessChecker = projectAccessChecker;
        this.changeLogMapper = changeLogMapper;
        this.sysFileMapper = sysFileMapper;
        this.bidDepositMapper = bidDepositMapper;
        this.assembler = new CashJournalViewAssembler(costSubjectMapper);
    }

    IPage<CashJournalEntryVO> page(CashJournalQuery query, boolean bidOnly) {
        IPage<CashJournalEntryVO> page = entryMapper.selectPageWithBalance(
                new Page<>(query.getPageNo(), query.getPageSize()), tenantId(), query,
                query.getProjectId() == null ? projectAccessChecker.accessibleProjectIds() : List.of(query.getProjectId()));
        if (bidOnly) page.getRecords().forEach(entry -> entry.setRunningBalance(null));
        return page;
    }

    CashJournalSummaryVO summary(CashJournalQuery query, boolean bidOnly) {
        Long tenantId = tenantId();
        List<Long> accessibleProjectIds = query.getProjectId() == null
                ? projectAccessChecker.accessibleProjectIds()
                : List.of(query.getProjectId());
        CashJournalEntryMapper.CashJournalAggregate aggregate = entryMapper.selectSummaryAggregate(
                tenantId, baseWrapper(query, accessibleProjectIds));
        if (aggregate == null) aggregate = CashJournalEntryMapper.CashJournalAggregate.empty();
        BigDecimal cashOut = aggregate.getCashOut() == null ? BigDecimal.ZERO : aggregate.getCashOut();
        BigDecimal cashIn = aggregate.getCashIn() == null ? BigDecimal.ZERO : aggregate.getCashIn();
        BigDecimal actualBidExpense = aggregate.getActualBidExpense() == null
                ? BigDecimal.ZERO : aggregate.getActualBidExpense();

        CashJournalSummaryVO summary = new CashJournalSummaryVO();
        if (!bidOnly) {
            BigDecimal cash = BigDecimal.ZERO;
            BigDecimal bank = BigDecimal.ZERO;
            for (FundAccountMapper.AccountTypeBalance account
                    : fundAccountMapper.selectBalancesByType(tenantId, query.getAccountId(), false)) {
                if (CashbookConstants.AccountType.CASH.equals(account.getAccountType())) {
                    cash = cash.add(account.getBalance());
                }
                if (CashbookConstants.AccountType.BANK.equals(account.getAccountType())) {
                    bank = bank.add(account.getBalance());
                }
            }
            summary.setCashBalance(money(cash));
            summary.setBankBalance(money(bank));
            summary.setIncome(money(cashIn));
            summary.setExpense(money(cashOut));
            summary.setPendingCount(aggregate.getPendingCount() == null ? 0L : aggregate.getPendingCount());
        }
        summary.setCumulativeCashOut(money(cashOut));
        summary.setCumulativeCashIn(money(cashIn));
        summary.setCashNetOutflow(money(cashOut.subtract(cashIn)));
        summary.setActualBidExpense(money(actualBidExpense));
        summary.setOutstandingDeposit(money(outstandingDeposit(tenantId, query.getBidCostId())));
        return summary;
    }

    CashJournalEntryVO detail(CashJournalEntry entry) {
        CashJournalEntryVO vo = assembler.toVO(entry);
        if (entry.getAccountId() != null) {
            FundAccount account = fundAccountMapper.selectOne(new LambdaQueryWrapper<FundAccount>()
                    .eq(FundAccount::getTenantId, tenantId())
                    .eq(FundAccount::getId, entry.getAccountId()));
            if (account != null) {
                vo.setAccountName(account.getAccountName());
                vo.setAccountType(account.getAccountType());
            }
        }
        vo.setAttachments(sysFileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                        .eq(SysFile::getTenantId, tenantId())
                        .eq(SysFile::getBusinessType, "CASH_JOURNAL")
                        .eq(SysFile::getBusinessId, entry.getId())
                        .orderByDesc(SysFile::getCreatedAt))
                .stream().map(assembler::toFileVO).toList());
        vo.setChangeLogs(changeLogMapper.selectList(new LambdaQueryWrapper<CashJournalChangeLog>()
                .eq(CashJournalChangeLog::getTenantId, tenantId())
                .eq(CashJournalChangeLog::getJournalEntryId, entry.getId())
                .orderByAsc(CashJournalChangeLog::getCreatedAt)));
        return vo;
    }

    byte[] exportCsv(CashJournalQuery query) {
        List<CashJournalEntry> entries = entryMapper.selectList(baseWrapper(query)
                .orderByDesc(CashJournalEntry::getBusinessDate)
                .orderByDesc(CashJournalEntry::getId));
        StringBuilder csv = new StringBuilder("\uFEFF流水号,业务日期,方向,金额,投标ID,成本科目,状态,来源,摘要,往来单位\r\n");
        for (CashJournalEntry entry : entries) {
            csv.append(csv(entry.getEntryNo())).append(',')
                    .append(entry.getBusinessDate()).append(',')
                    .append(entry.getDirection()).append(',')
                    .append(money(entry.getAmount())).append(',')
                    .append(entry.getBidCostId() == null ? "" : entry.getBidCostId()).append(',')
                    .append(csv(entry.getCostSubjectNameSnapshot())).append(',')
                    .append(entry.getStatus()).append(',')
                    .append(entry.getSourceType()).append(',')
                    .append(csv(entry.getSummary())).append(',')
                    .append(csv(entry.getCounterpartyName())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    CashJournalEntryVO toVO(CashJournalEntry entry) {
        return assembler.toVO(entry);
    }

    private LambdaQueryWrapper<CashJournalEntry> baseWrapper(CashJournalQuery query) {
        List<Long> projectIds = query.getProjectId() == null
                ? projectAccessChecker.accessibleProjectIds()
                : List.of(query.getProjectId());
        return baseWrapper(query, projectIds);
    }

    private LambdaQueryWrapper<CashJournalEntry> baseWrapper(CashJournalQuery query,
                                                              List<Long> projectIds) {
        LambdaQueryWrapper<CashJournalEntry> wrapper = new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, tenantId());
        if (query.getAccountId() != null) wrapper.eq(CashJournalEntry::getAccountId, query.getAccountId());
        if (StringUtils.hasText(query.getDirection())) wrapper.eq(CashJournalEntry::getDirection, query.getDirection());
        if (StringUtils.hasText(query.getStatus())) wrapper.eq(CashJournalEntry::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getSourceType())) wrapper.eq(CashJournalEntry::getSourceType, query.getSourceType());
        if (query.getSourceId() != null) wrapper.eq(CashJournalEntry::getSourceId, query.getSourceId());
        if (query.getProjectId() != null) {
            wrapper.and(scope -> scope.eq(CashJournalEntry::getProjectId, query.getProjectId())
                    .or(linked -> linked.isNull(CashJournalEntry::getProjectId)
                            .exists("SELECT 1 FROM bid_cost b WHERE b.tenant_id=cash_journal_entry.tenant_id "
                                    + "AND b.id=cash_journal_entry.bid_cost_id AND b.project_id={0} "
                                    + "AND b.deleted_flag=0", query.getProjectId())));
        } else {
            String accessibleBid = "SELECT 1 FROM bid_cost b "
                    + "WHERE b.tenant_id=cash_journal_entry.tenant_id "
                    + "AND b.id=cash_journal_entry.bid_cost_id AND b.deleted_flag=0 "
                    + "AND (b.project_id IS NULL"
                    + (projectIds.isEmpty() ? "" : " OR b.project_id IN ("
                    + IntStream.range(0, projectIds.size()).mapToObj(i -> "{" + i + "}")
                    .collect(Collectors.joining(",")) + ")")
                    + ")";
            wrapper.and(scope -> {
                scope.and(unprojected -> unprojected.isNull(CashJournalEntry::getProjectId)
                        .and(bid -> bid.isNull(CashJournalEntry::getBidCostId)
                                .or().exists(accessibleBid, projectIds.toArray())));
                if (!projectIds.isEmpty()) scope.or().in(CashJournalEntry::getProjectId, projectIds);
            });
        }
        if (query.getContractId() != null) wrapper.eq(CashJournalEntry::getContractId, query.getContractId());
        if (query.getBidCostId() != null) wrapper.eq(CashJournalEntry::getBidCostId, query.getBidCostId());
        if (query.getCostSubjectId() != null) wrapper.eq(CashJournalEntry::getCostSubjectId, query.getCostSubjectId());
        if (query.getBidDepositId() != null) wrapper.eq(CashJournalEntry::getBidDepositId, query.getBidDepositId());
        if (StringUtils.hasText(query.getCostSubjectRootCode())) {
            wrapper.exists("SELECT 1 FROM cost_subject child JOIN cost_subject root "
                    + "ON root.tenant_id=child.tenant_id AND root.id=child.parent_id AND root.deleted_flag=0 "
                    + "WHERE child.tenant_id=cash_journal_entry.tenant_id "
                    + "AND child.id=cash_journal_entry.cost_subject_id AND child.deleted_flag=0 "
                    + "AND root.subject_code={0}", query.getCostSubjectRootCode().trim());
        }
        if (query.getBusinessDateStart() != null) wrapper.ge(CashJournalEntry::getBusinessDate, query.getBusinessDateStart());
        if (query.getBusinessDateEnd() != null) wrapper.le(CashJournalEntry::getBusinessDate, query.getBusinessDateEnd());
        String attachmentExists = "SELECT 1 FROM sys_file f WHERE f.tenant_id = cash_journal_entry.tenant_id "
                + "AND f.business_type = 'CASH_JOURNAL' AND f.business_id = cash_journal_entry.id "
                + "AND f.deleted_flag = 0";
        if (Boolean.TRUE.equals(query.getHasAttachment())) wrapper.exists(attachmentExists);
        if (Boolean.FALSE.equals(query.getHasAttachment())) wrapper.notExists(attachmentExists);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(CashJournalEntry::getEntryNo, keyword)
                    .or().like(CashJournalEntry::getSummary, keyword)
                    .or().like(CashJournalEntry::getCounterpartyName, keyword));
        }
        return wrapper;
    }

    private BigDecimal outstandingDeposit(Long tenantId, Long bidCostId) {
        if (bidCostId == null) return BigDecimal.ZERO;
        return bidDepositMapper.selectOutstandingTotal(tenantId, bidCostId);
    }

    private String csv(String value) {
        if (value == null) return "";
        String firstNonWhitespace = value.stripLeading();
        String safe = !firstNonWhitespace.isEmpty() && "=+-@".indexOf(firstNonWhitespace.charAt(0)) >= 0
                ? "'" + value : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

}
