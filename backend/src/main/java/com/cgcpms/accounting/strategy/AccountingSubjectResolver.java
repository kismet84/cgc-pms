package com.cgcpms.accounting.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountingSubjectResolver {

    private final CostSubjectMapper subjectMapper;

    public CostSubject require(String subjectCode, String accountCategory) {
        CostSubject subject = find(UserContext.getCurrentTenantId(), subjectCode, accountCategory);
        if (subject == null) {
            throw unavailable(subjectCode);
        }
        return subject;
    }

    public CostSubject requireWithPublicFallback(String subjectCode, String accountCategory) {
        Long tenantId = UserContext.getCurrentTenantId();
        CostSubject subject = find(tenantId, subjectCode, accountCategory);
        if (subject == null && !Long.valueOf(0L).equals(tenantId)) {
            subject = find(0L, subjectCode, accountCategory);
        }
        if (subject == null) {
            throw unavailable(subjectCode);
        }
        return subject;
    }

    private CostSubject find(Long tenantId, String subjectCode, String accountCategory) {
        return subjectMapper.selectOne(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getTenantId, tenantId)
                .eq(CostSubject::getSubjectCode, subjectCode)
                .eq(CostSubject::getAccountCategory, accountCategory)
                .eq(CostSubject::getStatus, "ENABLE")
                .eq(CostSubject::getDeletedFlag, 0));
    }

    private static BusinessException unavailable(String subjectCode) {
        return new BusinessException("ACCOUNTING_SUBJECT_UNAVAILABLE",
                "会计科目" + subjectCode + "未配置、分类不符或已停用");
    }
}
