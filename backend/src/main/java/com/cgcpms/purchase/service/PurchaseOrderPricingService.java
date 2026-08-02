package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.vo.PurchaseOrderPricingSuggestionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PurchaseOrderPricingService {
    private final CtContractMapper contractMapper;
    private final CtContractItemMapper contractItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectAccessChecker projectAccessChecker;

    public PurchaseOrderPricingSuggestionVO suggest(Long contractId, Long materialId) {
        Long tenantId = UserContext.getCurrentTenantId();
        CtContract contract = contractMapper.selectById(contractId);
        requirePurchaseContract(contract, tenantId);
        projectAccessChecker.checkAccess(contract.getProjectId(), "查询采购订单价格建议");
        CtContractItem contractItem = requireUniqueContractItem(contract, materialId);
        String pricingMode = contract.getPricingMode();
        if ("FIXED".equals(pricingMode)) {
            if (contractItem.getUnitPrice() == null || contractItem.getUnitPrice().signum() <= 0) {
                throw new BusinessException("PURCHASE_CONTRACT_PRICE_INVALID", "固定价合同材料单价缺失");
            }
            return new PurchaseOrderPricingSuggestionVO(pricingMode, contractItem.getId().toString(),
                    contractItem.getUnitPrice().toPlainString(), false, "CONTRACT_ITEM", null, null);
        }
        Map<String, Object> recent = findRecentReceipt(tenantId, contract.getPartyBId(), materialId);
        return new PurchaseOrderPricingSuggestionVO(pricingMode, contractItem.getId().toString(),
                recent == null ? null : ((BigDecimal) recent.get("unit_price")).toPlainString(), true,
                recent == null ? "MANUAL" : "RECENT_RECEIPT",
                recent == null ? null : String.valueOf(recent.get("id")),
                recent == null ? null : dateText(recent.get("receipt_date")));
    }

    public CtContract requirePurchaseContract(CtContract contract, Long tenantId) {
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "关联合同不存在");
        }
        if (!"PURCHASE".equals(contract.getContractType())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())
                || !List.of("FIXED", "ACTUAL").contains(contract.getPricingMode())) {
            throw new BusinessException("PURCHASE_CONTRACT_INVALID", "合同必须为执行中的采购合同且计价模式有效");
        }
        return contract;
    }

    public CtContractItem requireUniqueContractItem(CtContract contract, Long materialId) {
        List<CtContractItem> items = contractItemMapper.selectList(new LambdaQueryWrapper<CtContractItem>()
                .eq(CtContractItem::getTenantId, contract.getTenantId())
                .eq(CtContractItem::getContractId, contract.getId())
                .eq(CtContractItem::getMaterialId, materialId));
        if (items.size() != 1) {
            throw new BusinessException("PURCHASE_CONTRACT_MATERIAL_NOT_UNIQUE", "采购合同材料清单缺失或不唯一");
        }
        return items.getFirst();
    }

    public Map<String, Object> findRecentReceipt(Long tenantId, Long partnerId, Long materialId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT i.id, i.unit_price, r.receipt_date
                FROM mat_receipt_item i
                JOIN mat_receipt r ON r.tenant_id=i.tenant_id AND r.id=i.receipt_id
                WHERE i.tenant_id=? AND r.partner_id=? AND i.material_id=?
                  AND r.approval_status='APPROVED' AND i.qualified_quantity>0 AND i.unit_price>0
                  AND i.deleted_flag=0 AND r.deleted_flag=0
                ORDER BY r.receipt_date DESC, i.id DESC LIMIT 1
                """, tenantId, partnerId, materialId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void requirePurchaseRequestDocument(Long requestId, Long tenantId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_document_generation
                WHERE tenant_id=? AND business_type='PURCHASE_REQUEST' AND business_id=?
                  AND status='SUCCEEDED' AND file_id IS NOT NULL AND deleted_flag=0
                """, Integer.class, tenantId, requestId);
        if (count == null || count < 1) {
            throw new BusinessException("PURCHASE_REQUEST_DOCUMENT_REQUIRED", "采购申请审批单尚未成功生成");
        }
    }

    private String dateText(Object value) {
        if (value instanceof LocalDate date) return date.toString();
        if (value instanceof Date date) return date.toLocalDate().toString();
        return String.valueOf(value);
    }
}
