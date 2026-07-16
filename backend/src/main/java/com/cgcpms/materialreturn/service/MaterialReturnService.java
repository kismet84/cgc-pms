package com.cgcpms.materialreturn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.materialreturn.dto.MaterialReturnRequest;
import com.cgcpms.materialreturn.entity.MaterialReturn;
import com.cgcpms.materialreturn.entity.MaterialReturnItem;
import com.cgcpms.materialreturn.mapper.MaterialReturnItemMapper;
import com.cgcpms.materialreturn.mapper.MaterialReturnMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialReturnService {

    private final MaterialReturnMapper returnMapper;
    private final MaterialReturnItemMapper returnItemMapper;
    private final MatRequisitionMapper requisitionMapper;
    private final MatRequisitionItemMapper requisitionItemMapper;
    private final MatStockTxnMapper stockTxnMapper;
    private final CostItemMapper costItemMapper;
    private final MatStockService stockService;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(rollbackFor = Exception.class)
    public Long confirm(MaterialReturnRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        MaterialReturn existing = returnMapper.selectOne(new LambdaQueryWrapper<MaterialReturn>()
                .eq(MaterialReturn::getTenantId, tenantId)
                .eq(MaterialReturn::getIdempotencyKey, request.idempotencyKey()));
        if (existing != null) return existing.getId();

        MatRequisitionItem requisitionItem = requisitionItemMapper.selectById(request.requisitionItemId());
        if (requisitionItem == null || !tenantId.equals(requisitionItem.getTenantId())) {
            throw new BusinessException("RETURN_REQUISITION_ITEM_NOT_FOUND", "原领料明细不存在");
        }
        MatRequisition requisition = requisitionMapper.selectById(requisitionItem.getRequisitionId());
        if (requisition == null || !tenantId.equals(requisition.getTenantId())
                || !Integer.valueOf(1).equals(requisition.getStockOutFlag())) {
            throw new BusinessException("RETURN_REQUISITION_NOT_ISSUED", "原领料单尚未实际出库");
        }
        projectAccessChecker.checkAccess(requisition.getProjectId(), "确认材料退料");

        MatStockTxn originalTxn = stockTxnMapper.selectById(request.originalStockTxnId());
        if (originalTxn == null || !tenantId.equals(originalTxn.getTenantId())
                || !"OUT".equals(originalTxn.getTxnType())
                || !"MAT_REQUISITION".equals(originalTxn.getSourceType())
                || !Objects.equals(originalTxn.getSourceId(), requisition.getId())
                || !Objects.equals(originalTxn.getSourceLineId(), requisitionItem.getId())
                || !Objects.equals(originalTxn.getWarehouseId(), requisition.getWarehouseId())
                || !Objects.equals(originalTxn.getMaterialId(), requisitionItem.getMaterialId())) {
            throw new BusinessException("RETURN_SOURCE_MISMATCH", "原出库流水与领料明细不匹配");
        }

        BigDecimal alreadyReturned = returnItemMapper.selectList(
                new LambdaQueryWrapper<MaterialReturnItem>()
                        .eq(MaterialReturnItem::getTenantId, tenantId)
                        .eq(MaterialReturnItem::getOriginalStockTxnId, originalTxn.getId()))
                .stream().map(MaterialReturnItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (alreadyReturned.add(request.quantity()).compareTo(originalTxn.getQuantity()) > 0) {
            throw new BusinessException("RETURN_EXCEEDS_ISSUED", "累计退料数量超过原实际出库数量");
        }

        CostItem originalCost = costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, tenantId)
                .eq(CostItem::getSourceType, "MAT_REQUISITION")
                .eq(CostItem::getSourceId, requisition.getId())
                .eq(CostItem::getSourceItemId, requisitionItem.getId()));
        if (originalCost == null) {
            throw new BusinessException("RETURN_ORIGINAL_COST_NOT_FOUND", "原出库成本不存在，禁止退料冲销");
        }

        BigDecimal unitCost = originalTxn.getUnitCost();
        BigDecimal amount = request.quantity().multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        MaterialReturn materialReturn = new MaterialReturn();
        materialReturn.setTenantId(tenantId);
        materialReturn.setProjectId(requisition.getProjectId());
        materialReturn.setContractId(requisition.getContractId());
        materialReturn.setWarehouseId(requisition.getWarehouseId());
        materialReturn.setRequisitionId(requisition.getId());
        materialReturn.setReturnCode("MRT-" + request.returnDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        materialReturn.setReturnDate(request.returnDate());
        materialReturn.setStatus("CONFIRMED");
        materialReturn.setReason(request.reason().trim());
        materialReturn.setIdempotencyKey(request.idempotencyKey());
        materialReturn.setTotalAmount(amount);
        materialReturn.setConfirmedBy(UserContext.getCurrentUserId());
        materialReturn.setConfirmedAt(LocalDateTime.now());
        returnMapper.insert(materialReturn);

        MaterialReturnItem returnItem = new MaterialReturnItem();
        returnItem.setTenantId(tenantId);
        returnItem.setReturnId(materialReturn.getId());
        returnItem.setRequisitionItemId(requisitionItem.getId());
        returnItem.setOriginalStockTxnId(originalTxn.getId());
        returnItem.setOriginalCostItemId(originalCost.getId());
        returnItem.setMaterialId(requisitionItem.getMaterialId());
        returnItem.setQuantity(request.quantity());
        returnItem.setUnitCost(unitCost);
        returnItem.setAmount(amount);
        returnItemMapper.insert(returnItem);

        stockService.stockInValued(requisition.getWarehouseId(), requisitionItem.getMaterialId(),
                request.quantity(), unitCost, "MATERIAL_RETURN", materialReturn.getId(), returnItem.getId());

        CostItem reversal = new CostItem();
        reversal.setTenantId(tenantId);
        reversal.setProjectId(requisition.getProjectId());
        reversal.setContractId(requisition.getContractId());
        reversal.setPartnerId(requisition.getPartnerId());
        reversal.setCostType("MATERIAL");
        reversal.setCostSubjectId(originalCost.getCostSubjectId());
        reversal.setAmount(amount.negate());
        reversal.setTaxAmount(BigDecimal.ZERO);
        reversal.setAmountWithoutTax(amount.negate());
        reversal.setSourceType("MATERIAL_RETURN");
        reversal.setSourceId(materialReturn.getId());
        reversal.setSourceItemId(returnItem.getId());
        reversal.setCostDate(request.returnDate());
        reversal.setCostStatus("CONFIRMED");
        reversal.setGeneratedFlag(1);
        reversal.setRemark("冲销原领料成本 " + originalCost.getId() + "：" + request.reason().trim());
        costItemMapper.insert(reversal);
        return materialReturn.getId();
    }

    public MaterialReturn getById(Long id) {
        MaterialReturn result = returnMapper.selectById(id);
        if (result == null || !UserContext.getCurrentTenantId().equals(result.getTenantId())) {
            throw new BusinessException("MATERIAL_RETURN_NOT_FOUND", "退料单不存在");
        }
        projectAccessChecker.checkAccess(result.getProjectId(), "查看材料退料");
        return result;
    }

    public List<MaterialReturnItem> getItems(Long returnId) {
        getById(returnId);
        return returnItemMapper.selectList(new LambdaQueryWrapper<MaterialReturnItem>()
                .eq(MaterialReturnItem::getTenantId, UserContext.getCurrentTenantId())
                .eq(MaterialReturnItem::getReturnId, returnId));
    }
}
