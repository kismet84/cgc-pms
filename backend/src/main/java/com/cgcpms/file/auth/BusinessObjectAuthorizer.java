package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
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
import com.cgcpms.payment.mapper.PayApplicationMapper;
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
    private final SubMeasureMapper subcontractMapper;
    private final StlSettlementMapper settlementMapper;
    private final VarOrderMapper variationMapper;
    private final CostTargetMapper bidCostMapper;
    private final MdPartnerMapper partnerMapper;
    private final MdMaterialMapper materialMapper;

    private static final Set<String> KNOWN_BUSINESS_TYPES = Set.of(
            "PROJECT", "CONTRACT", "INVOICE", "RECEIPT",
            "PAYMENT", "SUBCONTRACT", "SETTLEMENT", "VARIATION",
            "BID_COST", "PARTNER", "MATERIAL"
    );

    /**
     * 验证当前用户对指定业务对象拥有读权限。
     */
    public void checkReadAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "读取");
    }

    /**
     * 验证当前用户对指定业务对象拥有写权限。
     */
    public void checkWriteAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "写入");
    }

    private void checkAccess(String businessType, Long businessId, String action) {
        if (businessType == null || !KNOWN_BUSINESS_TYPES.contains(businessType.toUpperCase())) {
            throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN",
                    "不支持的业务类型: " + businessType);
        }

        String upperType = businessType.toUpperCase();

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
            default:
                throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN",
                        "不支持的业务类型: " + businessType);
        }
    }
}
