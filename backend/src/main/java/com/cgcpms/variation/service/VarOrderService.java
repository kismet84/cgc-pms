package com.cgcpms.variation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VarOrderService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VarOrderMapper varOrderMapper;
    private final VarOrderItemMapper varOrderItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final WorkflowEngine workflowEngine;

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

        VarOrderVO vo = toVO(order);

        // Load items
        List<VarOrderItem> items = varOrderItemMapper.selectList(
                new LambdaQueryWrapper<VarOrderItem>()
                        .eq(VarOrderItem::getVarOrderId, id));
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));

        return vo;
    }

    @Transactional
    public Long create(VarOrder order) {
        // Auto-generate var code: VO-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "VO-" + today + "-";

        LambdaQueryWrapper<VarOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(VarOrder::getVarCode, prefix)
                .orderByDesc(VarOrder::getVarCode)
                .last("LIMIT 1");
        VarOrder last = varOrderMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getVarCode() != null && last.getVarCode().startsWith(prefix)) {
            try {
                seq = Integer.parseInt(last.getVarCode().substring(last.getVarCode().lastIndexOf('-') + 1)) + 1;
            } catch (NumberFormatException ignored) {
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

    @Transactional
    public void update(VarOrder order) {
        VarOrder existing = varOrderMapper.selectById(order.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        varOrderMapper.updateById(order);
    }

    @Transactional
    public void saveItems(Long varOrderId, List<VarOrderItem> items) {
        // Verify order exists and belongs to tenant
        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");

        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可编辑");
        if (order.getCostGeneratedFlag() != null && order.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        // Delete old items
        varOrderItemMapper.delete(new LambdaQueryWrapper<VarOrderItem>()
                .eq(VarOrderItem::getVarOrderId, varOrderId));

        // Batch insert new items and calculate total amount
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (VarOrderItem item : items) {
                item.setVarOrderId(varOrderId);
                item.setTenantId(UserContext.getCurrentTenantId());
                item.setId(null);
                varOrderItemMapper.insert(item);
                totalAmount = totalAmount.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
            }
        }

        // Update header reported amount
        order.setReportedAmount(totalAmount);
        varOrderMapper.updateById(order);
    }

    @Transactional
    public void delete(Long id) {
        VarOrder existing = varOrderMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_IN_APPROVAL", "签证变更审批中或已审批，不可删除");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可删除，请走冲销");

        varOrderMapper.deleteById(id);
    }

    /**
     * 提交签证变更审批。
     */
    @Transactional
    public void submitForApproval(Long varOrderId) {
        VarOrder order = varOrderMapper.selectById(varOrderId);
        if (order == null || !order.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("VAR_ORDER_NOT_FOUND", "变更签证不存在");

        if (!"DRAFT".equals(order.getApprovalStatus()))
            throw new BusinessException("VAR_ORDER_ALREADY_SUBMITTED", "签证已提交审批，不可重复提交");

        LambdaUpdateWrapper<VarOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(VarOrder::getId, varOrderId)
                .set(VarOrder::getApprovalStatus, "APPROVING");
        varOrderMapper.update(null, updateWrapper);

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
                null, null);
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

    private VarOrderVO toVO(VarOrder m, Map<Long, String> projectNames,
                              Map<Long, String> contractNames, Map<Long, String> partnerNames) {
        VarOrderVO vo = buildBaseVO(m);
        if (m.getProjectId() != null) vo.setProjectName(projectNames.get(m.getProjectId()));
        if (m.getContractId() != null) vo.setContractName(contractNames.get(m.getContractId()));
        if (m.getPartnerId() != null) vo.setPartnerName(partnerNames.get(m.getPartnerId()));
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
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DTF) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().format(DTF) : null);
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
        vo.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(DTF) : null);
        vo.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }
}
