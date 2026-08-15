package com.cgcpms.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.security.BusinessAmountAccess;
import com.cgcpms.project.service.ProjectExecutionGuard;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final MdPartnerMapper partnerMapper;
    private final MatStockService stockService;
    private final CostGenerationService costGenerationService;
    private final ProjectAccessChecker projectAccessChecker;
    private final ProjectExecutionGuard projectExecutionGuard;
    private final WorkflowEngine workflowEngine;
    private final MatRequisitionAssembler assembler;
    private final CodeGenerationService codeGenerationService;
    private final JdbcTemplate jdbc;

    // ================================================================
    // 分页查询
    // ================================================================

    public PageResult<MatRequisitionVO> getPage(long pageNo, long pageSize, Long projectId,
                                                 Long contractId, Long warehouseId,
                                                 String approvalStatus, String requisitionCode,
                                                 LocalDate dateFrom, LocalDate dateTo) {
        List<Long> projectIds;
        if (projectId != null) {
            checkProjectAccess(projectId, "查询领料申请");
            projectIds = List.of(projectId);
        } else {
            projectIds = projectAccessChecker.accessibleProjectIds();
            if (projectIds.isEmpty()) {
                return new PageResult<>(pageNo, pageSize, 0, List.of());
            }
        }
        LambdaQueryWrapper<MatRequisition> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MatRequisition::getProjectId, projectIds);
        if (contractId != null) wrapper.eq(MatRequisition::getContractId, contractId);
        if (warehouseId != null) wrapper.eq(MatRequisition::getWarehouseId, warehouseId);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(MatRequisition::getApprovalStatus, approvalStatus);
        if (StringUtils.hasText(requisitionCode)) wrapper.like(MatRequisition::getRequisitionCode, requisitionCode);
        if (dateFrom != null) wrapper.ge(MatRequisition::getRequisitionDate, dateFrom);
        if (dateTo != null) wrapper.le(MatRequisition::getRequisitionDate, dateTo);
        wrapper.eq(MatRequisition::getTenantId, UserContext.getCurrentTenantId());
        if (selfOnly("requisition:query")) wrapper.eq(MatRequisition::getRequisitionerId, UserContext.getCurrentUserId());
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
        requireOwner(r, "requisition:query");
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
        requireOwner(r, "requisition:query");
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
        requisition.setApprovalStatus("DRAFT");
        requisition.setStockOutFlag(0);
        requisition.setTenantId(UserContext.getCurrentTenantId());
        requisition.setTotalAmount(BigDecimal.ZERO.setScale(2));
        if (selfOnly("requisition:add")) requisition.setRequisitionerId(UserContext.getCurrentUserId());

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            requisition.setRequisitionCode(codeGenerationService.nextCode(
                    requisitionMapper, MatRequisition::getRequisitionCode,
                    "REQ-", requisition.getTenantId(), true, attempt));
            try {
                requisitionMapper.insert(requisition);
                return requisition.getId();
            } catch (DuplicateKeyException e) {
                log.warn("领料申请编号冲突，重试生成 requisitionCode={}", requisition.getRequisitionCode());
            }
        }

        throw new BusinessException("REQUISITION_CODE_CONFLICT", "领料申请编号生成冲突，请重试");
    }

    // ================================================================
    // 更新
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void update(MatRequisition requisition) {
        MatRequisition existing = requisitionMapper.selectById(requisition.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");
        requireOwner(existing, "requisition:edit");
        checkProjectAccess(existing.getProjectId(), "编辑领料申请");

        // 驳回后允许修改，修改即回到草稿等待重新提交
        if (!"DRAFT".equals(existing.getApprovalStatus()) && !"REJECTED".equals(existing.getApprovalStatus()))
            throw new BusinessException("REQUISITION_IN_APPROVAL", "领料申请审批中或已审批，不可编辑");

        // Prevent overwriting approval status via update
        requisition.setApprovalStatus("DRAFT");
        requisition.setRequisitionCode(existing.getRequisitionCode());
        requisition.setStockOutFlag(existing.getStockOutFlag());
        requisition.setTotalAmount(existing.getTotalAmount());
        if (selfOnly("requisition:edit")) requisition.setRequisitionerId(existing.getRequisitionerId());
        checkProjectAccess(requisition.getProjectId(), "编辑领料申请");
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
        requireOwner(existing, "requisition:delete");
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
        requireOwner(requisition, "requisition:submit");
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
        if (!BusinessAmountAccess.canView() && items != null && items.stream().anyMatch(item ->
                item.getUnitPrice() != null || item.getAmount() != null)) {
            throw new BusinessException("AMOUNT_FIELD_FORBIDDEN", "当前账号不得提交领料金额字段");
        }
        MatRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null || !requisition.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");
        requireOwner(requisition, "requisition:edit");
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
            if (item.getMaterialId() == null)
                throw new BusinessException("REQUISITION_ITEM_NO_MATERIAL", "领料申请明细物料不能为空");
        }
        for (Long materialId : items.stream()
                .map(MatRequisitionItem::getMaterialId)
                .distinct()
                .sorted()
                .toList()) {
            MdMaterial material = materialMapper.selectByIdForUpdate(materialId, tenantId);
            if (material == null || !"ENABLE".equals(material.getStatus())) {
                throw new BusinessException("MATERIAL_INVALID", "领料物料不存在或已停用");
            }
        }
        for (MatRequisitionItem item : items) {
            item.setRequisitionId(requisitionId);
            item.setTenantId(tenantId);
            projectExecutionGuard.requireActiveWbs(requisition.getProjectId(), item.getWbsTaskId(), "保存领料申请明细");
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0)
                throw new BusinessException("REQUISITION_QUANTITY_INVALID", "领料数量必须大于0");
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
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

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
                    "MAT_REQUISITION", requisitionId, item.getId(), item.getWbsTaskId());
            item.setUnitPrice(movement.unitCost());
            item.setAmount(movement.amount());
            requisitionItemMapper.updateById(item);
            totalIssuedAmount = totalIssuedAmount.add(movement.amount());
        }

        requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .set(MatRequisition::getTotalAmount, totalIssuedAmount.setScale(2, RoundingMode.HALF_UP)));
        costGenerationService.generateCost("MAT_REQUISITION", requisitionId);

        requisitionMapper.update(null, new LambdaUpdateWrapper<MatRequisition>()
                .eq(MatRequisition::getId, requisitionId)
                .eq(MatRequisition::getStockOutFlag, 2)
                .set(MatRequisition::getStockOutFlag, 1)
                .set(MatRequisition::getStockOutBy, UserContext.getCurrentUserId())
                .set(MatRequisition::getStockOutAt, LocalDateTime.now()));
    }

    public Map<String, Object> formOptions(Long projectId) {
        checkProjectAccess(projectId, "读取领料申请表单选项");
        Long tenantId = UserContext.getCurrentTenantId();
        List<MatWarehouse> warehouseRows = warehouseMapper.selectList(new LambdaQueryWrapper<MatWarehouse>()
                .eq(MatWarehouse::getTenantId, tenantId).eq(MatWarehouse::getProjectId, projectId)
                .eq(MatWarehouse::getStatus, "ENABLE").orderByAsc(MatWarehouse::getWarehouseCode));
        List<CtContract> contractRows = contractMapper.selectList(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTenantId, tenantId).eq(CtContract::getProjectId, projectId)
                .eq(CtContract::getContractStatus, ContractStatusConstants.STATUS_PERFORMING)
                .orderByAsc(CtContract::getContractCode));
        Set<Long> partnerIds = contractRows.stream().map(CtContract::getPartyBId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<MdPartner> partnerRows = partnerIds.isEmpty() ? List.of()
                : partnerMapper.selectList(new LambdaQueryWrapper<MdPartner>()
                .eq(MdPartner::getTenantId, tenantId).in(MdPartner::getId, partnerIds)
                .eq(MdPartner::getStatus, "ENABLE").orderByAsc(MdPartner::getPartnerCode));
        List<Map<String, Object>> wbsTaskRows = jdbc.query("""
                SELECT task.id, task.task_code, task.task_name
                FROM project_wbs_task task
                JOIN project_schedule_plan schedule
                  ON schedule.tenant_id=task.tenant_id
                 AND schedule.id=task.schedule_plan_id
                 AND schedule.project_id=task.project_id
                 AND schedule.deleted_flag=0
                 AND schedule.status='ACTIVE'
                WHERE task.tenant_id=? AND task.project_id=? AND task.deleted_flag=0
                ORDER BY task.sort_order, task.task_code
                """, (result, rowNum) -> option(
                "id", result.getLong("id"), "taskCode", result.getString("task_code"),
                "taskName", result.getString("task_name")), tenantId, projectId);

        return Map.of(
                "warehouses", warehouseRows.stream().map(row -> option(
                        "id", row.getId(), "warehouseCode", row.getWarehouseCode(),
                        "warehouseName", row.getWarehouseName(), "projectId", row.getProjectId())).toList(),
                "materials", List.of(),
                "partners", partnerRows.stream().map(row -> option(
                        "id", row.getId(), "partnerCode", row.getPartnerCode(),
                        "partnerName", row.getPartnerName())).toList(),
                "wbsTasks", wbsTaskRows,
                "contracts", contractRows.stream().map(row -> option(
                        "id", row.getId(), "contractCode", row.getContractCode(),
                        "contractName", row.getContractName(), "projectId", row.getProjectId())).toList());
    }

    public List<Map<String, Object>> materialOptions(Long projectId, Long warehouseId, String keyword) {
        checkProjectAccess(projectId, "搜索领料物料候选");
        Long tenantId = UserContext.getCurrentTenantId();
        Integer warehouseCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mat_warehouse
                WHERE id=? AND tenant_id=? AND project_id=? AND status='ENABLE' AND deleted_flag=0
                """, Integer.class, warehouseId, tenantId, projectId);
        if (warehouseCount == null || warehouseCount != 1)
            throw new BusinessException("REQUISITION_WAREHOUSE_INVALID", "领料仓库不存在、已停用或不属于项目");
        String value = keyword == null ? "" : keyword.trim();
        if (value.length() > 100)
            throw new BusinessException("REQUISITION_MATERIAL_KEYWORD_TOO_LONG", "物料搜索关键词不能超过100个字符");
        String pattern = "%" + value + "%";
        return jdbc.query("""
                SELECT material.id,material.material_code,material.material_name,
                       material.specification,material.unit
                FROM mat_stock stock
                JOIN md_material material
                  ON material.id=stock.material_id
                 AND material.tenant_id=stock.tenant_id
                 AND material.status='ENABLE'
                 AND material.deleted_flag=0
                WHERE stock.tenant_id=? AND stock.warehouse_id=? AND stock.deleted_flag=0
                  AND stock.available_qty>0
                  AND (?='' OR material.material_code LIKE ? OR material.material_name LIKE ?
                       OR COALESCE(material.specification,'') LIKE ?)
                ORDER BY material.material_code,material.id
                LIMIT 50
                """, (result, rowNum) -> option(
                "id", result.getLong("id"), "materialCode", result.getString("material_code"),
                "materialName", result.getString("material_name"),
                "specification", result.getString("specification"), "unit", result.getString("unit")),
                tenantId, warehouseId, value, pattern, pattern, pattern);
    }

    private Map<String, Object> option(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private void requireOwner(MatRequisition requisition, String fullAuthority) {
        if (selfOnly(fullAuthority)
                && !Objects.equals(requisition.getRequisitionerId(), UserContext.getCurrentUserId())) {
            throw new BusinessException("REQUISITION_NOT_FOUND", "领料申请不存在");
        }
    }

    private boolean selfOnly(String fullAuthority) {
        return !UserContext.hasAnyRole("ADMIN", "SUPER_ADMIN")
                && hasAuthority("requisition:self") && !hasAuthority(fullAuthority);
    }

    private boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
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
