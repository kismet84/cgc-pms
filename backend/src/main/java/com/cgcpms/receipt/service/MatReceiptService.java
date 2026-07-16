package com.cgcpms.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.receipt.vo.MatReceiptItemVO;
import com.cgcpms.receipt.vo.MatReceiptVO;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatReceiptService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;
    private final MatReceiptMapper matReceiptMapper;
    private final MatReceiptItemMapper matReceiptItemMapper;
    private final MatPurchaseOrderMapper matPurchaseOrderMapper;
    private final MatPurchaseOrderItemMapper matPurchaseOrderItemMapper;
    private final MatWarehouseMapper matWarehouseMapper;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;

    private final MatReceiptAssembler assembler;

    // ──────────────────────── Query ────────────────────────

    public IPage<MatReceiptVO> getPage(long pageNum, long pageSize, Long projectId, Long orderId,
                                        Long contractId, Long partnerId, String receiptCode, String qualityStatus) {
        LambdaQueryWrapper<MatReceipt> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) wrapper.eq(MatReceipt::getProjectId, projectId);
        if (orderId != null) wrapper.eq(MatReceipt::getOrderId, orderId);
        if (contractId != null) wrapper.eq(MatReceipt::getContractId, contractId);
        if (partnerId != null) wrapper.eq(MatReceipt::getPartnerId, partnerId);
        if (StringUtils.hasText(receiptCode)) wrapper.like(MatReceipt::getReceiptCode, receiptCode);
        if (StringUtils.hasText(qualityStatus)) wrapper.eq(MatReceipt::getQualityStatus, qualityStatus);
        wrapper.eq(MatReceipt::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(MatReceipt::getCreatedAt);

        Page<MatReceipt> page = matReceiptMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<MatReceiptVO> vos = assembler.assembleBatch(page.getRecords());
        IPage<MatReceiptVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    public MatReceiptVO getById(Long id) {
        MatReceipt receipt = matReceiptMapper.selectById(id);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");
        checkProjectAccess(receipt.getProjectId(), "查看材料验收单");

        MatReceiptVO vo = assembler.assemble(receipt);

        // Load items
        LambdaQueryWrapper<MatReceiptItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(MatReceiptItem::getReceiptId, id)
                .eq(MatReceiptItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatReceiptItem::getCreatedAt);
        List<MatReceiptItem> items = matReceiptItemMapper.selectList(itemWrapper);
        vo.setItems(assembler.assembleItems(items));
        return vo;
    }

    public List<MatReceiptItemVO> getItems(Long receiptId) {
        MatReceipt receipt = matReceiptMapper.selectById(receiptId);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");
        checkProjectAccess(receipt.getProjectId(), "查看材料验收明细");

        LambdaQueryWrapper<MatReceiptItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatReceiptItem::getReceiptId, receiptId)
                .eq(MatReceiptItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatReceiptItem::getCreatedAt);
        return assembler.assembleItems(matReceiptItemMapper.selectList(wrapper));
    }

    /**
     * Load order items for receipt line selection.
     * Includes ordered_qty, already_received_qty, remaining_qty.
     */
    public List<MatReceiptItemVO> getOrderItemsForReceipt(Long orderId) {
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ORDER_NOT_FOUND", "采购订单不存在");
        checkProjectAccess(order.getProjectId(), "选择采购订单验收明细");

        LambdaQueryWrapper<MatPurchaseOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatPurchaseOrderItem::getOrderId, orderId)
                .eq(MatPurchaseOrderItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatPurchaseOrderItem::getCreatedAt);
        return assembler.assembleOrderItemsForReceipt(matPurchaseOrderItemMapper.selectList(wrapper));
    }

    // ──────────────────────── CRUD ────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public Long create(MatReceipt receipt) {
        checkProjectAccess(receipt.getProjectId(), "创建材料验收单");
        // Auto-generate receipt code: MR-yyyyMMdd-XXX
        String prefix = "MR-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        receipt.setApprovalStatus("DRAFT");
        receipt.setCostGeneratedFlag(0);
        if (!StringUtils.hasText(receipt.getReceiptMode())) receipt.setReceiptMode("INVENTORY");

        // Validate order if present
        if (receipt.getOrderId() != null) {
            MatPurchaseOrder order = matPurchaseOrderMapper.selectById(receipt.getOrderId());
            if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
                throw new BusinessException("ORDER_NOT_FOUND", "关联采购订单不存在");
            validateReceiptOrderRelation(receipt, order);
            // Auto-fill contract and partner from order
            if (receipt.getContractId() == null) receipt.setContractId(order.getContractId());
            if (receipt.getPartnerId() == null) receipt.setPartnerId(order.getPartnerId());
        }
        if (isDirectConsumption(receipt) && receipt.getWarehouseId() != null) {
            throw new BusinessException("DIRECT_RECEIPT_WAREHOUSE_FORBIDDEN", "直耗验收不得进入普通库存仓库");
        }
        if (!isDirectConsumption(receipt) && receipt.getWarehouseId() != null) {
            validateWarehouse(receipt.getWarehouseId(), receipt.getProjectId());
        }

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            receipt.setReceiptCode(nextReceiptCode(prefix, attempt));
            try {
                matReceiptMapper.insert(receipt);
                return receipt.getId();
            } catch (DuplicateKeyException e) {
                log.warn("收货单编号冲突，重试生成 receiptCode={}", receipt.getReceiptCode());
            }
        }
        throw new BusinessException("RECEIPT_CODE_CONFLICT", "收货单编号生成冲突，请重试");
    }

    private String nextReceiptCode(String prefix, int offset) {
        LambdaQueryWrapper<MatReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(MatReceipt::getReceiptCode, prefix)
                .eq(MatReceipt::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(MatReceipt::getReceiptCode);
        Page<MatReceipt> page = new Page<>(0, 1);
        Page<MatReceipt> result = matReceiptMapper.selectPage(page, wrapper);
        MatReceipt last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1 + offset;
        if (last != null && last.getReceiptCode() != null && last.getReceiptCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getReceiptCode().substring(prefix.length())) + 1 + offset;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getReceiptCode(), e);
            }
        }
        return prefix + String.format("%03d", seq);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MatReceipt receipt) {
        MatReceipt existing = matReceiptMapper.selectById(receipt.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");
        checkProjectAccess(existing.getProjectId(), "编辑材料验收单");

        if (!"DRAFT".equals(existing.getApprovalStatus()) && !"REJECTED".equals(existing.getApprovalStatus()))
            throw new BusinessException("RECEIPT_IN_APPROVAL", "验收单审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        // Prevent overwriting generated flags
        receipt.setApprovalStatus("DRAFT");
        receipt.setCostGeneratedFlag(existing.getCostGeneratedFlag());
        receipt.setReceiptCode(existing.getReceiptCode());
        if (!StringUtils.hasText(receipt.getReceiptMode())) receipt.setReceiptMode(existing.getReceiptMode());

        Long effectiveProjectId = receipt.getProjectId() != null ? receipt.getProjectId() : existing.getProjectId();
        Long effectiveOrderId = receipt.getOrderId() != null ? receipt.getOrderId() : existing.getOrderId();
        if (effectiveOrderId != null) {
            MatPurchaseOrder order = matPurchaseOrderMapper.selectById(effectiveOrderId);
            if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId())) {
                throw new BusinessException("ORDER_NOT_FOUND", "关联采购订单不存在");
            }
            MatReceipt relation = new MatReceipt();
            relation.setProjectId(effectiveProjectId);
            relation.setContractId(receipt.getContractId() != null ? receipt.getContractId() : existing.getContractId());
            relation.setPartnerId(receipt.getPartnerId() != null ? receipt.getPartnerId() : existing.getPartnerId());
            validateReceiptOrderRelation(relation, order);
        }
        Long effectiveWarehouseId = receipt.getWarehouseId() != null ? receipt.getWarehouseId() : existing.getWarehouseId();
        if (isDirectConsumption(receipt) && effectiveWarehouseId != null) {
            throw new BusinessException("DIRECT_RECEIPT_WAREHOUSE_FORBIDDEN", "直耗验收不得进入普通库存仓库");
        }
        if (!isDirectConsumption(receipt) && effectiveWarehouseId != null) {
            validateWarehouse(effectiveWarehouseId, effectiveProjectId);
        }

        matReceiptMapper.updateById(receipt);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MatReceipt receipt = matReceiptMapper.selectById(id);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");
        checkProjectAccess(receipt.getProjectId(), "删除材料验收单");

        if (!"DRAFT".equals(receipt.getApprovalStatus()))
            throw new BusinessException("RECEIPT_IN_APPROVAL", "验收单审批中或已审批，不可删除");
        if (receipt.getCostGeneratedFlag() != null && receipt.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可删除");

        // @TableLogic on BaseEntity handles soft-delete automatically
        matReceiptMapper.deleteById(id);
    }

    // ──────────────────────── Workflow ────────────────────────

    /**
     * 提交材料验收审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long receiptId) {
        MatReceipt receipt = matReceiptMapper.selectById(receiptId);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

        // 只允许草稿状态提交
        if (!"DRAFT".equals(receipt.getApprovalStatus()))
            throw new BusinessException("RECEIPT_ALREADY_SUBMITTED", "验收单已提交审批，不可重复提交");

        validateReceiptForSubmission(receipt);

        // 更新审批状态为审批中
        LambdaUpdateWrapper<MatReceipt> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getApprovalStatus, "APPROVING");
        matReceiptMapper.update(null, updateWrapper);

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                "MATERIAL_RECEIPT",
                receiptId,
                receipt.getReceiptCode(),
                receipt.getTotalAmount(),
                receipt.getProjectId(),
                receipt.getContractId(),
                null, null, null);
    }

    // ─────────────────── Item batch operations ───────────────────

    /**
     * Batch save receipt items with quantity and order-balance validation.
     * Draft lines do not mutate the purchase order. Accepted quantity is confirmed only after approval.
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveItemsBatch(Long receiptId, List<MatReceiptItem> items) {
        MatReceipt receipt = matReceiptMapper.selectById(receiptId);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");
        checkProjectAccess(receipt.getProjectId(), "编辑材料验收明细");

        if (!"DRAFT".equals(receipt.getApprovalStatus()))
            throw new BusinessException("RECEIPT_IN_APPROVAL", "验收单审批中或已审批，不可编辑");
        if (receipt.getCostGeneratedFlag() != null && receipt.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        if (receipt.getOrderId() == null) {
            throw new BusinessException("RECEIPT_ORDER_REQUIRED", "验收单必须关联采购订单");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        Map<Long, BigDecimal> draftActualByOrderItem = new HashMap<>();

        for (MatReceiptItem item : items) {
            BigDecimal actual = item.getActualQuantity();
            BigDecimal qualified = item.getQualifiedQuantity() != null
                    ? item.getQualifiedQuantity() : BigDecimal.ZERO;
            if (actual == null || actual.signum() <= 0 || qualified.signum() < 0
                    || qualified.compareTo(actual) > 0) {
                throw new BusinessException("RECEIPT_QUANTITY_INVALID", "实收数量必须大于 0，合格数量必须在 0 到实收数量之间");
            }
            if (item.getOrderItemId() == null) {
                throw new BusinessException("ORDER_ITEM_REQUIRED", "验收明细必须关联采购订单明细");
            }
            MatPurchaseOrderItem orderItem = matPurchaseOrderItemMapper.selectById(item.getOrderItemId());
            if (orderItem == null || !tenantId.equals(orderItem.getTenantId())
                    || !receipt.getOrderId().equals(orderItem.getOrderId())) {
                throw new BusinessException("ORDER_ITEM_MISMATCH", "采购订单明细不属于当前验收单");
            }
            BigDecimal received = nvl(orderItem.getReceivedQuantity());
            BigDecimal remaining = nvl(orderItem.getQuantity()).subtract(received);
            BigDecimal draftActual = draftActualByOrderItem.merge(
                    item.getOrderItemId(), actual, BigDecimal::add);
            if (draftActual.compareTo(remaining) > 0) {
                throw new BusinessException("RECEIPT_EXCEEDS_ORDER",
                        "验收数量超过采购订单剩余数量，订单明细ID=" + item.getOrderItemId());
            }
            item.setMaterialId(orderItem.getMaterialId());
            item.setUnitPrice(nvl(orderItem.getUnitPrice()));
            item.setAmount(qualified.multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
        }

        // Delete old items
        LambdaQueryWrapper<MatReceiptItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MatReceiptItem::getReceiptId, receiptId)
                .eq(MatReceiptItem::getTenantId, tenantId);
        matReceiptItemMapper.delete(deleteWrapper);

        // Insert new items
        for (MatReceiptItem item : items) {
            item.setReceiptId(receiptId);
            item.setTenantId(tenantId);
            if (item.getActualQuantity() == null) item.setActualQuantity(BigDecimal.ZERO);
            if (item.getQualifiedQuantity() == null) item.setQualifiedQuantity(BigDecimal.ZERO);
            matReceiptItemMapper.insert(item);
        }

        // Recalculate total amount
        BigDecimal totalAmount = items.stream()
                .map(MatReceiptItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LambdaUpdateWrapper<MatReceipt> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getTotalAmount, totalAmount)
                .set(MatReceipt::getQualityStatus, deriveQualityStatus(items));
        matReceiptMapper.update(null, updateWrapper);
    }

    private void validateReceiptForSubmission(MatReceipt receipt) {
        checkProjectAccess(receipt.getProjectId(), "提交材料验收审批");
        if (receipt.getOrderId() == null || receipt.getContractId() == null || receipt.getPartnerId() == null) {
            throw new BusinessException("RECEIPT_RELATION_REQUIRED", "验收单必须关联采购订单、合同和供应商");
        }
        if (receipt.getReceiptDate() == null) {
            throw new BusinessException("RECEIPT_INFO_INCOMPLETE", "验收日期不能为空");
        }
        if (!isDirectConsumption(receipt) && receipt.getWarehouseId() == null) {
            throw new BusinessException("RECEIPT_INFO_INCOMPLETE", "库存验收必须指定入库仓库");
        }
        if (isDirectConsumption(receipt) && receipt.getWarehouseId() != null) {
            throw new BusinessException("DIRECT_RECEIPT_WAREHOUSE_FORBIDDEN", "直耗验收不得进入普通库存仓库");
        }
        MatPurchaseOrder order = matPurchaseOrderMapper.selectById(receipt.getOrderId());
        if (order == null || !UserContext.getCurrentTenantId().equals(order.getTenantId())) {
            throw new BusinessException("ORDER_NOT_FOUND", "关联采购订单不存在");
        }
        validateReceiptOrderRelation(receipt, order);
        if (!"APPROVED".equals(order.getApprovalStatus()) || !"APPROVED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_NOT_APPROVED", "采购订单审批通过后才能提交验收");
        }
        if (!isDirectConsumption(receipt)) {
            validateWarehouse(receipt.getWarehouseId(), receipt.getProjectId());
        }
        List<MatReceiptItem> items = matReceiptItemMapper.selectList(new LambdaQueryWrapper<MatReceiptItem>()
                .eq(MatReceiptItem::getReceiptId, receipt.getId())
                .eq(MatReceiptItem::getTenantId, UserContext.getCurrentTenantId()));
        if (items.isEmpty()) {
            throw new BusinessException("RECEIPT_ITEMS_REQUIRED", "验收单至少需要一条验收明细");
        }
        for (MatReceiptItem item : items) {
            if (item.getOrderItemId() == null || item.getMaterialId() == null
                    || item.getActualQuantity() == null || item.getActualQuantity().signum() <= 0
                    || item.getQualifiedQuantity() == null || item.getQualifiedQuantity().signum() < 0
                    || item.getQualifiedQuantity().compareTo(item.getActualQuantity()) > 0) {
                throw new BusinessException("RECEIPT_ITEM_INCOMPLETE", "验收明细数据不完整或数量非法");
            }
            if (isDirectConsumption(receipt) && !StringUtils.hasText(item.getUseLocation())) {
                throw new BusinessException("DIRECT_RECEIPT_USE_LOCATION_REQUIRED", "直耗材料必须填写使用部位");
            }
        }
    }

    private void validateReceiptOrderRelation(MatReceipt receipt, MatPurchaseOrder order) {
        if (!Objects.equals(receipt.getProjectId(), order.getProjectId())) {
            throw new BusinessException("RECEIPT_PROJECT_MISMATCH", "验收单项目与采购订单项目不一致");
        }
        if (receipt.getContractId() != null && !Objects.equals(receipt.getContractId(), order.getContractId())) {
            throw new BusinessException("RECEIPT_CONTRACT_MISMATCH", "验收单合同与采购订单合同不一致");
        }
        if (receipt.getPartnerId() != null && !Objects.equals(receipt.getPartnerId(), order.getPartnerId())) {
            throw new BusinessException("RECEIPT_PARTNER_MISMATCH", "验收单供应商与采购订单供应商不一致");
        }
    }

    private void validateWarehouse(Long warehouseId, Long projectId) {
        MatWarehouse warehouse = matWarehouseMapper.selectById(warehouseId);
        if (warehouse == null || !UserContext.getCurrentTenantId().equals(warehouse.getTenantId())
                || !"ENABLE".equals(warehouse.getStatus())) {
            throw new BusinessException("WAREHOUSE_INVALID", "入库仓库不存在或已停用");
        }
        if (!Objects.equals(projectId, warehouse.getProjectId())) {
            throw new BusinessException("WAREHOUSE_PROJECT_MISMATCH", "入库仓库不属于验收项目");
        }
    }

    private String deriveQualityStatus(List<MatReceiptItem> items) {
        if (items.isEmpty()) return "PENDING";
        BigDecimal actual = items.stream().map(MatReceiptItem::getActualQuantity)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal qualified = items.stream().map(MatReceiptItem::getQualifiedQuantity)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (qualified.signum() == 0) return "REJECTED";
        return qualified.compareTo(actual) == 0 ? "QUALIFIED" : "PARTIAL";
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean isDirectConsumption(MatReceipt receipt) {
        return "DIRECT_CONSUMPTION".equals(receipt.getReceiptMode());
    }

    private void checkProjectAccess(Long projectId, String action) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_REQUIRED", "验收单缺少项目关系");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }
}
