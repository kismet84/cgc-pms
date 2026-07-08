package com.cgcpms.variation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.mapper.VarOrderItemMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.variation.vo.VarOrderItemVO;
import com.cgcpms.variation.vo.VarOrderVO;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VarOrderService {

    private final VarOrderMapper varOrderMapper;
    private final VarOrderItemMapper varOrderItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;

    public IPage<VarOrderVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                      Long partnerId, String varType, String direction, String varCode) {
        LambdaQueryWrapper<VarOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VarOrder::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq(VarOrder::getProjectId, projectId);
        if (contractId != null) wrapper.eq(VarOrder::getContractId, contractId);
        if (partnerId != null) wrapper.eq(VarOrder::getPartnerId, partnerId);
        if (StringUtils.hasText(varType)) wrapper.eq(VarOrder::getVarType, varType);
        if (StringUtils.hasText(direction)) wrapper.eq(VarOrder::getDirection, direction);
        if (StringUtils.hasText(varCode)) wrapper.like(VarOrder::getVarCode, varCode);
        wrapper.orderByDesc(VarOrder::getCreatedAt);

        Page<VarOrder> page = varOrderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-prefetch related project/contract/partner names to avoid N+1
        List<VarOrder> records = page.getRecords();
        Set<Long> projectIds = records.stream()
                .map(VarOrder::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> contractIds = records.stream()
                .map(VarOrder::getContractId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream()
                .map(VarOrder::getPartnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectBatchIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectBatchIds(partnerIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return page.convert(t -> toVO(t, projectNames, contractNames, partnerNames));
    }

    public VarOrderVO getById(Long id) {
        VarOrder order = varOrderMapper.selectById(id);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(order.getProjectId(), "查看变更签证");

        VarOrderVO vo = toVO(order);

        // Load items
        List<VarOrderItem> items = varOrderItemMapper.selectList(
                new LambdaQueryWrapper<VarOrderItem>()
                        .eq(VarOrderItem::getVarOrderId, id));
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));

        return vo;
    }

    public List<VarOrderVO> toVOList(List<VarOrder> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        NameMaps nameMaps = resolveNameMaps(records);
        return records.stream()
                .map(record -> toVO(record, nameMaps.projectNames(), nameMaps.contractNames(), nameMaps.partnerNames()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(VarOrder order) {
        validateDraftOrder(order);
        validateProjectAndContract(order.getProjectId(), order.getContractId(), "创建变更签证");

        // Auto-generate var code: VO-yyyyMMdd-XXX（含软删除记录查询最大编号，避免 UK 冲突）
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "VO-" + today + "-";

        String lastCode = varOrderMapper.selectLastCodeByPrefix(prefix, UserContext.getCurrentTenantId());

        int seq = 1;
        if (lastCode != null && lastCode.startsWith(prefix)) {
            try {
                seq = Integer.parseInt(lastCode.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", lastCode, e);
            }
        }
        order.setVarCode(prefix + String.format("%03d", seq));

        // Default approval status
        if (order.getApprovalStatus() == null || order.getApprovalStatus().isBlank()) {
            order.setApprovalStatus("DRAFT");
        }

        // Default direction
        if (order.getDirection() == null || order.getDirection().isBlank()) {
            order.setDirection("COST");
        }

        // Default ownerConfirmFlag
        if (order.getOwnerConfirmFlag() == null) {
            order.setOwnerConfirmFlag(0);
        }

        // Default impactDays
        if (order.getImpactDays() == null) {
            order.setImpactDays(0);
        }

        order.setTenantId(UserContext.getCurrentTenantId());
        varOrderMapper.insert(order);
        return order.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(VarOrder order) {
        VarOrder existing = varOrderMapper.selectById(order.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(existing.getProjectId(), "编辑变更签证");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        validateDraftOrder(order);
        validateProjectAndContract(
                order.getProjectId() != null ? order.getProjectId() : existing.getProjectId(),
                order.getContractId() != null ? order.getContractId() : existing.getContractId(),
                "编辑变更签证");

        varOrderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long varOrderId, List<VarOrderItem> items) {
        // Verify order exists and belongs to tenant
        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(order.getProjectId(), "编辑变更签证明细");

        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可编辑");
        if (order.getCostGeneratedFlag() != null && order.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        List<VarOrderItem> validItems = normalizeDraftItems(items);

        // Delete old items
        varOrderItemMapper.delete(new LambdaQueryWrapper<VarOrderItem>()
                .eq(VarOrderItem::getVarOrderId, varOrderId));

        // Batch insert new items and calculate total amount
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (VarOrderItem item : validItems) {
            item.setVarOrderId(varOrderId);
            item.setTenantId(UserContext.getCurrentTenantId());
            item.setId(null);
            totalAmount = totalAmount.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
        }
        Db.saveBatch(validItems, 50);

        // Update header reported amount
        order.setReportedAmount(totalAmount);
        varOrderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        VarOrder existing = varOrderMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(existing.getProjectId(), "删除变更签证");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可删除");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可删除，请走冲销");

        varOrderMapper.deleteById(id);
    }

    /**
     * 提交签证变更审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long varOrderId) {
        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");
        checkProjectAccess(order.getProjectId(), "提交变更签证审批");

        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_ALREADY_SUBMITTED", "签证已提交审批，不可重复提交");

        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                "VAR_ORDER",
                varOrderId,
                order.getVarCode(),
                order.getReportedAmount(),
                order.getProjectId(),
                order.getContractId(),
                null, null, null);

        LambdaUpdateWrapper<VarOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(VarOrder::getId, varOrderId)
                .set(VarOrder::getApprovalStatus, "APPROVING");
        varOrderMapper.update(null, updateWrapper);
    }

    private void validateDraftOrder(VarOrder order) {
        if (order.getProjectId() == null) {
            throw new BusinessException("VAR_ORDER_PROJECT_REQUIRED", "请选择项目");
        }
        if (order.getContractId() == null) {
            throw new BusinessException("VAR_ORDER_CONTRACT_REQUIRED", "请选择合同");
        }
        if (!StringUtils.hasText(order.getVarType())) {
            throw new BusinessException("VAR_ORDER_TYPE_REQUIRED", "请选择变更类型");
        }
    }

    private void validateProjectAndContract(Long projectId, Long contractId, String action) {
        checkProjectAccess(projectId, action);
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        }
        if (!java.util.Objects.equals(contract.getProjectId(), projectId)) {
            throw new BusinessException("CONTRACT_PROJECT_MISMATCH", "合同不属于当前项目");
        }
    }

    private void checkProjectAccess(Long projectId, String action) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_REQUIRED", "变更签证缺少项目关系");
        }
        projectAccessChecker.checkAccess(projectId, action);
    }

    private List<VarOrderItem> normalizeDraftItems(List<VarOrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("VAR_ORDER_ITEMS_REQUIRED", "请至少保留一条有效明细");
        }
        List<VarOrderItem> validItems = new ArrayList<>();
        for (VarOrderItem item : items) {
            if (item == null) {
                continue;
            }
            BigDecimal quantity = item.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (!StringUtils.hasText(item.getItemName())) {
                throw new BusinessException("VAR_ORDER_ITEM_NAME_REQUIRED", "请填写明细名称");
            }
            if (item.getCostSubjectId() == null) {
                throw new BusinessException("VAR_ORDER_ITEM_COST_SUBJECT_REQUIRED", "请选择成本科目");
            }
            validItems.add(item);
        }
        if (validItems.isEmpty()) {
            throw new BusinessException("VAR_ORDER_ITEMS_REQUIRED", "请至少保留一条有效明细");
        }
        return validItems;
    }

    // ---- VO conversion helpers ----

    private VarOrderVO toVO(VarOrder m) {
        VarOrderVO vo = buildBaseVO(m);
        if (m.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(m.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (m.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(m.getContractId());
            if (contract != null) vo.setContractName(contract.getContractName());
        }
        if (m.getPartnerId() != null) {
            MdPartner partner = mdPartnerMapper.selectById(m.getPartnerId());
            if (partner != null) vo.setPartnerName(partner.getPartnerName());
        }
        return vo;
    }

    /**
     * Null-safe map lookup that guards against null keys and Map.of().
     */
    private String safeGet(Map<Long, String> names, Long id) {
        if (id == null) return null;
        return names.get(id);
    }

    private NameMaps resolveNameMaps(List<VarOrder> records) {
        Set<Long> projectIds = records.stream()
                .map(VarOrder::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> contractIds = records.stream()
                .map(VarOrder::getContractId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream()
                .map(VarOrder::getPartnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectBatchIds(contractIds).stream()
                .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectBatchIds(partnerIds).stream()
                .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
        return new NameMaps(projectNames, contractNames, partnerNames);
    }

    private VarOrderVO toVO(VarOrder m, Map<Long, String> projectNames,
                              Map<Long, String> contractNames, Map<Long, String> partnerNames) {
        VarOrderVO vo = buildBaseVO(m);
        vo.setProjectName(safeGet(projectNames, m.getProjectId()));
        vo.setContractName(safeGet(contractNames, m.getContractId()));
        vo.setPartnerName(safeGet(partnerNames, m.getPartnerId()));
        return vo;
    }

    private VarOrderVO buildBaseVO(VarOrder m) {
        VarOrderVO vo = new VarOrderVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
        vo.setContractId(m.getContractId() != null ? m.getContractId().toString() : null);
        vo.setPartnerId(m.getPartnerId() != null ? m.getPartnerId().toString() : null);
        vo.setVarCode(m.getVarCode());
        vo.setVarName(m.getVarName());
        vo.setVarType(m.getVarType());
        vo.setDirection(m.getDirection());
        vo.setReportedAmount(m.getReportedAmount() != null ? m.getReportedAmount().toPlainString() : null);
        vo.setApprovedAmount(m.getApprovedAmount() != null ? m.getApprovedAmount().toPlainString() : null);
        vo.setConfirmedAmount(m.getConfirmedAmount() != null ? m.getConfirmedAmount().toPlainString() : null);
        vo.setOwnerConfirmFlag(m.getOwnerConfirmFlag());
        vo.setImpactDays(m.getImpactDays());
        vo.setApprovalStatus(m.getApprovalStatus());
        vo.setCostGeneratedFlag(m.getCostGeneratedFlag());
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }

    private VarOrderItemVO toItemVO(VarOrderItem item) {
        VarOrderItemVO vo = new VarOrderItemVO();
        vo.setId(item.getId() != null ? item.getId().toString() : null);
        vo.setTenantId(item.getTenantId() != null ? item.getTenantId().toString() : null);
        vo.setVarOrderId(item.getVarOrderId() != null ? item.getVarOrderId().toString() : null);
        vo.setItemName(item.getItemName());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity() != null ? item.getQuantity().toPlainString() : null);
        vo.setUnitPrice(item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : null);
        vo.setAmount(item.getAmount() != null ? item.getAmount().toPlainString() : null);
        vo.setCostSubjectId(item.getCostSubjectId() != null ? item.getCostSubjectId().toString() : null);
        vo.setCreatedBy(item.getCreatedBy() != null ? item.getCreatedBy().toString() : null);
        vo.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }

    private record NameMaps(
            Map<Long, String> projectNames,
            Map<Long, String> contractNames,
            Map<Long, String> partnerNames) {
    }
}
