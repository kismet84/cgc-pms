package com.cgcpms.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
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
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatReceiptService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;
    private static final int ORDER_ITEM_UPDATE_MAX_RETRIES = 3;

    private final MatReceiptMapper matReceiptMapper;
    private final MatReceiptItemMapper matReceiptItemMapper;
    private final MatPurchaseOrderMapper matPurchaseOrderMapper;
    private final MatPurchaseOrderItemMapper matPurchaseOrderItemMapper;
    private final WorkflowEngine workflowEngine;

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

        LambdaQueryWrapper<MatPurchaseOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatPurchaseOrderItem::getOrderId, orderId)
                .eq(MatPurchaseOrderItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatPurchaseOrderItem::getCreatedAt);
        return assembler.assembleOrderItemsForReceipt(matPurchaseOrderItemMapper.selectList(wrapper));
    }

    // ──────────────────────── CRUD ────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public Long create(MatReceipt receipt) {
        // Auto-generate receipt code: MR-yyyyMMdd-XXX
        String prefix = "MR-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        receipt.setApprovalStatus("DRAFT");
        receipt.setCostGeneratedFlag(0);

        // Validate order if present
        if (receipt.getOrderId() != null) {
            MatPurchaseOrder order = matPurchaseOrderMapper.selectById(receipt.getOrderId());
            if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
                throw new BusinessException("ORDER_NOT_FOUND", "关联采购订单不存在");
            // Auto-fill contract and partner from order
            if (receipt.getContractId() == null) receipt.setContractId(order.getContractId());
            if (receipt.getPartnerId() == null) receipt.setPartnerId(order.getPartnerId());
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

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("RECEIPT_IN_APPROVAL", "验收单审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        // Prevent overwriting generated flags
        receipt.setApprovalStatus(existing.getApprovalStatus());
        receipt.setCostGeneratedFlag(existing.getCostGeneratedFlag());
        receipt.setReceiptCode(existing.getReceiptCode());

        matReceiptMapper.updateById(receipt);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MatReceipt receipt = matReceiptMapper.selectById(id);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

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
     * Batch save receipt items with quantity validation.
     * W0 Decision 3: WARN but don't block when receipt qty > order remaining qty.
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveItemsBatch(Long receiptId, List<MatReceiptItem> items) {
        MatReceipt receipt = matReceiptMapper.selectById(receiptId);
        if (receipt == null || !receipt.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RECEIPT_NOT_FOUND", "验收单不存在");

        if (!"DRAFT".equals(receipt.getApprovalStatus()))
            throw new BusinessException("RECEIPT_IN_APPROVAL", "验收单审批中或已审批，不可编辑");
        if (receipt.getCostGeneratedFlag() != null && receipt.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        Long tenantId = UserContext.getCurrentTenantId();

        // Subtract old item quantities from order items before deletion
        LambdaQueryWrapper<MatReceiptItem> oldItemWrapper = new LambdaQueryWrapper<>();
        oldItemWrapper.eq(MatReceiptItem::getReceiptId, receiptId)
                .eq(MatReceiptItem::getTenantId, tenantId);
        List<MatReceiptItem> oldItems = matReceiptItemMapper.selectList(oldItemWrapper);
        for (MatReceiptItem oldItem : oldItems) {
            if (oldItem.getOrderItemId() != null) {
                BigDecimal oldQty = oldItem.getActualQuantity() != null ? oldItem.getActualQuantity() : BigDecimal.ZERO;
                adjustOrderItemReceivedQuantity(oldItem.getOrderItemId(), oldQty.negate(), true);
            }
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
            if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
            if (item.getAmount() == null) item.setAmount(BigDecimal.ZERO);
            matReceiptItemMapper.insert(item);

            // Update order item received_quantity (W0 Decision 3: always update, warn if exceeds)
            if (item.getOrderItemId() != null) {
                adjustOrderItemReceivedQuantity(item.getOrderItemId(), item.getActualQuantity(), false);
            }
        }

        // Recalculate total amount
        BigDecimal totalAmount = items.stream()
                .map(MatReceiptItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LambdaUpdateWrapper<MatReceipt> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatReceipt::getId, receiptId)
                .set(MatReceipt::getTotalAmount, totalAmount);
        matReceiptMapper.update(null, updateWrapper);
    }

    private void adjustOrderItemReceivedQuantity(Long orderItemId, BigDecimal delta, boolean floorAtZero) {
        int retries = 0;
        while (true) {
            MatPurchaseOrderItem orderItem = matPurchaseOrderItemMapper.selectById(orderItemId);
            if (orderItem == null || !orderItem.getTenantId().equals(UserContext.getCurrentTenantId())) {
                return;
            }
            BigDecimal currentReceived = orderItem.getReceivedQuantity() != null
                    ? orderItem.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal nextReceived = currentReceived.add(delta != null ? delta : BigDecimal.ZERO);
            if (floorAtZero && nextReceived.compareTo(BigDecimal.ZERO) < 0) {
                nextReceived = BigDecimal.ZERO;
            }
            orderItem.setReceivedQuantity(nextReceived);
            int updated = matPurchaseOrderItemMapper.updateById(orderItem);
            if (updated > 0) {
                return;
            }
            if (++retries >= ORDER_ITEM_UPDATE_MAX_RETRIES) {
                throw new BusinessException("ORDER_ITEM_CONCURRENT_CONFLICT", "采购订单明细并发更新冲突，请稍后重试");
            }
        }
    }
}
