package com.cgcpms.cashbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.vo.FundAccountVO;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FundAccountService {

    private final FundAccountMapper fundAccountMapper;
    private final CashJournalEntryMapper entryMapper;

    public List<FundAccountVO> list() {
        return listEntities().stream().map(this::toMaskedVO).toList();
    }

    public List<FundAccountVO> listForManagement() {
        return listEntities().stream().map(this::toFullVO).toList();
    }

    private List<FundAccount> listEntities() {
        return fundAccountMapper.selectList(new LambdaQueryWrapper<FundAccount>()
                        .eq(FundAccount::getTenantId, tenantId())
                        .orderByDesc(FundAccount::getEnabledFlag)
                        .orderByAsc(FundAccount::getAccountCode));
    }

    @Transactional(rollbackFor = Exception.class)
    public FundAccountVO createFundAccount(FundAccountCommand command) {
        validate(command);
        String code = command.getAccountCode().trim();
        if (existsByCode(code, null)) {
            throw new BusinessException("FUND_ACCOUNT_CODE_DUPLICATE", "资金账户编码已存在");
        }
        FundAccount account = new FundAccount();
        copy(command, account);
        account.setTenantId(tenantId());
        account.setAccountCode(code);
        account.setEnabledFlag(1);
        account.setVersion(0);
        fundAccountMapper.insert(account);
        return toMaskedVO(account);
    }

    @Transactional(rollbackFor = Exception.class)
    public FundAccountVO updateFundAccount(Long id, FundAccountCommand command) {
        validate(command);
        FundAccount account = requireAccountForUpdate(id);
        String code = command.getAccountCode().trim();
        if (existsByCode(code, id)) {
            throw new BusinessException("FUND_ACCOUNT_CODE_DUPLICATE", "资金账户编码已存在");
        }
        long entryCount = entryMapper.selectCount(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, tenantId())
                .eq(CashJournalEntry::getAccountId, id));
        if (entryCount > 0 && (!Objects.equals(account.getOpeningDate(), command.getOpeningDate())
                || account.getOpeningBalance().compareTo(command.getOpeningBalance()) != 0)) {
            throw new BusinessException("FUND_ACCOUNT_OPENING_LOCKED", "已有流水后不可修改期初日期或期初余额");
        }
        copy(command, account);
        account.setAccountCode(code);
        updateAccount(account);
        return toMaskedVO(account);
    }

    @Transactional(rollbackFor = Exception.class)
    public FundAccountVO setEnabled(Long id, boolean enabled) {
        FundAccount account = requireAccountForUpdate(id);
        account.setEnabledFlag(enabled ? 1 : 0);
        updateAccount(account);
        return toMaskedVO(account);
    }

    public FundAccount requireEnabled(Long id) {
        FundAccount account = requireAccount(id);
        if (!Integer.valueOf(1).equals(account.getEnabledFlag())) {
            throw new BusinessException("FUND_ACCOUNT_DISABLED", "资金账户已停用");
        }
        return account;
    }

    public FundAccount requireAccount(Long id) {
        FundAccount account = id == null ? null : fundAccountMapper.selectById(id);
        if (account == null || !Objects.equals(account.getTenantId(), tenantId())) {
            throw new BusinessException("FUND_ACCOUNT_NOT_FOUND", "资金账户不存在");
        }
        return account;
    }

    private FundAccount requireAccountForUpdate(Long id) {
        FundAccount account = id == null ? null : fundAccountMapper.selectByIdForUpdate(id, tenantId());
        if (account == null) throw new BusinessException("FUND_ACCOUNT_NOT_FOUND", "资金账户不存在");
        return account;
    }

    private boolean existsByCode(String code, Long excludedId) {
        LambdaQueryWrapper<FundAccount> wrapper = new LambdaQueryWrapper<FundAccount>()
                .eq(FundAccount::getTenantId, tenantId())
                .eq(FundAccount::getAccountCode, code);
        if (excludedId != null) {
            wrapper.ne(FundAccount::getId, excludedId);
        }
        return fundAccountMapper.selectCount(wrapper) > 0;
    }

    private void validate(FundAccountCommand command) {
        if (command == null || !StringUtils.hasText(command.getAccountCode())
                || !StringUtils.hasText(command.getAccountName())
                || command.getOpeningDate() == null || command.getOpeningBalance() == null
                || command.getOpeningBalance().compareTo(BigDecimal.ZERO) < 0
                || command.getOpeningBalance().scale() > 2
                || Math.max(0, command.getOpeningBalance().precision() - command.getOpeningBalance().scale()) > 16) {
            throw new BusinessException("FUND_ACCOUNT_INVALID", "资金账户信息不完整或不合法");
        }
        if (!List.of(CashbookConstants.AccountType.CASH, CashbookConstants.AccountType.BANK)
                .contains(command.getAccountType())) {
            throw new BusinessException("FUND_ACCOUNT_TYPE_INVALID", "资金账户类型不合法");
        }
        if (CashbookConstants.AccountType.BANK.equals(command.getAccountType())
                && (!StringUtils.hasText(command.getBankName()) || !StringUtils.hasText(command.getBankAccountNo()))) {
            throw new BusinessException("FUND_ACCOUNT_BANK_INFO_REQUIRED", "银行账户必须填写开户行和账号");
        }
    }

    private void copy(FundAccountCommand command, FundAccount account) {
        account.setAccountName(command.getAccountName().trim());
        account.setAccountType(command.getAccountType());
        account.setBankName(CashbookConstants.AccountType.BANK.equals(command.getAccountType())
                ? command.getBankName().trim() : null);
        account.setBankAccountNo(CashbookConstants.AccountType.BANK.equals(command.getAccountType())
                ? command.getBankAccountNo().trim() : null);
        account.setOpeningDate(command.getOpeningDate());
        account.setOpeningBalance(command.getOpeningBalance().setScale(2));
        account.setRemark(command.getRemark());
    }

    private FundAccountVO toMaskedVO(FundAccount account) {
        return toVO(account, true);
    }

    private FundAccountVO toFullVO(FundAccount account) {
        return toVO(account, false);
    }

    private FundAccountVO toVO(FundAccount account, boolean masked) {
        FundAccountVO vo = new FundAccountVO();
        vo.setId(String.valueOf(account.getId()));
        vo.setAccountCode(account.getAccountCode());
        vo.setAccountName(account.getAccountName());
        vo.setAccountType(account.getAccountType());
        vo.setBankName(account.getBankName());
        vo.setBankAccountNo(masked ? mask(account.getBankAccountNo()) : account.getBankAccountNo());
        vo.setOpeningDate(account.getOpeningDate());
        vo.setOpeningBalance(money(account.getOpeningBalance()));
        vo.setEnabledFlag(account.getEnabledFlag());
        vo.setVersion(account.getVersion());
        vo.setRemark(masked ? null : account.getRemark());
        return vo;
    }

    private void updateAccount(FundAccount account) {
        if (fundAccountMapper.updateById(account) != 1) {
            throw new BusinessException("FUND_ACCOUNT_CONCURRENT_MODIFICATION", "资金账户已被并发修改，请刷新后重试");
        }
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return "****" + trimmed.substring(Math.max(0, trimmed.length() - 4));
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2).toPlainString();
    }

    private Long tenantId() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("TENANT_CONTEXT_REQUIRED", "缺少租户上下文");
        return tenantId;
    }
}
