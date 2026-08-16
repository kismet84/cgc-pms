package com.cgcpms.accounting.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.constant.AccountingSubjectCatalog;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AccountingSubjectResolver {

    private final CostSubjectMapper subjectMapper;
    private final JdbcTemplate jdbcTemplate;

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

    public CostSubject requireFundAccount(Long fundAccountId) {
        if (fundAccountId == null) {
            throw new BusinessException("FUND_ACCOUNT_REQUIRED", "资金账户不能为空");
        }
        List<String> subjectCodes = jdbcTemplate.query("""
                SELECT accounting_subject_code FROM fund_account
                WHERE tenant_id=? AND id=? AND enabled_flag=1 AND deleted_flag=0
                """, (rs, rowNum) -> rs.getString(1),
                UserContext.getCurrentTenantId(), fundAccountId);
        if (subjectCodes.isEmpty()) {
            throw new BusinessException("FUND_ACCOUNT_UNAVAILABLE", "资金账户不存在或已停用");
        }
        String subjectCode = subjectCodes.getFirst();
        if (subjectCode == null) {
            throw new BusinessException("FUND_ACCOUNT_ACCOUNTING_SUBJECT_REQUIRED", "资金账户未配置正式总账科目");
        }
        return require(subjectCode, "ASSET");
    }

    public CostSubject requireBusinessCostSubject(Long subjectId) {
        CostSubject subject = subjectMapper.selectById(subjectId);
        if (subject == null || !Objects.equals(subject.getTenantId(), UserContext.getCurrentTenantId())
                || !"COST".equals(subject.getAccountCategory()) || !"ENABLE".equals(subject.getStatus())
                || Integer.valueOf(1).equals(subject.getLedgerFlag())) {
            throw new BusinessException("PAYMENT_COST_SUBJECT_INVALID", "费用分类科目不存在、跨租户、非项目成本类或已停用");
        }
        return subject;
    }

    public CostSubject requireFulfillmentSubject(CostSubject businessSubject) {
        return require(AccountingSubjectCatalog.FULFILLMENT_BY_CATEGORY.get(categoryKey(businessSubject)), "ASSET");
    }

    public CostSubject requirePayableSubject(CostSubject businessSubject) {
        String code = switch (categoryKey(businessSubject)) {
            case "MATERIAL" -> AccountingSubjectCatalog.PAYABLE_MATERIAL;
            case "EQUIPMENT" -> AccountingSubjectCatalog.PAYABLE_EQUIPMENT;
            case "LABOR" -> AccountingSubjectCatalog.PAYABLE_LABOR;
            case "MACHINERY" -> AccountingSubjectCatalog.PAYABLE_MACHINERY;
            default -> AccountingSubjectCatalog.PAYABLE_SUBCONTRACT;
        };
        return require(code, "LIABILITY");
    }

    public String categoryKey(CostSubject businessSubject) {
        if (businessSubject == null) return "OTHER";
        String type = businessSubject.getSubjectType() == null ? "" : businessSubject.getSubjectType().toUpperCase(Locale.ROOT);
        String name = businessSubject.getSubjectName() == null ? "" : businessSubject.getSubjectName();
        if ("MATERIAL".equals(type)) return "MATERIAL";
        if ("PURCHASE".equals(type)) return name.contains("设备") ? "EQUIPMENT" : "MATERIAL";
        if ("LABOR".equals(type)) return "LABOR";
        if ("MACHINERY".equals(type)) return "MACHINERY";
        if ("SUBCONTRACT".equals(type)) return "SUBCONTRACT";
        if ("MEASURES".equals(type)) return "MEASURES";
        if ("SITE_MANAGEMENT".equals(type) || "OVERHEAD".equals(type)) return "SITE_MANAGEMENT";
        return "OTHER";
    }

    private CostSubject find(Long tenantId, String subjectCode, String accountCategory) {
        return subjectMapper.selectOne(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getTenantId, tenantId)
                .eq(CostSubject::getSubjectCode, subjectCode)
                .eq(CostSubject::getAccountCategory, accountCategory)
                .eq(CostSubject::getLedgerFlag, 1)
                .eq(CostSubject::getStatus, "ENABLE")
                .eq(CostSubject::getDeletedFlag, 0));
    }

    private static BusinessException unavailable(String subjectCode) {
        return new BusinessException("ACCOUNTING_SUBJECT_UNAVAILABLE",
                "会计科目" + subjectCode + "未配置、分类不符或已停用");
    }
}
