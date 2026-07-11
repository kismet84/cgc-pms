package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 文件关联业务对象授权校验器。
 * <p>
 * 每个 businessType 必须注册对应的校验逻辑，
 * 在上传、下载、列表、删除文件前验证当前用户对关联业务对象的访问权限。
 * 未知 businessType 将直接拒绝。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessObjectAuthorizer {

    private final ProjectAccessChecker projectAccessChecker;
    private final CtContractMapper contractMapper;
    private final PayInvoiceMapper invoiceMapper;
    private final MatReceiptMapper receiptMapper;
    private final PayApplicationMapper paymentMapper;
    private final PayRecordMapper payRecordMapper;
    private final SubMeasureMapper subcontractMapper;
    private final StlSettlementMapper settlementMapper;
    private final VarOrderMapper variationMapper;
    private final CostTargetMapper bidCostMapper;
    private final MdPartnerMapper partnerMapper;
    private final MdMaterialMapper materialMapper;
    private final CashJournalEntryMapper cashJournalEntryMapper;

    private static final Set<String> KNOWN_BUSINESS_TYPES = Set.of(
            "PROJECT", "CONTRACT", "INVOICE", "RECEIPT",
            "PAYMENT", "SUBCONTRACT", "SETTLEMENT", "VARIATION",
            "BID_COST", "PARTNER", "MATERIAL", "CASH_JOURNAL"
    );

    /**
     * 验证当前用户对指定业务对象拥有读权限。
     */
    public void checkReadAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "读取", false,
                "file:query", "cashbook:journal:query");
    }

    /**
     * 验证当前用户对指定业务对象拥有附件上传权限。
     */
    public void checkUploadAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "写入", true,
                "file:upload", "cashbook:journal:maintain");
    }

    /**
     * 验证当前用户对指定业务对象拥有附件删除权限。
     */
    public void checkDeleteAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "删除", true,
                "file:delete", "cashbook:journal:maintain");
    }

    private void checkAccess(String businessType, Long businessId, String action, boolean write,
                             String genericAuthority, String cashJournalAuthority) {
        if (businessType == null || !KNOWN_BUSINESS_TYPES.contains(businessType.toUpperCase())) {
            throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN",
                    "不支持的业务类型: " + businessType);
        }

        String upperType = businessType.toUpperCase();
        requireAuthority("CASH_JOURNAL".equals(upperType) ? cashJournalAuthority : genericAuthority);

        switch (upperType) {
            case "PROJECT":
                projectAccessChecker.checkAccess(businessId, action + "项目文件");
                break;
            case "CONTRACT": {
                CtContract contract = contractMapper.selectById(businessId);
                if (contract == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "合同不存在: " + businessId);
                }
                if (!contract.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该合同文件");
                }
                checkProjectAccess(contract.getProjectId(), action + "合同文件");
                break;
            }
            case "INVOICE": {
                PayInvoice invoice = invoiceMapper.selectById(businessId);
                if (invoice == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "发票不存在: " + businessId);
                }
                if (!invoice.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该发票文件");
                }
                checkProjectAccess(resolveInvoiceProjectId(invoice), action + "发票文件");
                break;
            }
            case "RECEIPT": {
                MatReceipt receipt = receiptMapper.selectById(businessId);
                if (receipt == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "收货单不存在: " + businessId);
                }
                if (!receipt.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该收货单文件");
                }
                checkProjectAccess(receipt.getProjectId(), action + "收货单文件");
                break;
            }
            case "PAYMENT": {
                PayApplication payment = paymentMapper.selectById(businessId);
                if (payment == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "付款申请不存在: " + businessId);
                }
                if (!payment.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该付款申请文件");
                }
                checkProjectAccess(payment.getProjectId(), action + "付款申请文件");
                break;
            }
            case "SUBCONTRACT": {
                SubMeasure subcontract = subcontractMapper.selectById(businessId);
                if (subcontract == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "分包计量不存在: " + businessId);
                }
                if (!subcontract.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该分包计量文件");
                }
                checkProjectAccess(subcontract.getProjectId(), action + "分包计量文件");
                break;
            }
            case "SETTLEMENT": {
                StlSettlement settlement = settlementMapper.selectById(businessId);
                if (settlement == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "结算单不存在: " + businessId);
                }
                if (!settlement.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该结算单文件");
                }
                checkProjectAccess(settlement.getProjectId(), action + "结算单文件");
                break;
            }
            case "VARIATION": {
                VarOrder variation = variationMapper.selectById(businessId);
                if (variation == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "变更单不存在: " + businessId);
                }
                if (!variation.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该变更单文件");
                }
                checkProjectAccess(variation.getProjectId(), action + "变更单文件");
                break;
            }
            case "BID_COST": {
                CostTarget bidCost = bidCostMapper.selectById(businessId);
                if (bidCost == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "目标成本不存在: " + businessId);
                }
                if (!bidCost.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该目标成本文件");
                }
                checkProjectAccess(bidCost.getProjectId(), action + "目标成本文件");
                break;
            }
            case "PARTNER": {
                MdPartner partner = partnerMapper.selectById(businessId);
                if (partner == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "合作方不存在: " + businessId);
                }
                if (!partner.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该合作方文件");
                }
                break;
            }
            case "MATERIAL": {
                MdMaterial material = materialMapper.selectById(businessId);
                if (material == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                            "物料不存在: " + businessId);
                }
                if (!material.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED",
                            "无权访问该物料文件");
                }
                break;
            }
            case "CASH_JOURNAL": {
                CashJournalEntry entry = write
                        ? cashJournalEntryMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                        : cashJournalEntryMapper.selectById(businessId);
                if (entry == null) {
                    throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "资金流水不存在: " + businessId);
                }
                if (!entry.getTenantId().equals(UserContext.getCurrentTenantId())) {
                    throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该资金流水文件");
                }
                if (write && !Set.of(CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE)
                        .contains(entry.getStatus())) {
                    throw new BusinessException("CASH_JOURNAL_ARCHIVED_IMMUTABLE", "归档或红冲流水的附件不可变更");
                }
                if (entry.getProjectId() != null) {
                    projectAccessChecker.checkAccess(entry.getProjectId(), action + "资金流水文件");
                }
                break;
            }
            default:
                throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN",
                        "不支持的业务类型: " + businessType);
        }
    }

    private void checkProjectAccess(Long projectId, String action) {
        if (projectId == null) {
            throw new BusinessException("FILE_ACCESS_DENIED", "业务对象缺少项目关系，拒绝访问文件");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }

    private void requireAuthority(String requiredAuthority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> requiredAuthority.equals(authority)
                        || "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!allowed) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权执行该文件操作");
        }
    }

    private Long resolveInvoiceProjectId(PayInvoice invoice) {
        if (invoice.getPayRecordId() != null) {
            PayRecord record = payRecordMapper.selectById(invoice.getPayRecordId());
            if (record != null && record.getTenantId().equals(UserContext.getCurrentTenantId())) {
                if (record.getProjectId() != null) {
                    return record.getProjectId();
                }
                if (record.getPayApplicationId() != null) {
                    PayApplication app = paymentMapper.selectById(record.getPayApplicationId());
                    if (app != null && app.getTenantId().equals(UserContext.getCurrentTenantId())) {
                        return app.getProjectId();
                    }
                }
            }
        }
        if (invoice.getPayApplicationId() != null) {
            PayApplication app = paymentMapper.selectById(invoice.getPayApplicationId());
            if (app != null && app.getTenantId().equals(UserContext.getCurrentTenantId())) {
                return app.getProjectId();
            }
        }
        return null;
    }
}
