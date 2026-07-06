package com.cgcpms.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.mapper.MatRequisitionItemMapper;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.requisition.vo.MatRequisitionItemVO;
import com.cgcpms.requisition.vo.MatRequisitionVO;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 领料申请服务 — CRUD / 提交审批 / 明细批量操作。
 * <p>
 * 编码规则：REQ-yyyyMMdd-XXX（自动序号，按天递增）。
 * 审批流使用 MATERIAL_REQUISITION 业务类型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatRequisitionService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final MatRequisitionMapper requisitionMapper;
    private final MatRequisitionItemMapper requisitionItemMapper;
    private final WorkflowEngine workflowEngine;
    private final MatRequisitionAssembler assembler;

    // ================================================================
    // 分页查询
    // ================================================================

    public PageResult<MatRequisitionVO> getPage(long pageNo, long pageSize, Long projectId,
                                                 Long contractId, Long warehouseId,
                                                 String approvalStatus, String requisitionCode) {
        LambdaQueryWrapper<MatRequisition> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) wrapper.eq(MatRequisition::getProjectId, projectId);
        if (contractId != null) wrapper.eq(MatRequisition::getContractId, contractId);
        if (warehouseId != null) wrapper.eq(MatRequisition::getWarehouseId, warehouseId);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(MatRequisition::getApprovalStatus, approvalStatus);
        if (StringUtils.hasText(requisitionCode)) wrapper.like(MatRequisition::getRequisitionCode, requisitionCode);
        wrapper.eq(MatRequisition::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(MatRequisition::getCreatedTime);

        Page<MatRequisition> page = requisitionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<MatRequisitionVO> vos = assembler.assembleBatch(page.getRecords());
        IPage<MatRequisitionVO> voPage = new Page<>(pageNo, pageSize, page.getTotal());
        voPage.setRecords(vos);
        return PageResult.of(voPage);
    }

    // ================================================================
    // 查询详情
    // ================================================================

    public MatRequisitionVO getById(Long id) {
        MatRequisition r = requisitionMapper.selectById(id);
        if (r == null || !r.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");
        return assembler.assemble(r);
    }

    // ================================================================
    // 查询明细
    // ================================================================

    public List<MatRequisitionItemVO> getItems(Long requisitionId) {
        MatRequisition r = requisitionMapper.selectById(requisitionId);
        if (r == null || !r.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");

        List<MatRequisitionItem> items = requisitionItemMapper.selectList(
                new LambdaQueryWrapper<MatRequisitionItem>()
                        .eq(MatRequisitionItem::getRequisitionId, requisitionId)
                        .eq(MatRequisitionItem::getTenantId, UserContext.getCurrentTenantId()));
        return assembler.assembleItems(items);
    }

    // ================================================================
    // 创建
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long create(MatRequisition requisition) {
        // Auto-generate requisition code: REQ-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "REQ-" + today + "-";

        requisition.setApprovalStatus("DRAFT");
        requisition.setTenantId(UserContext.getCurrentTenantId());

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            requisition.setRequisitionCode(nextRequisitionCode(prefix, attempt));
            try {
                requisitionMapper.insert(requisition);
                return requisition.getId();
            } catch (DuplicateKeyException e) {
                log.warn("领料申请编号冲突，重试生成 requisitionCode={}", requisition.getRequisitionCode());
            }
        }

        throw new BusinessException("REQUISITION_CODE_CONFLICT", "领料申请编号生成冲突，请重试");
    }

    private String nextRequisitionCode(String prefix, int offset) {
        LambdaQueryWrapper<MatRequisition> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(MatRequisition::getRequisitionCode, prefix)
                .eq(MatRequisition::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(MatRequisition::getRequisitionCode);
        Page<MatRequisition> page = new Page<>(0, 1);
        Page<MatRequisition> result = requisitionMapper.selectPage(page, wrapper);
        MatRequisition last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1;
        if (last != null && last.getRequisitionCode() != null
                && last.getRequisitionCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getRequisitionCode().substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getRequisitionCode(), e);
            }
        }
        return prefix + String.format("%03d", seq + offset);
    }

    // ================================================================
    // 更新
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void update(MatRequisition requisition) {
        MatRequisition existing = requisitionMapper.selectById(requisition.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");

        // Only DRAFT can be updated
        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("REQUISITION_IN_APPROVAL", "领料申请审批中或已审批，不可编辑");

        // Prevent overwriting approval status via update
        requisition.setApprovalStatus(existing.getApprovalStatus());
        requisition.setRequisitionCode(existing.getRequisitionCode());
        requisition.setStockOutFlag(existing.getStockOutFlag());

        requisitionMapper.updateById(requisition);
    }

    // ================================================================
    // 删除
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MatRequisition existing = requisitionMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("REQUISITION_IN_APPROVAL", "领料申请审批中或已审批，不可删除");

        // Delete items first
        LambdaQueryWrapper<MatRequisitionItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(MatRequisitionItem::getRequisitionId, id)
                .eq(MatRequisitionItem::getTenantId, UserContext.getCurrentTenantId());
        requisitionItemMapper.delete(itemWrapper);

        requisitionMapper.deleteById(id);
    }

    // ================================================================
    // 提交审批
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long requisitionId) {
        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null || !requisition.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");

        // 只允许草稿状态提交
        if (!"DRAFT".equals(requisition.getApprovalStatus()))
            throw new BusinessException("REQUISITION_ALREADY_SUBMITTED", "领料申请已提交审批，不可重复提交");

        // 必须有申请编号
        if (requisition.getRequisitionCode() == null || requisition.getRequisitionCode().isBlank())
            throw new BusinessException("REQUISITION_NO_CODE", "申请编号不能为空，无法提交审批");

        // Check items exist with quantity > 0
        List<MatRequisitionItem> items = requisitionItemMapper.selectList(
                new LambdaQueryWrapper<MatRequisitionItem>()
                        .eq(MatRequisitionItem::getRequisitionId, requisitionId)
                        .eq(MatRequisitionItem::getTenantId, UserContext.getCurrentTenantId()));
        if (items.isEmpty())
            throw new BusinessException("REQUISITION_NO_ITEMS", "领料申请没有明细，无法提交审批");
        boolean hasQuantity = items.stream().anyMatch(i -> i.getQuantity() != null
                && i.getQuantity().compareTo(BigDecimal.ZERO) > 0);
        if (!hasQuantity)
            throw new BusinessException("REQUISITION_ZERO_QTY", "领料申请明细数量均为0，无法提交审批");
        boolean hasMissingMaterial = items.stream().anyMatch(i -> i.getMaterialId() == null);
        if (hasMissingMaterial)
            throw new BusinessException("REQUISITION_ITEM_NO_MATERIAL", "领料申请明细物料不能为空，无法提交审批");

        // 更新审批状态为审批中
        LambdaUpdateWrapper<MatRequisition> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatRequisition::getId, requisitionId)
                .set(MatRequisition::getApprovalStatus, "APPROVING");
        requisitionMapper.update(null, updateWrapper);

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                "MATERIAL_REQUISITION",
                requisitionId,
                requisition.getRequisitionCode(),
                requisition.getTotalAmount(),
                requisition.getProjectId(),
                requisition.getContractId(),
                null, null, null);
    }

    // ================================================================
    // 批量保存明细
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void saveItemsBatch(Long requisitionId, List<MatRequisitionItem> items) {
        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null || !requisition.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");

        if (!"DRAFT".equals(requisition.getApprovalStatus()))
            throw new BusinessException("REQUISITION_IN_APPROVAL", "领料申请审批中或已审批，不可编辑明细");

        // Delete old items (tenant isolation)
        LambdaQueryWrapper<MatRequisitionItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MatRequisitionItem::getRequisitionId, requisitionId)
                .eq(MatRequisitionItem::getTenantId, UserContext.getCurrentTenantId());
        requisitionItemMapper.delete(deleteWrapper);

        // Insert new items
        Long tenantId = UserContext.getCurrentTenantId();
        for (MatRequisitionItem item : items) {
            item.setRequisitionId(requisitionId);
            item.setTenantId(tenantId);
            if (item.getMaterialId() == null)
                throw new BusinessException("REQUISITION_ITEM_NO_MATERIAL", "领料申请明细物料不能为空");
            if (item.getQuantity() == null) item.setQuantity(BigDecimal.ZERO);
            if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
            if (item.getAmount() == null) item.setAmount(BigDecimal.ZERO);
            requisitionItemMapper.insert(item);
        }

        // Recalculate totalAmount on parent header
        BigDecimal totalAmount = items.stream()
                .map(MatRequisitionItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LambdaUpdateWrapper<MatRequisition> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatRequisition::getId, requisitionId)
                .set(MatRequisition::getTotalAmount, totalAmount);
        requisitionMapper.update(null, updateWrapper);
    }
}
