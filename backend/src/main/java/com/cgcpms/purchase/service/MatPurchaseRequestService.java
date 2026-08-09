package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.file.service.FileLifecycleGateway;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.procurement.service.ProcurementIntegrityService;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.purchase.vo.MatPurchaseRequestItemVO;
import com.cgcpms.purchase.vo.MatPurchaseRequestVO;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatPurchaseRequestService {

    private final MatPurchaseRequestMapper requestMapper;
    private final MatPurchaseRequestItemMapper requestItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final CtContractMapper ctContractMapper;
    private final WorkflowEngine workflowEngine;
    private final WfInstanceMapper wfInstanceMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbcTemplate;
    private final ProcurementIntegrityService integrityService;
    private final FileLifecycleGateway fileLifecycleGateway;
    private final CodeGenerationService codeGenerationService;

    // ================================================================
    // 分页查询
    // ================================================================

    public PageResult<MatPurchaseRequestVO> getPage(long pageNum, long pageSize, Long projectId,
                                                String approvalStatus, String status, String requestCode) {
        LambdaQueryWrapper<MatPurchaseRequest> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, "查询采购申请");
            wrapper.eq(MatPurchaseRequest::getProjectId, projectId);
        } else {
            List<Long> accessibleProjectIds = projectAccessChecker.accessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                wrapper.eq(MatPurchaseRequest::getProjectId, -1L);
            } else {
                wrapper.in(MatPurchaseRequest::getProjectId, accessibleProjectIds);
            }
        }
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(MatPurchaseRequest::getApprovalStatus, approvalStatus);
        if (StringUtils.hasText(status)) wrapper.eq(MatPurchaseRequest::getStatus, status);
        if (StringUtils.hasText(requestCode)) wrapper.like(MatPurchaseRequest::getRequestCode, requestCode);
        wrapper.eq(MatPurchaseRequest::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(MatPurchaseRequest::getCreatedTime);

        Page<MatPurchaseRequest> page = requestMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // Batch-prefetch project names to avoid N+1
        List<MatPurchaseRequest> records = page.getRecords();
        Set<Long> projectIds = records.stream().map(MatPurchaseRequest::getProjectId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectByIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));

        // Batch-prefetch contract names to avoid N+1
        Set<Long> contractIds = records.stream().map(MatPurchaseRequest::getContractId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectByIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, BigDecimal> requestTotals = requestTotals(
                UserContext.getCurrentTenantId(),
                records.stream().map(MatPurchaseRequest::getId).collect(Collectors.toSet()));

        IPage<MatPurchaseRequestVO> voPage = page.convert(
                r -> toVO(r, projectNames, contractNames, requestTotals.getOrDefault(r.getId(), BigDecimal.ZERO)));
        return PageResult.of(voPage);
    }

    // ================================================================
    // 查询详情
    // ================================================================

    public MatPurchaseRequestVO getById(Long id) {
        MatPurchaseRequest r = requestMapper.selectById(id);
        if (r == null || !r.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        projectAccessChecker.checkAccess(r.getProjectId(), "查看采购申请");
        return toVO(r, requestTotals(r.getTenantId(), Set.of(r.getId())).getOrDefault(r.getId(), BigDecimal.ZERO));
    }

    // ================================================================
    // 查询明细
    // ================================================================

    public List<MatPurchaseRequestItemVO> getItems(Long requestId) {
        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        projectAccessChecker.checkAccess(request.getProjectId(), "查看采购申请明细");

        List<MatPurchaseRequestItem> items = requestItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getRequestId, requestId)
                        .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId()));
        Set<Long> materialIds = items.stream().map(MatPurchaseRequestItem::getMaterialId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> materialNames = materialIds.isEmpty() ? Map.of()
                : mdMaterialMapper.selectByIds(materialIds).stream()
                        .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));
        return items.stream().map(item -> toItemVO(item, materialNames)).collect(Collectors.toList());
    }

    // ================================================================
    // 创建
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long create(MatPurchaseRequest request) {
        validateProjectRequired(request.getProjectId());
        projectAccessChecker.checkAccess(request.getProjectId(), "创建采购申请");
        request.setContractId(null);
        request.setPurpose(null);

        Long tenantId = UserContext.getCurrentTenantId();
        request.setApprovalStatus("DRAFT");
        request.setStatus("DRAFT");
        request.setTenantId(tenantId);

        for (int attempt = 0; attempt < 3; attempt++) {
            request.setRequestCode(codeGenerationService.nextCode(
                    requestMapper, MatPurchaseRequest::getRequestCode,
                    "PR-", tenantId, true, attempt));
            try {
                requestMapper.insert(request);
                return request.getId();
            } catch (DuplicateKeyException e) {
                request.setId(null);
                log.warn("采购申请编号冲突，重试生成 requestCode={}", request.getRequestCode());
            }
        }
        throw new BusinessException("REQUEST_CODE_CONFLICT", "采购申请编号生成冲突，请重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MatPurchaseRequest request, List<MatPurchaseRequestItem> items) {
        if (items == null || items.isEmpty() || items.size() > 200) {
            throw new BusinessException("PURCHASE_REQUEST_ITEMS_INVALID", "采购申请明细必须为1到200条");
        }
        Long requestId = create(request);
        saveItemsBatch(requestId, items);
        return requestId;
    }

    // ================================================================
    // 更新
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void update(MatPurchaseRequest request) {
        MatPurchaseRequest existing = requestMapper.selectById(request.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        projectAccessChecker.checkAccess(existing.getProjectId(), "编辑采购申请");

        // Only DRAFT can be updated
        if (!List.of("DRAFT", "REJECTED").contains(existing.getApprovalStatus()))
            throw new BusinessException("REQUEST_IN_APPROVAL", "采购申请审批中或已审批，不可编辑");

        // Prevent overwriting approval status via update
        request.setApprovalStatus("DRAFT");
        request.setStatus("DRAFT");
        Long projectId = request.getProjectId() != null ? request.getProjectId() : existing.getProjectId();
        validateProjectRequired(projectId);
        if (!Objects.equals(projectId, existing.getProjectId())) {
            projectAccessChecker.checkAccess(projectId, "编辑采购申请");
        }
        request.setContractId(null);
        // purpose 已退出新流程；置空使 MyBatis 不更新历史列。
        request.setPurpose(null);

        requestMapper.updateById(request);
    }

    // ================================================================
    // 提交审批
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long requestId) {
        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        // 只允许草稿状态提交
        if (!"DRAFT".equals(request.getApprovalStatus()))
            throw new BusinessException("PURCHASE_REQUEST_ALREADY_SUBMITTED", "采购申请已提交审批，不可重复提交");

        // 必须有申请编号
        if (request.getRequestCode() == null || request.getRequestCode().isBlank())
            throw new BusinessException("PURCHASE_REQUEST_NO_CODE", "申请编号不能为空，无法提交审批");

        projectAccessChecker.checkAccess(request.getProjectId(), "提交采购申请审批");
        integrityService.requireActiveProject(request.getProjectId(), "提交采购申请");
        integrityService.requireCleanAttachment("PURCHASE_REQUEST", requestId);

        List<MatPurchaseRequestItem> items = requestItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getRequestId, requestId)
                        .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId()));
        if (items.isEmpty())
            throw new BusinessException("PURCHASE_REQUEST_NO_ITEMS", "采购申请没有明细，无法提交审批");

        for (MatPurchaseRequestItem item : items) {
            validateRequestItemForSubmission(request, item);
            item.setApprovedQuantity(item.getQuantity());
            item.setApprovalVersion(0);
            requestItemMapper.updateById(item);
        }

        // 更新审批状态为审批中
        LambdaUpdateWrapper<MatPurchaseRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "APPROVING");
        requestMapper.update(null, updateWrapper);

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submitPurchaseRequest(userId, username, tenantId,
                "PURCHASE_REQUEST",
                requestId,
                request.getRequestCode(),
                null,
                request.getProjectId(),
                null,
                null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resubmitForApproval(Long requestId, Long instanceId) {
        MatPurchaseRequest request = requestMapper.selectById(requestId);
        WfInstance instance = wfInstanceMapper.selectById(instanceId);
        if (request == null || !Objects.equals(request.getTenantId(), UserContext.getCurrentTenantId())
                || instance == null || !Objects.equals(instance.getBusinessId(), requestId)
                || !"PURCHASE_REQUEST".equals(instance.getBusinessType())) {
            throw new BusinessException("PURCHASE_REQUEST_RESUBMIT_MISMATCH", "采购申请与审批实例不匹配");
        }
        projectAccessChecker.checkAccess(request.getProjectId(), "重新提交采购申请审批");
        List<MatPurchaseRequestItem> items = requestItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getRequestId, requestId)
                        .eq(MatPurchaseRequestItem::getTenantId, request.getTenantId()));
        if (items.isEmpty()) throw new BusinessException("PURCHASE_REQUEST_NO_ITEMS", "采购申请没有明细");
        for (MatPurchaseRequestItem item : items) {
            validateRequestItemForSubmission(request, item);
            item.setApprovedQuantity(item.getQuantity());
            item.setApprovalVersion((item.getApprovalVersion() == null ? 0 : item.getApprovalVersion()) + 1);
            requestItemMapper.updateById(item);
        }
        workflowEngine.resubmitPurchaseRequest(instanceId, UserContext.getCurrentUserId(), UserContext.getCurrentUsername());
        requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                .eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "APPROVING"));
    }

    // ================================================================
    // 删除
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MatPurchaseRequest existing = requestMapper.selectByIdForUpdate(id, UserContext.getCurrentTenantId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        projectAccessChecker.checkAccess(existing.getProjectId(), "删除采购申请");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("REQUEST_IN_APPROVAL", "采购申请审批中或已审批，不可删除");

        fileLifecycleGateway.deleteAllForBusinessCascade("PURCHASE_REQUEST", id);

        // Delete items first
        LambdaQueryWrapper<MatPurchaseRequestItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(MatPurchaseRequestItem::getRequestId, id)
                .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId());
        requestItemMapper.delete(itemWrapper);

        requestMapper.deleteById(id);
    }

    // ================================================================
    // 批量保存明细
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void saveItemsBatch(Long requestId, List<MatPurchaseRequestItem> items) {
        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        if (!List.of("DRAFT", "REJECTED").contains(request.getApprovalStatus()))
            throw new BusinessException("REQUEST_IN_APPROVAL", "采购申请审批中或已审批，不可编辑明细");

        projectAccessChecker.checkAccess(request.getProjectId(), "编辑采购申请明细");

        // Delete old items (tenant isolation)
        LambdaQueryWrapper<MatPurchaseRequestItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MatPurchaseRequestItem::getRequestId, requestId)
                .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId());
        requestItemMapper.delete(deleteWrapper);

        Long tenantId = UserContext.getCurrentTenantId();
        for (MatPurchaseRequestItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new BusinessException("QUANTITY_INVALID", "物料数量必须大于 0");
            }
            item.setId(IdWorker.getId());
            item.setRequestId(requestId);
            item.setTenantId(tenantId);
            // Auto-create material if name provided but no existing materialId
            resolveMaterialId(item, tenantId);
        }
        for (Long materialId : items.stream()
                .map(MatPurchaseRequestItem::getMaterialId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList()) {
            requireEnabledMaterialForUpdate(materialId, tenantId);
        }
        for (MatPurchaseRequestItem item : items) {
            validatePlanningReferences(item, request.getProjectId(), tenantId);
            item.setBudgetLineId(null);
            item.setEstimatedUnitPrice(null);
            item.setEstimatedAmount(null);
            MdMaterial material = mdMaterialMapper.selectById(item.getMaterialId());
            item.setMaterialName(material.getMaterialName());
            item.setSpecification(material.getSpecification());
            item.setCreatedBy(UserContext.getCurrentUserId());
            item.setUpdatedBy(UserContext.getCurrentUserId());
        }
        for (MatPurchaseRequestItem item : items) {
            requestItemMapper.insert(item);
        }
        if ("REJECTED".equals(request.getApprovalStatus())) {
            requestMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequest>()
                    .eq(MatPurchaseRequest::getId, requestId)
                    .set(MatPurchaseRequest::getApprovalStatus, "DRAFT")
                    .set(MatPurchaseRequest::getStatus, "DRAFT"));
        }
    }

    private void validateRequestItemForSubmission(MatPurchaseRequest request, MatPurchaseRequestItem item) {
        if (item.getMaterialId() == null || item.getQuantity() == null || item.getQuantity().signum() <= 0) {
            throw new BusinessException("PURCHASE_REQUEST_ITEM_INCOMPLETE", "采购申请明细必须填写物料和有效数量");
        }
        if (item.getPlannedDate() == null) {
            throw new BusinessException("PURCHASE_REQUEST_PLANNED_DATE_REQUIRED", "采购申请明细必须填写计划到货日期");
        }
        if (!StringUtils.hasText(item.getUseLocation())) {
            throw new BusinessException("PURCHASE_REQUEST_USE_LOCATION_REQUIRED", "采购申请每条明细必须填写使用部位");
        }
        integrityService.validateSubTask(request.getProjectId(), item.getSubTaskId());
    }

    /**
     * 自定义物料：name + unit -> 自动查找或创建 MdMaterial
     */
    private void resolveMaterialId(MatPurchaseRequestItem item, Long tenantId) {
        if (item.getMaterialId() != null) {
            return;
        }
        if (item.getMaterialName() == null || item.getMaterialName().isBlank()) return;

        MdMaterial existing = mdMaterialMapper.selectOne(
                new LambdaQueryWrapper<MdMaterial>()
                        .eq(MdMaterial::getMaterialName, item.getMaterialName().trim())
                        .eq(MdMaterial::getTenantId, tenantId));
        if (existing != null) {
            item.setMaterialId(existing.getId());
            if (item.getUnit() == null || item.getUnit().isBlank()) {
                item.setUnit(existing.getUnit());
            }
            return;
        }

        MdMaterial material = new MdMaterial();
        material.setTenantId(tenantId);
        material.setMaterialName(item.getMaterialName().trim());
        material.setMaterialCode("CUSTOM-" + System.currentTimeMillis());
        material.setUnit(item.getUnit());
        material.setStatus("ENABLE");
        mdMaterialMapper.insert(material);
        item.setMaterialId(material.getId());
    }

    private MdMaterial requireEnabledMaterialForUpdate(Long materialId, Long tenantId) {
        MdMaterial material = mdMaterialMapper.selectByIdForUpdate(materialId, tenantId);
        if (material == null) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "物料不存在");
        }
        if (!"ENABLE".equals(material.getStatus())) {
            throw new BusinessException("MATERIAL_DISABLED", "物料已停用");
        }
        return material;
    }

    private void validatePlanningReferences(MatPurchaseRequestItem item, Long projectId, Long tenantId) {
        if (item.getWbsTaskId() != null) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM project_wbs_task
                    WHERE id=? AND tenant_id=? AND project_id=? AND deleted_flag=0
                    """, Integer.class, item.getWbsTaskId(), tenantId, projectId);
            if (count == null || count != 1) {
                throw new BusinessException("PURCHASE_WBS_MISMATCH", "WBS任务不存在或不属于当前项目");
            }
        }
    }

    private void validateProjectRequired(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_REQUIRED", "项目不能为空");
        }
    }

    // ================================================================
    // VO 转换
    // ================================================================

    private MatPurchaseRequestVO toVO(MatPurchaseRequest r, BigDecimal totalAmount) {
        MatPurchaseRequestVO vo = buildBaseVO(r);
        vo.setTotalAmount(totalAmount.toPlainString());
        if (r.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(r.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (r.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(r.getContractId());
            if (contract != null) vo.setContractName(contract.getContractName());
        }
        return vo;
    }

    private MatPurchaseRequestVO toVO(MatPurchaseRequest r, Map<Long, String> projectNames,
                                      Map<Long, String> contractNames, BigDecimal totalAmount) {
        MatPurchaseRequestVO vo = buildBaseVO(r);
        vo.setTotalAmount(totalAmount.toPlainString());
        if (r.getProjectId() != null) vo.setProjectName(projectNames.get(r.getProjectId()));
        if (r.getContractId() != null) vo.setContractName(contractNames.get(r.getContractId()));
        return vo;
    }

    private Map<Long, BigDecimal> requestTotals(Long tenantId, Set<Long> requestIds) {
        if (requestIds.isEmpty()) return Map.of();
        return requestItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getTenantId, tenantId)
                        .in(MatPurchaseRequestItem::getRequestId, requestIds))
                .stream()
                .collect(Collectors.groupingBy(
                        MatPurchaseRequestItem::getRequestId,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                item -> item.getEstimatedAmount() == null
                                        ? BigDecimal.ZERO : item.getEstimatedAmount(),
                                BigDecimal::add)));
    }

    private MatPurchaseRequestVO buildBaseVO(MatPurchaseRequest r) {
        MatPurchaseRequestVO vo = new MatPurchaseRequestVO();
        vo.setId(String.valueOf(r.getId()));
        vo.setTenantId(String.valueOf(r.getTenantId()));
        vo.setProjectId(r.getProjectId() != null ? String.valueOf(r.getProjectId()) : null);
        vo.setContractId(r.getContractId() != null ? String.valueOf(r.getContractId()) : null);
        vo.setPurpose(r.getPurpose());
        vo.setRequestCode(r.getRequestCode());
        vo.setApprovalStatus(r.getApprovalStatus());
        vo.setStatus(r.getStatus());
        vo.setCreatedBy(String.valueOf(r.getCreatedBy()));
        vo.setCreatedTime(r.getCreatedTime() != null ? r.getCreatedTime().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedTime(r.getUpdatedTime() != null ? r.getUpdatedTime().format(DateTimeUtils.DTF) : null);
        vo.setRemark(r.getRemark());
        return vo;
    }

    private MatPurchaseRequestItemVO toItemVO(MatPurchaseRequestItem item, Map<Long, String> materialNames) {
        MatPurchaseRequestItemVO vo = new MatPurchaseRequestItemVO();
        vo.setId(String.valueOf(item.getId()));
        vo.setTenantId(String.valueOf(item.getTenantId()));
        vo.setRequestId(String.valueOf(item.getRequestId()));
        vo.setMaterialId(item.getMaterialId() != null ? String.valueOf(item.getMaterialId()) : null);
        vo.setWbsTaskId(item.getWbsTaskId() != null ? String.valueOf(item.getWbsTaskId()) : null);
        vo.setBudgetLineId(item.getBudgetLineId() != null ? String.valueOf(item.getBudgetLineId()) : null);
        vo.setMaterialName(item.getMaterialId() != null ? materialNames.get(item.getMaterialId()) : item.getMaterialName());
        vo.setBudgetLineId(item.getBudgetLineId() != null ? String.valueOf(item.getBudgetLineId()) : null);
        vo.setSubTaskId(item.getSubTaskId() != null ? String.valueOf(item.getSubTaskId()) : null);
        vo.setQuantity(String.valueOf(item.getQuantity()));
        vo.setApprovedQuantity(item.getApprovedQuantity() != null ? item.getApprovedQuantity().toPlainString() : null);
        vo.setApprovalVersion(item.getApprovalVersion());
        vo.setSpecification(item.getSpecification());
        vo.setUseLocation(item.getUseLocation());
        vo.setEstimatedUnitPrice(item.getEstimatedUnitPrice() != null ? item.getEstimatedUnitPrice().toPlainString() : null);
        vo.setEstimatedAmount(item.getEstimatedAmount() != null ? item.getEstimatedAmount().toPlainString() : null);
        vo.setUnit(item.getUnit());
        vo.setPlannedDate(item.getPlannedDate() != null ? item.getPlannedDate().toString() : null);
        vo.setCreatedBy(item.getCreatedBy() != null ? String.valueOf(item.getCreatedBy()) : null);
        vo.setCreatedTime(item.getCreatedTime() != null ? item.getCreatedTime().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedTime(item.getUpdatedTime() != null ? item.getUpdatedTime().format(DateTimeUtils.DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }
}
