package com.cgcpms.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final CtContractMapper contractMapper;
    private final MatWarehouseMapper warehouseMapper;
    private final MdMaterialMapper materialMapper;
    private final MatStockService stockService;
    private final CostGenerationService costGenerationService;
    private final ProjectAccessChecker projectAccessChecker;
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
        checkProjectAccess(r.getProjectId(), "查看领料申请");
        return assembler.assemble(r);
    }

    // ================================================================
    // 查询明细
    // ================================================================

    public List<MatRequisitionItemVO> getItems(Long requisitionId) {
        MatRequisition r = requisitionMapper.selectById(requisitionId);
        if (r == null || !r.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");
        checkProjectAccess(r.getProjectId(), "查看领料申请明细");

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
        checkProjectAccess(requisition.getProjectId(), "创建领料申请");
        validateRelations(requisition);
        // Auto-generate requisition code: REQ-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "REQ-" + today + "-";

        requisition.setApprovalStatus("DRAFT");
        requisition.setStockOutFlag(0);
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
        checkProjectAccess(existing.getProjectId(), "编辑领料申请");

        // 驳回后允许修改，修改即回到草稿等待重新提交
        if (!"DRAFT".equals(existing.getApprovalStatus()) && !"REJECTED".equals(existing.getApprovalStatus()))
            throw new BusinessException("REQUISITION_IN_APPROVAL", "领料申请审批中或已审批，不可编辑");

        // Prevent overwriting approval status via update
        requisition.setApprovalStatus("DRAFT");
        requisition.setRequisitionCode(existing.getRequisitionCode());
        requisition.setStockOutFlag(existing.getStockOutFlag());
        validateRelations(requisition);

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
        checkProjectAccess(existing.getProjectId(), "删除领料申请");

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
        checkProjectAccess(requisition.getProjectId(), "提交领料审批");

        // 只允许草稿状态提交
        if (!"DRAFT".equals(requisition.getApprovalStatus()))
            throw new BusinessException("REQUISITION_ALREADY_SUBMITTED", "领料申请已提交审批，不可重复提交");

        // 必须有申请编号
        if (requisition.getRequisitionCode() == null || requisition.getRequisitionCode().isBlank())
            throw new BusinessException("REQUISITION_NO_CODE", "申请编号不能为空，无法提交审批");
        if (requisition.getRequisitionDate() == null || requisition.getContractId() == null
                || requisition.getWarehouseId() == null) {
            throw new BusinessException("REQUISITION_INFO_INCOMPLETE", "领料日期、合同和仓库不能为空");
        }
        validateRelations(requisition);

        // Check items exist with quantity > 0
        List<MatRequisitionItem> items = requisitionItemMapper.selectList(
                new LambdaQueryWrapper<MatRequisitionItem>()
                        .eq(MatRequisitionItem::getRequisitionId, requisitionId)
                        .eq(MatRequisitionItem::getTenantId, UserContext.getCurrentTenantId()));
        if (items.isEmpty())
            throw new BusinessException("REQUISITION_NO_ITEMS", "领料申请没有明细，无法提交审批");
        boolean hasInvalidQuantity = items.stream().anyMatch(i -> i.getQuantity() == null
                || i.getQuantity().compareTo(BigDecimal.ZERO) <= 0);
        if (hasInvalidQuantity)
            throw new BusinessException("REQUISITION_QUANTITY_INVALID", "每条领料明细数量都必须大于0");
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
        checkProjectAccess(requisition.getProjectId(), "编辑领料申请明细");

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
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0)
                throw new BusinessException("REQUISITION_QUANTITY_INVALID", "领料数量必须大于0");
            MdMaterial material = materialMapper.selectById(item.getMaterialId());
            if (material == null || !tenantId.equals(material.getTenantId()) || !"ENABLE".equals(material.getStatus())) {
                throw new BusinessException("MATERIAL_INVALID", "领料物料不存在或已停用");
            }
            if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
            if (item.getUnitPrice().signum() < 0)
                throw new BusinessException("REQUISITION_PRICE_INVALID", "领料参考单价不能为负数");
            item.setAmount(item.getQuantity().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
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

    /**
     * 仓管员执行实际出库。审批仅授权领料，不直接改变库存；本方法才是库存事实发生点。
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeStockOut(Long requisitionId) {
        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null || !UserContext.getCurrentTenantId().equals(requisition.getTenantId())) {
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");
        }
        checkProjectAccess(requisition.getProjectId(), "执行领料出库");
        if (!"APPROVED".equals(requisition.getApprovalStatus())) {
            throw new BusinessException("REQUISITION_NOT_APPROVED", "领料申请审批通过后才能出库");
        }
        if (Integer.valueOf(1).equals(requisition.getStockOutFlag())) {
            return;
        }
        validateRelations(requisition);
        List<MatRequisitionItem> items = requisitionItemMapper.selectList(
                new LambdaQueryWrapper<MatRequisitionItem>()
                        .eq(MatRequisitionItem::getRequisitionId, requisitionId)
                        .eq(MatRequisitionItem::getTenantId, UserContext.getCurrentTenantId()));
        if (items.isEmpty()) {
            throw new BusinessException("REQUISITION_NO_ITEMS", "领料申请没有明细，无法出库");
        }

        int claimed = requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .eq(MatRequisition::getApprovalStatus, "APPROVED")
                .eq(MatRequisition::getStockOutFlag, 0)
                .set(MatRequisition::getStockOutFlag, 2));
        if (claimed != 1) {
            MatRequisition latest = requisitionMapper.selectById(requisitionId);
            if (latest != null && Integer.valueOf(1).equals(latest.getStockOutFlag())) return;
            throw new BusinessException("REQUISITION_STOCK_OUT_CONFLICT", "领料出库正在处理或状态已变化");
        }

        BigDecimal totalIssuedAmount = BigDecimal.ZERO;
        for (MatRequisitionItem item : items) {
            if (item.getMaterialId() == null || item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new BusinessException("REQUISITION_ITEM_INVALID", "领料明细物料或数量非法");
            }
            MatStockService.StockMovementResult movement = stockService.stockOutValued(
                    requisition.getWarehouseId(), item.getMaterialId(), item.getQuantity(),
                    "MAT_REQUISITION", requisitionId, item.getId());
            item.setUnitPrice(movement.unitCost());
            item.setAmount(movement.amount());
            requisitionItemMapper.updateById(item);
            totalIssuedAmount = totalIssuedAmount.add(movement.amount());
        }

        requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .set(MatRequisition::getTotalAmount, totalIssuedAmount));
        costGenerationService.generateCost("MAT_REQUISITION", requisitionId);

        requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .eq(MatRequisition::getStockOutFlag, 2)
                .set(MatRequisition::getStockOutFlag, 1)
                .set(MatRequisition::getStockOutBy, UserContext.getCurrentUserId())
                .set(MatRequisition::getStockOutAt, LocalDateTime.now()));
    }

    private void validateRelations(MatRequisition requisition) {
        if (requisition.getProjectId() == null) {
            throw new BusinessException("PROJECT_REQUIRED", "领料申请必须关联项目");
        }
        if (requisition.getContractId() != null) {
            CtContract contract = contractMapper.selectById(requisition.getContractId());
            if (contract == null || !UserContext.getCurrentTenantId().equals(contract.getTenantId())
                    || !Objects.equals(requisition.getProjectId(), contract.getProjectId())
                    || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())) {
                throw new BusinessException("REQUISITION_CONTRACT_INVALID", "领料合同不存在、不属于项目或不可履约");
            }
        }
        if (requisition.getWarehouseId() != null) {
            MatWarehouse warehouse = warehouseMapper.selectById(requisition.getWarehouseId());
            if (warehouse == null || !UserContext.getCurrentTenantId().equals(warehouse.getTenantId())
                    || !Objects.equals(requisition.getProjectId(), warehouse.getProjectId())
                    || !"ENABLE".equals(warehouse.getStatus())) {
                throw new BusinessException("REQUISITION_WAREHOUSE_INVALID", "领料仓库不存在、已停用或不属于项目");
            }
        }
    }

    private void checkProjectAccess(Long projectId, String action) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_REQUIRED", "领料申请必须关联项目");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }
}
