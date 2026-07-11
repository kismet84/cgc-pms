package com.cgcpms.cashbook.service;

import com.cgcpms.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CashJournalAlertRecipientResolver {

    private final SysUserMapper userMapper;

    public Set<Long> resolve(Long tenantId) {
        if (tenantId == null) return Set.of();
        return new LinkedHashSet<>(userMapper.selectCashJournalAlertRecipientIds(tenantId));
    }

    public boolean isEligible(Long tenantId, Long userId) {
        return userId != null && resolve(tenantId).contains(userId);
    }
}
