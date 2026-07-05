package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.purchase.vo.MatPurchaseRequestItemVO;
import com.cgcpms.purchase.vo.MatPurchaseRequestVO;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    // ================================================================
    // 分页查询
    // ================================================================

    public PageResult<MatPurchaseRequestVO> getPage(long pageNum, long pageSize, Long projectId,
                                                String approvalStatus, String status, String requestCode) {
        LambdaQueryWrapper<MatPurchaseRequest> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) wrapper.eq(MatPurchaseRequest::getProjectId, projectId);
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
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));

        // Batch-prefetch contract names to avoid N+1
        Set<Long> contractIds = records.stream().map(MatPurchaseRequest::getContractId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectBatchIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));

        IPage<MatPurchaseRequestVO> voPage = page.convert(r -> toVO(r, projectNames, contractNames));
        return PageResult.of(voPage);
    }

    // ================================================================
    // 查询详情
    // ================================================================

    public MatPurchaseRequestVO getById(Long id) {
        MatPurchaseRequest r = requestMapper.selectById(id);
        if (r == null || !r.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");
        return toVO(r);
    }

    // ================================================================
    // 查询明细
    // ================================================================

    public List<MatPurchaseRequestItemVO> getItems(Long requestId) {
        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        List<MatPurchaseRequestItem> items = requestItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getRequestId, requestId)
                        .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId()));
        Set<Long> materialIds = items.stream().map(MatPurchaseRequestItem::getMaterialId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> materialNames = materialIds.isEmpty() ? Map.of()
                : mdMaterialMapper.selectBatchIds(materialIds).stream()
                        .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));
        return items.stream().map(item -> toItemVO(item, materialNames)).collect(Collectors.toList());
    }

    // ================================================================
    // 创建
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long create(MatPurchaseRequest request) {
        // Auto-generate request code: PR-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "PR-" + today + "-";

        LambdaQueryWrapper<MatPurchaseRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(MatPurchaseRequest::getRequestCode, prefix)
                .eq(MatPurchaseRequest::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(MatPurchaseRequest::getRequestCode);
        Page<MatPurchaseRequest> page = new Page<>(0, 1);
        Page<MatPurchaseRequest> result = requestMapper.selectPage(page, wrapper);
        MatPurchaseRequest last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1;
        if (last != null && last.getRequestCode() != null && last.getRequestCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getRequestCode().substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getRequestCode(), e);
            }
        }
        request.setRequestCode(prefix + String.format("%03d", seq));
        request.setApprovalStatus("DRAFT");
        request.setStatus("DRAFT");
        request.setTenantId(UserContext.getCurrentTenantId());

        requestMapper.insert(request);
        return request.getId();
    }

    // ================================================================
    // 更新
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void update(MatPurchaseRequest request) {
        MatPurchaseRequest existing = requestMapper.selectById(request.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        // Only DRAFT can be updated
        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("REQUEST_IN_APPROVAL", "采购申请审批中或已审批，不可编辑");

        // Prevent overwriting approval status via update
        request.setApprovalStatus(existing.getApprovalStatus());
        request.setStatus(existing.getStatus());

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

        // Check items exist
        Long itemCount = requestItemMapper.selectCount(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getRequestId, requestId)
                        .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId()));
        if (itemCount == 0)
            throw new BusinessException("PURCHASE_REQUEST_NO_ITEMS", "采购申请没有明细，无法提交审批");

        // 更新审批状态为审批中
        LambdaUpdateWrapper<MatPurchaseRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MatPurchaseRequest::getId, requestId)
                .set(MatPurchaseRequest::getApprovalStatus, "APPROVING");
        requestMapper.update(null, updateWrapper);

        // 调用审批引擎
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                "PURCHASE_REQUEST",
                requestId,
                request.getRequestCode(),
                null,
                request.getProjectId(),
                null,
                null, null, null);
    }

    // ================================================================
    // 删除
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MatPurchaseRequest existing = requestMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("REQUEST_IN_APPROVAL", "采购申请审批中或已审批，不可删除");

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

        if (!"DRAFT".equals(request.getApprovalStatus()))
            throw new BusinessException("REQUEST_IN_APPROVAL", "采购申请审批中或已审批，不可编辑明细");

        // Delete old items (tenant isolation)
        LambdaQueryWrapper<MatPurchaseRequestItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MatPurchaseRequestItem::getRequestId, requestId)
                .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId());
        requestItemMapper.delete(deleteWrapper);

        // Insert new items
        Long tenantId = UserContext.getCurrentTenantId();
        for (MatPurchaseRequestItem item : items) {
            item.setId(null);
            item.setRequestId(requestId);
            item.setTenantId(tenantId);
            // Auto-create material if name provided but no existing materialId
            resolveMaterial(item, tenantId);
            requestItemMapper.insert(item);
        }
    }

    /**
     * 自定义物料：name + unit -> 自动查找或创建 MdMaterial
     */
    private void resolveMaterial(MatPurchaseRequestItem item, Long tenantId) {
        if (item.getMaterialId() != null) return;
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

    // ================================================================
    // 转采购订单（手动触发）
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public void convertToPurchaseOrder(Long requestId) {
        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        if (!"APPROVED".equals(request.getStatus()))
            throw new BusinessException("REQUEST_NOT_APPROVED", "采购申请未审批通过，无法转换");

        if ("CONVERTED".equals(request.getStatus()))
            throw new BusinessException("REQUEST_ALREADY_CONVERTED", "采购申请已转换，不可重复转换");

        throw new BusinessException("NOT_IMPLEMENTED", "手动转换请通过审批流程自动触发");
    }

    // ================================================================
    // VO 转换
    // ================================================================

    private MatPurchaseRequestVO toVO(MatPurchaseRequest r) {
        MatPurchaseRequestVO vo = buildBaseVO(r);
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

    private MatPurchaseRequestVO toVO(MatPurchaseRequest r, Map<Long, String> projectNames, Map<Long, String> contractNames) {
        MatPurchaseRequestVO vo = buildBaseVO(r);
        if (r.getProjectId() != null) vo.setProjectName(projectNames.get(r.getProjectId()));
        if (r.getContractId() != null) vo.setContractName(contractNames.get(r.getContractId()));
        return vo;
    }

    private MatPurchaseRequestVO buildBaseVO(MatPurchaseRequest r) {
        MatPurchaseRequestVO vo = new MatPurchaseRequestVO();
        vo.setId(String.valueOf(r.getId()));
        vo.setTenantId(String.valueOf(r.getTenantId()));
        vo.setProjectId(r.getProjectId() != null ? String.valueOf(r.getProjectId()) : null);
        vo.setContractId(r.getContractId() != null ? String.valueOf(r.getContractId()) : null);
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
        vo.setMaterialName(item.getMaterialId() != null ? materialNames.get(item.getMaterialId()) : item.getMaterialName());
        vo.setQuantity(String.valueOf(item.getQuantity()));
        vo.setUnit(item.getUnit());
        vo.setPlannedDate(item.getPlannedDate() != null ? item.getPlannedDate().toString() : null);
        vo.setCreatedBy(item.getCreatedBy() != null ? String.valueOf(item.getCreatedBy()) : null);
        vo.setCreatedTime(item.getCreatedTime() != null ? item.getCreatedTime().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedTime(item.getUpdatedTime() != null ? item.getUpdatedTime().format(DateTimeUtils.DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }
}
