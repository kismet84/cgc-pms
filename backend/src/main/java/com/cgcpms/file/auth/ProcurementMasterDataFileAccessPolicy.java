package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProcurementMasterDataFileAccessPolicy implements FileAccessPolicy {

    private final ProjectAccessChecker projectAccessChecker;
    private final MatReceiptMapper receiptMapper;
    private final BidCostMapper bidCostMapper;
    private final MdPartnerMapper partnerMapper;
    private final MdMaterialMapper materialMapper;
    private final JdbcTemplate jdbcTemplate;

    ProcurementMasterDataFileAccessPolicy(ProjectAccessChecker projectAccessChecker,
                                          MatReceiptMapper receiptMapper,
                                          BidCostMapper bidCostMapper,
                                          MdPartnerMapper partnerMapper,
                                          MdMaterialMapper materialMapper,
                                          JdbcTemplate jdbcTemplate) {
        this.projectAccessChecker = projectAccessChecker;
        this.receiptMapper = receiptMapper;
        this.bidCostMapper = bidCostMapper;
        this.partnerMapper = partnerMapper;
        this.materialMapper = materialMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public FileAccessPolicyRegistry.Group group() {
        return FileAccessPolicyRegistry.Group.PROCUREMENT_MASTER_DATA;
    }

    @Override
    public void checkObject(FileAccessPolicyRegistry.BusinessType businessType,
                            Long businessId,
                            String action,
                            boolean write,
                            String documentType) {
        switch (businessType) {
            case PURCHASE_REQUEST, PURCHASE_ORDER ->
                    checkProcurement(businessType, businessId, action, write);
            case RECEIPT, MATERIAL_RECEIPT -> checkReceipt(businessId, action, write);
            case BID_COST -> checkBid(businessId, action);
            case PARTNER -> checkPartner(businessId, write);
            case MATERIAL -> checkMaterial(businessId);
            default -> throw new IllegalArgumentException("Unsupported procurement/master-data file type");
        }
    }

    private void checkProcurement(FileAccessPolicyRegistry.BusinessType businessType,
                                  Long businessId,
                                  String action,
                                  boolean write) {
        String table = businessType == FileAccessPolicyRegistry.BusinessType.PURCHASE_REQUEST
                ? "mat_purchase_request" : "mat_purchase_order";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT project_id,approval_status FROM " + table
                        + " WHERE id=? AND tenant_id=? AND deleted_flag=0"
                        + (write ? " FOR UPDATE" : ""),
                businessId, UserContext.getCurrentTenantId());
        if (rows.isEmpty()) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "采购业务对象不存在: " + businessId);
        }
        Map<String, Object> row = rows.getFirst();
        if (write && !"DRAFT".equals(String.valueOf(row.get("approval_status")))) {
            throw new BusinessException("PROCUREMENT_DOCUMENT_IMMUTABLE", "采购单据提交后附件不可变更");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                ((Number) row.get("project_id")).longValue(), action + "采购单据文件");
    }

    private void checkReceipt(Long businessId, String action, boolean write) {
        MatReceipt receipt = write
                ? receiptMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : receiptMapper.selectById(businessId);
        if (receipt == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "收货单不存在: " + businessId);
        }
        if (!receipt.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该收货单文件");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker, receipt.getProjectId(), action + "收货单文件");
        if (write && !Set.of("DRAFT", "REJECTED").contains(receipt.getApprovalStatus())) {
            throw new BusinessException("PROCUREMENT_DOCUMENT_IMMUTABLE", "材料验收提交后附件不可变更");
        }
    }

    private void checkBid(Long businessId, String action) {
        BidCost bidCost = bidCostMapper.selectById(businessId);
        if (bidCost == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "投标记录不存在: " + businessId);
        }
        if (!bidCost.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该投标文件");
        }
        if (bidCost.getProjectId() != null) {
            FilePolicySupport.checkProjectAccess(projectAccessChecker,
                    bidCost.getProjectId(), action + "投标文件");
        }
    }

    private void checkPartner(Long businessId, boolean write) {
        MdPartner partner = write
                ? partnerMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : partnerMapper.selectById(businessId);
        if (partner == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "合作方不存在: " + businessId);
        }
        if (!partner.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该合作方文件");
        }
    }

    private void checkMaterial(Long businessId) {
        MdMaterial material = materialMapper.selectById(businessId);
        if (material == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "物料不存在: " + businessId);
        }
        if (!material.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该物料文件");
        }
    }
}
