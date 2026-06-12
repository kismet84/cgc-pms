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
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatPurchaseRequestService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MatPurchaseRequestMapper requestMapper;
    private final MatPurchaseRequestItemMapper requestItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final WorkflowEngine workflowEngine;

    // ═══════════════════════════════════════════════════════════════
    // 分页查询
    // ═══════════════════════════════════════════════════════════════

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

        IPage<MatPurchaseRequestVO> voPage = page.convert(r -> toVO(r, projectNames));
        return PageResult.of(voPage);
    }

    // ═══════════════════════════════════════════════════════════════
    // 详情查询
    // ═══════════════════════════════════════════════════════════════

    public MatPurchaseRequestVO getById(Long id) {
        MatPurchaseRequest request = requestMapper.selectById(id);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        MatPurchaseRequestVO vo = toVO(request);

        // Load items
        LambdaQueryWrapper<MatPurchaseRequestItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(MatPurchaseRequestItem::getRequestId, id)
                .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatPurchaseRequestItem::getCreatedTime);
        List<MatPurchaseRequestItem> items = requestItemMapper.selectList(itemWrapper);

        // Resolve material names
        Set<Long> materialIds = items.stream().map(MatPurchaseRequestItem::getMaterialId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> materialNames = materialIds.isEmpty() ? Map.of()
                : mdMaterialMapper.selectBatchIds(materialIds).stream()
                        .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));

        vo.setItems(items.stream().map(i -> toItemVO(i, materialNames)).toList());
        return vo;
    }

    public List<MatPurchaseRequestItemVO> getItems(Long requestId) {
        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        LambdaQueryWrapper<MatPurchaseRequestItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatPurchaseRequestItem::getRequestId, requestId)
                .eq(MatPurchaseRequestItem::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MatPurchaseRequestItem::getCreatedTime);
        List<MatPurchaseRequestItem> items = requestItemMapper.selectList(wrapper);

        Set<Long> materialIds = items.stream().map(MatPurchaseRequestItem::getMaterialId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> materialNames = materialIds.isEmpty() ? Map.of()
                : mdMaterialMapper.selectBatchIds(materialIds).stream()
                        .collect(Collectors.toMap(MdMaterial::getId, MdMaterial::getMaterialName, (a, b) -> a));

        return items.stream().map(i -> toItemVO(i, materialNames)).toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // 创建采购申请
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public Long create(MatPurchaseRequest request) {
        // Auto-generate request code: PR-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "PR-" + today + "-";

        LambdaQueryWrapper<MatPurchaseRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(MatPurchaseRequest::getRequestCode, prefix)
                .eq(MatPurchaseRequest::getTenantId, UserContext.getCurrentTenantId())
                .orderByDesc(MatPurchaseRequest::getRequestCode)
                .last("LIMIT 1");
        MatPurchaseRequest last = requestMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getRequestCode() != null && last.getRequestCode().length() == prefix.length() + 3) {
            try {
                seq = Integer.parseInt(last.getRequestCode().substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
            }
        }
        request.setRequestCode(prefix + String.format("%03d", seq));
        request.setApprovalStatus("DRAFT");
        request.setStatus("DRAFT");
        request.setTenantId(UserContext.getCurrentTenantId());

        requestMapper.insert(request);
        return request.getId();
    }

    // ═══════════════════════════════════════════════════════════════
    // 更新采购申请
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void update(MatPurchaseRequest request) {
        MatPurchaseRequest existing = requestMapper.selectById(request.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        // Guard: cannot edit if approving or approved
        if ("APPROVED".equals(existing.getApprovalStatus()) || "APPROVING".equals(existing.getApprovalStatus()))
            throw new BusinessException("REQUEST_IN_APPROVAL", "采购申请审批中或已审批，不可编辑");

        // Prevent overwriting approval status via update
        request.setApprovalStatus(existing.getApprovalStatus());
        request.setStatus(existing.getStatus());

        requestMapper.updateById(request);
    }

    // ═══════════════════════════════════════════════════════════════
    // 提交审批
    // ═══════════════════════════════════════════════════════════════

    @Transactional
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

    // ═══════════════════════════════════════════════════════════════
    // 删除
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void delete(Long id) {
        MatPurchaseRequest request = requestMapper.selectById(id);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        if (!"DRAFT".equals(request.getApprovalStatus()))
            throw new BusinessException("REQUEST_IN_APPROVAL", "采购申请审批中或已审批，不可删除");

        // Soft delete
        LambdaUpdateWrapper<MatPurchaseRequest> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MatPurchaseRequest::getId, id)
                .set(MatPurchaseRequest::getDeletedFlag, 1);
        requestMapper.update(null, wrapper);
    }

    // ═══════════════════════════════════════════════════════════════
    // 批量保存明细
    // ═══════════════════════════════════════════════════════════════

    @Transactional
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
            item.setRequestId(requestId);
            item.setTenantId(tenantId);
            requestItemMapper.insert(item);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 转采购订单（手动触发，用于审批通过后未自动转换的场景）
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void convertToPurchaseOrder(Long requestId) {
        MatPurchaseRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PURCHASE_REQUEST_NOT_FOUND", "采购申请不存在");

        if (!"APPROVED".equals(request.getStatus()))
            throw new BusinessException("REQUEST_NOT_APPROVED", "采购申请未审批通过，无法转换");

        if ("CONVERTED".equals(request.getStatus()))
            throw new BusinessException("REQUEST_ALREADY_CONVERTED", "采购申请已转换，不可重复转换");

        // This method delegates to the handler's convert logic.
        // For manual trigger, we mark as CONVERTED directly and delegate to handler pattern.
        // Actually, the handler does the conversion. Here we just mark and let caller handle.
        throw new BusinessException("NOT_IMPLEMENTED", "手动转换请通过审批流程自动触发");
    }

    // ═══════════════════════════════════════════════════════════════
    // VO 转换
    // ═══════════════════════════════════════════════════════════════

    private MatPurchaseRequestVO toVO(MatPurchaseRequest r) {
        MatPurchaseRequestVO vo = buildBaseVO(r);
        if (r.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(r.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        return vo;
    }

    private MatPurchaseRequestVO toVO(MatPurchaseRequest r, Map<Long, String> projectNames) {
        MatPurchaseRequestVO vo = buildBaseVO(r);
        if (r.getProjectId() != null) vo.setProjectName(projectNames.get(r.getProjectId()));
        return vo;
    }

    private MatPurchaseRequestVO buildBaseVO(MatPurchaseRequest r) {
        MatPurchaseRequestVO vo = new MatPurchaseRequestVO();
        vo.setId(r.getId() != null ? r.getId().toString() : null);
        vo.setTenantId(r.getTenantId() != null ? r.getTenantId().toString() : null);
        vo.setProjectId(r.getProjectId() != null ? r.getProjectId().toString() : null);
        vo.setRequestCode(r.getRequestCode());
        vo.setApprovalStatus(r.getApprovalStatus());
        vo.setStatus(r.getStatus());
        vo.setCreatedBy(r.getCreatedBy() != null ? r.getCreatedBy().toString() : null);
        vo.setCreatedTime(r.getCreatedTime() != null ? DTF.format(r.getCreatedTime()) : null);
        vo.setUpdatedTime(r.getUpdatedTime() != null ? DTF.format(r.getUpdatedTime()) : null);
        vo.setRemark(r.getRemark());
        return vo;
    }

    private MatPurchaseRequestItemVO toItemVO(MatPurchaseRequestItem i, Map<Long, String> materialNames) {
        MatPurchaseRequestItemVO vo = new MatPurchaseRequestItemVO();
        vo.setId(i.getId() != null ? i.getId().toString() : null);
        vo.setTenantId(i.getTenantId() != null ? i.getTenantId().toString() : null);
        vo.setRequestId(i.getRequestId() != null ? i.getRequestId().toString() : null);
        vo.setMaterialId(i.getMaterialId() != null ? i.getMaterialId().toString() : null);
        vo.setMaterialName(i.getMaterialId() != null ? materialNames.get(i.getMaterialId()) : null);
        vo.setQuantity(i.getQuantity() != null ? i.getQuantity().toPlainString() : null);
        vo.setUnit(i.getUnit());
        vo.setPlannedDate(i.getPlannedDate() != null ? DATE_FMT.format(i.getPlannedDate()) : null);
        vo.setCreatedBy(i.getCreatedBy() != null ? i.getCreatedBy().toString() : null);
        vo.setCreatedTime(i.getCreatedTime() != null ? DTF.format(i.getCreatedTime()) : null);
        vo.setUpdatedTime(i.getUpdatedTime() != null ? DTF.format(i.getUpdatedTime()) : null);
        vo.setRemark(i.getRemark());
        return vo;
    }
}
