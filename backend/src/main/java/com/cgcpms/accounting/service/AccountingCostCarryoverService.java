package com.cgcpms.accounting.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.system.role.SystemRoleContract;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountingCostCarryoverService {

    public static final String SOURCE_TYPE = "ACCOUNTING_COST_CARRYOVER";
    public static final String ENTRY_TYPE = "COST_CARRYOVER";

    private final JdbcTemplate jdbcTemplate;
    private final AccountingEntryMapper entryMapper;
    private final AccountingEntryLineMapper lineMapper;
    private final AccountingPeriodGuard periodGuard;
    private final AccountingDimensionValidator dimensionValidator;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(rollbackFor = Exception.class)
    public AccountingEntry create(Long projectId, Long contractId, LocalDate carryoverDate) {
        requireCompanyFinance();
        if (projectId == null || contractId == null || carryoverDate == null) {
            throw new BusinessException("ACCOUNTING_CARRYOVER_REQUIRED", "项目、主合同和结转日期不能为空");
        }
        projectAccessChecker.checkAccess(projectId, "结转项目成本");
        periodGuard.assertWritable(carryoverDate);
        Integer validContract = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pm_project project_row
                JOIN ct_contract contract_row ON contract_row.tenant_id=project_row.tenant_id
                 AND contract_row.id=project_row.owner_contract_id
                WHERE project_row.tenant_id=? AND project_row.id=? AND project_row.deleted_flag=0
                  AND project_row.status<>'CLOSED' AND contract_row.id=?
                  AND contract_row.contract_type='MAIN' AND contract_row.approval_status='APPROVED'
                  AND contract_row.contract_status IN ('PERFORMING','SETTLED') AND contract_row.deleted_flag=0
                """, Integer.class, UserContext.getCurrentTenantId(), projectId, contractId);
        if (validContract == null || validContract == 0) {
            throw new BusinessException("ACCOUNTING_CARRYOVER_CONTRACT_INVALID", "仅可对未关闭项目的已批准权威主合同结转成本");
        }

        List<Balance> balances = currentBalances(UserContext.getCurrentTenantId(), projectId, contractId,
                carryoverDate);
        if (balances.isEmpty()) {
            throw new BusinessException("ACCOUNTING_COST_CARRYOVER_EMPTY", "当前项目主合同没有可结转的合同履约成本余额");
        }
        BigDecimal total = balances.stream().map(Balance::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String hash = hash(balances);
        Long batchId = IdWorker.getId();
        try {
            jdbcTemplate.update("""
                    INSERT INTO accounting_cost_carryover
                      (id,tenant_id,project_id,contract_id,carryover_date,balance_hash,total_amount,status,created_by)
                    VALUES (?,?,?,?,?,?,?,'DRAFT',?)
                    """, batchId, UserContext.getCurrentTenantId(), projectId, contractId, carryoverDate,
                    hash, total, UserContext.getCurrentUserId());
        } catch (DuplicateKeyException duplicate) {
            Long entryId = jdbcTemplate.queryForObject("""
                    SELECT entry_id FROM accounting_cost_carryover
                    WHERE tenant_id=? AND project_id=? AND contract_id=? AND carryover_date=? AND balance_hash=?
                    """, Long.class, UserContext.getCurrentTenantId(), projectId, contractId, carryoverDate, hash);
            if (entryId != null) return entryMapper.selectById(entryId);
            throw new BusinessException("ACCOUNTING_COST_CARRYOVER_CONCURRENT", "同一成本余额正在结转，请刷新后重试");
        }

        AccountingEntry entry = new AccountingEntry();
        entry.setTenantId(UserContext.getCurrentTenantId());
        entry.setEntryCode("COST-CO-" + batchId);
        entry.setEntryDate(carryoverDate);
        entry.setEntryType(ENTRY_TYPE);
        entry.setSourceType(SOURCE_TYPE);
        entry.setSourceId(batchId);
        entry.setProjectId(projectId);
        entry.setContractId(contractId);
        entry.setEntryStatus("DRAFT");
        entry.setReviewStatus("PENDING");
        entry.setTotalDebit(total);
        entry.setTotalCredit(total);
        entry.setPeriodId(periodGuard.findPeriodId(carryoverDate));
        entry.setAdjustmentFlag(0);
        entry.setVersion(0);
        entry.setLines(balances.stream().flatMap(balance -> java.util.stream.Stream.of(
                line("DEBIT", balance.targetId(), balance.targetCode(), balance.targetName(), balance.amount(),
                        "结转" + balance.targetName()),
                line("CREDIT", balance.sourceId(), balance.sourceCode(), balance.sourceName(), balance.amount(),
                        "结转" + balance.sourceName()))).toList());
        dimensionValidator.validate(entry);
        entryMapper.insert(entry);
        int lineNo = 1;
        for (AccountingEntryLine line : entry.getLines()) {
            line.setTenantId(entry.getTenantId());
            line.setEntryId(entry.getId());
            line.setLineNo(lineNo++);
            lineMapper.insert(line);
        }
        jdbcTemplate.update("UPDATE accounting_cost_carryover SET entry_id=? WHERE tenant_id=? AND id=?",
                entry.getId(), entry.getTenantId(), batchId);
        return entry;
    }

    void assertCurrentSnapshotForPost(AccountingEntry entry) {
        if (!SOURCE_TYPE.equals(entry.getSourceType())) return;
        List<BatchSnapshot> batches = jdbcTemplate.query("""
                SELECT project_id,contract_id,carryover_date,balance_hash,total_amount,status,entry_id
                FROM accounting_cost_carryover
                WHERE tenant_id=? AND id=?
                FOR UPDATE
                """, (rs, rowNum) -> new BatchSnapshot(rs.getLong("project_id"), rs.getLong("contract_id"),
                rs.getObject("carryover_date", LocalDate.class), rs.getString("balance_hash"),
                rs.getBigDecimal("total_amount"), rs.getString("status"), rs.getLong("entry_id")),
                entry.getTenantId(), entry.getSourceId());
        if (batches.size() != 1) {
            throw new BusinessException("ACCOUNTING_COST_CARRYOVER_BATCH_INVALID", "成本结转批次不存在");
        }
        BatchSnapshot batch = batches.getFirst();
        if (!"DRAFT".equals(batch.status()) || !entry.getId().equals(batch.entryId())
                || !entry.getProjectId().equals(batch.projectId())
                || !entry.getContractId().equals(batch.contractId())
                || !entry.getEntryDate().equals(batch.carryoverDate())) {
            throw new BusinessException("ACCOUNTING_COST_CARRYOVER_BATCH_INVALID", "成本结转凭证与批次不一致");
        }
        List<Balance> balances = currentBalances(entry.getTenantId(), batch.projectId(), batch.contractId(),
                batch.carryoverDate());
        BigDecimal total = balances.stream().map(Balance::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!batch.balanceHash().equals(hash(balances)) || batch.totalAmount().compareTo(total) != 0
                || entry.getTotalDebit().compareTo(total) != 0 || entry.getTotalCredit().compareTo(total) != 0) {
            throw new BusinessException("ACCOUNTING_COST_CARRYOVER_SNAPSHOT_DRIFT",
                    "合同履约成本余额已变化，请废弃旧草稿并重新生成结转凭证");
        }
    }

    void assertSourceReversalAllowed(AccountingEntry original) {
        if (SOURCE_TYPE.equals(original.getSourceType()) || original.getProjectId() == null
                || original.getContractId() == null) return;
        Integer blocked = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM accounting_cost_carryover batch
                WHERE batch.tenant_id=? AND batch.project_id=? AND batch.contract_id=?
                  AND batch.status='POSTED' AND batch.carryover_date>=?
                  AND EXISTS (
                    SELECT 1 FROM accounting_entry_line source_line
                    JOIN accounting_cost_carryover_mapping mapping
                      ON mapping.tenant_id=source_line.tenant_id
                     AND mapping.fulfillment_subject_id=source_line.accounting_subject_id
                     AND mapping.status='ENABLE'
                    WHERE source_line.tenant_id=batch.tenant_id AND source_line.entry_id=?
                      AND source_line.deleted_flag=0
                  )
                """, Integer.class, original.getTenantId(), original.getProjectId(), original.getContractId(),
                original.getEntryDate(), original.getId());
        if (blocked != null && blocked > 0) {
            throw new BusinessException("ACCOUNTING_COST_CARRYOVER_REVERSE_BLOCKED",
                    "该凭证成本已结转，请先冲销对应成本结转凭证");
        }
    }

    private List<Balance> currentBalances(Long tenantId, Long projectId, Long contractId, LocalDate carryoverDate) {
        return jdbcTemplate.query("""
                SELECT mapping.category_code,source.id source_id,source.subject_code source_code,
                       source.subject_name source_name,target.id target_id,target.subject_code target_code,
                       target.subject_name target_name,
                       SUM(CASE WHEN line.direction='DEBIT' THEN line.amount ELSE -line.amount END) balance
                FROM accounting_cost_carryover_mapping mapping
                JOIN cost_subject source ON source.tenant_id=mapping.tenant_id AND source.id=mapping.fulfillment_subject_id
                JOIN cost_subject target ON target.tenant_id=mapping.tenant_id AND target.id=mapping.expense_subject_id
                JOIN accounting_entry_line line ON line.tenant_id=mapping.tenant_id
                 AND line.accounting_subject_id=source.id AND line.deleted_flag=0
                JOIN accounting_entry entry_row ON entry_row.tenant_id=line.tenant_id AND entry_row.id=line.entry_id
                WHERE mapping.tenant_id=? AND mapping.status='ENABLE'
                  AND entry_row.project_id=? AND entry_row.contract_id=? AND entry_row.entry_date<=?
                  AND entry_row.entry_status='POSTED' AND entry_row.original_entry_id IS NULL
                  AND entry_row.deleted_flag=0
                GROUP BY mapping.category_code,source.id,source.subject_code,source.subject_name,
                         target.id,target.subject_code,target.subject_name
                HAVING SUM(CASE WHEN line.direction='DEBIT' THEN line.amount ELSE -line.amount END)>0
                ORDER BY mapping.category_code
                """, (rs, rowNum) -> new Balance(rs.getString("category_code"), rs.getLong("source_id"),
                        rs.getString("source_code"), rs.getString("source_name"), rs.getLong("target_id"),
                        rs.getString("target_code"), rs.getString("target_name"), rs.getBigDecimal("balance")),
                tenantId, projectId, contractId, carryoverDate);
    }

    private void requireCompanyFinance() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user user_row
                JOIN sys_user_role ur ON ur.tenant_id=user_row.tenant_id AND ur.user_id=user_row.id
                JOIN sys_role role_row ON role_row.tenant_id=ur.tenant_id AND role_row.id=ur.role_id
                WHERE user_row.tenant_id=? AND user_row.id=? AND user_row.status='ENABLE' AND user_row.deleted_flag=0
                  AND role_row.role_code=? AND role_row.status='ENABLE' AND role_row.deleted_flag=0
                """, Integer.class, UserContext.getCurrentTenantId(), UserContext.getCurrentUserId(),
                SystemRoleContract.COMPANY_FINANCE);
        if (count == null || count == 0) {
            throw new BusinessException("ACCOUNTING_COMPANY_FINANCE_REQUIRED", "仅公司财务可执行项目成本结转");
        }
    }

    private static AccountingEntryLine line(String direction, Long subjectId, String code, String name,
                                            BigDecimal amount, String summary) {
        AccountingEntryLine line = new AccountingEntryLine();
        line.setDirection(direction);
        line.setAccountingSubjectId(subjectId);
        line.setAccountCode(code);
        line.setAccountName(name);
        line.setAmount(amount);
        line.setSummary(summary);
        return line;
    }

    private static String hash(List<Balance> balances) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = balances.stream()
                    .map(item -> item.category() + "|" + item.sourceId() + "|" + item.targetId() + "|" + item.amount().toPlainString())
                    .collect(java.util.stream.Collectors.joining("\n"));
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算成本结转快照", e);
        }
    }

    private record Balance(String category, Long sourceId, String sourceCode, String sourceName,
                           Long targetId, String targetCode, String targetName, BigDecimal amount) {
    }

    private record BatchSnapshot(Long projectId, Long contractId, LocalDate carryoverDate,
                                 String balanceHash, BigDecimal totalAmount, String status, Long entryId) {
    }
}
