package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.vo.PmProjectVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cgcpms.auth.context.UserContext;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import com.cgcpms.common.util.DateTimeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmProjectService {

    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final CtContractItemMapper ctContractItemMapper;
    private final CtContractPaymentTermMapper ctContractPaymentTermMapper;
    private final SysFileMapper sysFileMapper;
    private final PayApplicationMapper payApplicationMapper;
    private final PayRecordMapper payRecordMapper;
    private final StlSettlementMapper stlSettlementMapper;
    private final WfInstanceMapper wfInstanceMapper;

    public IPage<PmProjectVO> getPage(long pageNo, long pageSize, String keyword, String projectCode, String projectName, String projectType, String status) {
        LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmProject::getTenantId, UserContext.getCurrentTenantId());
        // keyword 全局搜索：匹配项目编号、项目名称、项目类型、合同金额、项目地址等字段
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w ->
                w.like(PmProject::getProjectCode, keyword)
                    .or().like(PmProject::getProjectName, keyword)
                    .or().like(PmProject::getProjectType, keyword)
                    .or().like(PmProject::getContractAmount, keyword)
                    .or().like(PmProject::getProjectAddress, keyword)
                    .or().like(PmProject::getOwnerUnit, keyword)
                    .or().like(PmProject::getSupervisorUnit, keyword)
                    .or().like(PmProject::getDesignUnit, keyword)
            );
        }
        if (StringUtils.hasText(projectCode)) wrapper.like(PmProject::getProjectCode, projectCode);
        if (StringUtils.hasText(projectName)) wrapper.like(PmProject::getProjectName, projectName);
        if (StringUtils.hasText(projectType)) wrapper.eq(PmProject::getProjectType, projectType);
        if (StringUtils.hasText(status)) wrapper.eq(PmProject::getStatus, status);
        wrapper.orderByDesc(PmProject::getCreatedAt);

        Page<PmProject> page = pmProjectMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public PmProjectVO getById(Long id) {
        PmProject project = pmProjectMapper.selectById(id);
        if (project == null) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        if (!project.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        return toVO(project);
    }

    @Transactional
    public Long create(PmProject project) {
        log.info("Creating project: {}", project.getProjectName());

        // Auto-generate project code: XM-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String prefix = "XM-" + today + "-";

        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }

        LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmProject::getTenantId, tenantId)
                .likeRight(PmProject::getProjectCode, prefix)
                .orderByDesc(PmProject::getProjectCode);
        Page<PmProject> page = new Page<>(0, 1);
        Page<PmProject> result = pmProjectMapper.selectPage(page, wrapper);
        List<PmProject> list = result.getRecords();

        int seq = 1;
        if (!list.isEmpty()) {
            PmProject last = list.get(0);
            if (last.getProjectCode() != null
                    && last.getProjectCode().length() == prefix.length() + 3) {
                try {
                    seq = Integer.parseInt(last.getProjectCode().substring(prefix.length())) + 1;
                } catch (NumberFormatException ex) {
                    log.warn("Failed to parse sequence number: {}", last.getProjectCode(), ex);
                }
            }
        }
        project.setProjectCode(prefix + String.format("%03d", seq));
        project.setStatus("DRAFT");
        project.setTenantId(tenantId);

        pmProjectMapper.insert(project);
        return project.getId();
    }

    @Transactional
    public void update(PmProject project) {
        PmProject existing = pmProjectMapper.selectById(project.getId());
        if (existing == null)
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        pmProjectMapper.updateById(project);
    }

    /**
     * Archive project — set status=ARCHIVED after verifying no active dependencies.
     * Active contracts, payments, settlements, or running workflows block archiving.
     */
    @Transactional
    public void archive(Long id) {
        PmProject existing = pmProjectMapper.selectById(id);
        if (existing == null) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        if ("ARCHIVED".equals(existing.getStatus())) {
            throw new BusinessException("PROJECT_ALREADY_ARCHIVED", "项目已归档");
        }

        Long tenantId = UserContext.getCurrentTenantId();

        // Check for active contracts (not SETTLED/TERMINATED)
        long activeContracts = ctContractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getProjectId, id)
                .eq(CtContract::getTenantId, tenantId)
                .notIn(CtContract::getContractStatus, "SETTLED", "TERMINATED"));
        if (activeContracts > 0) {
            throw new BusinessException("PROJECT_HAS_ACTIVE_CONTRACTS",
                    "项目存在未完成的合同 (" + activeContracts + " 个)，无法归档");
        }

        // Check for pending/active payments
        long activePayments = payApplicationMapper.selectCount(new LambdaQueryWrapper<PayApplication>()
                .eq(PayApplication::getProjectId, id)
                .eq(PayApplication::getTenantId, tenantId)
                .notIn(PayApplication::getPayStatus, "PAID"));
        if (activePayments > 0) {
            throw new BusinessException("PROJECT_HAS_ACTIVE_PAYMENTS",
                    "项目存在未完成的付款申请 (" + activePayments + " 个)，无法归档");
        }

        // Check for unsettled settlements (not finalized)
        long activeSettlements = stlSettlementMapper.selectCount(new LambdaQueryWrapper<StlSettlement>()
                .eq(StlSettlement::getProjectId, id)
                .eq(StlSettlement::getTenantId, tenantId)
                .isNull(StlSettlement::getFinalizedAt));
        if (activeSettlements > 0) {
            throw new BusinessException("PROJECT_HAS_ACTIVE_SETTLEMENTS",
                    "项目存在未完成的结算 (" + activeSettlements + " 个)，无法归档");
        }

        // Check for running workflow instances
        long runningWorkflows = wfInstanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getProjectId, id)
                .eq(WfInstance::getTenantId, tenantId)
                .eq(WfInstance::getInstanceStatus, "RUNNING"));
        if (runningWorkflows > 0) {
            throw new BusinessException("PROJECT_HAS_RUNNING_WORKFLOWS",
                    "项目存在运行中的审批流程 (" + runningWorkflows + " 个)，无法归档");
        }

        existing.setStatus("ARCHIVED");
        pmProjectMapper.updateById(existing);
        log.info("Project {} archived successfully", id);
    }

    /**
     * Physical delete — SUPER_ADMIN only, and only for projects with zero dependencies.
     * For normal delete, use {@link #archive(Long)} instead.
     */
    @Transactional
    public void delete(Long id) {
        if (!UserContext.hasRole("SUPER_ADMIN")) {
            throw new BusinessException("DELETE_FORBIDDEN",
                    "物理删除仅限超级管理员。普通管理员请使用归档功能。");
        }

        PmProject existing = pmProjectMapper.selectById(id);
        if (existing == null) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }

        Long tenantId = UserContext.getCurrentTenantId();

        // SUPER_ADMIN can only physically delete empty projects (no contracts)
        long contractCount = ctContractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getProjectId, id)
                .eq(CtContract::getTenantId, tenantId));
        if (contractCount > 0) {
            throw new BusinessException("PROJECT_HAS_DEPENDENCIES",
                    "项目存在关联合同 (" + contractCount + " 个)，无法物理删除。请先归档。");
        }
        long paymentCount = payRecordMapper.selectCount(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getProjectId, id)
                .eq(PayRecord::getTenantId, tenantId));
        if (paymentCount > 0) {
            throw new BusinessException("PROJECT_HAS_DEPENDENCIES",
                    "项目存在付款记录 (" + paymentCount + " 条)，无法物理删除。请先归档。");
        }
        long workflowCount = wfInstanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getProjectId, id)
                .eq(WfInstance::getTenantId, tenantId));
        if (workflowCount > 0) {
            throw new BusinessException("PROJECT_HAS_DEPENDENCIES",
                    "项目存在审批流程 (" + workflowCount + " 条)，无法物理删除。请先归档。");
        }

        // Cascade: logical-delete associated files (tenant-scoped)
        sysFileMapper.delete(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBusinessType, "PROJECT")
                .eq(SysFile::getBusinessId, id)
                .eq(SysFile::getTenantId, tenantId));

        // Avoid unique key collision with previously deleted projects sharing the same code.
        // project_code 列长度限制为 50 字符，超出时截断以避免 DB 错误。
        String delCode = existing.getProjectCode() + "-DEL-" + id;
        if (delCode.length() > 50) {
            delCode = delCode.substring(0, 50);
        }
        existing.setProjectCode(delCode);
        pmProjectMapper.updateById(existing);

        pmProjectMapper.deleteById(id);
        log.info("Project {} physically deleted by SUPER_ADMIN", id);
    }

    private PmProjectVO toVO(PmProject p) {
        PmProjectVO vo = new PmProjectVO();
        vo.setId(p.getId() != null ? p.getId().toString() : null);
        vo.setTenantId(p.getTenantId() != null ? p.getTenantId().toString() : null);
        vo.setOrgId(p.getOrgId() != null ? p.getOrgId().toString() : null);
        vo.setProjectCode(p.getProjectCode());
        vo.setProjectName(p.getProjectName());
        vo.setProjectType(p.getProjectType());
        vo.setProjectAddress(p.getProjectAddress());
        vo.setOwnerUnit(p.getOwnerUnit());
        vo.setSupervisorUnit(p.getSupervisorUnit());
        vo.setDesignUnit(p.getDesignUnit());
        vo.setContractAmount(p.getContractAmount() != null ? p.getContractAmount().toPlainString() : null);
        vo.setTargetCost(p.getTargetCost() != null ? p.getTargetCost().toPlainString() : null);
        vo.setPlannedStartDate(p.getPlannedStartDate() != null ? p.getPlannedStartDate().toString() : null);
        vo.setPlannedEndDate(p.getPlannedEndDate() != null ? p.getPlannedEndDate().toString() : null);
        vo.setActualStartDate(p.getActualStartDate() != null ? p.getActualStartDate().toString() : null);
        vo.setActualEndDate(p.getActualEndDate() != null ? p.getActualEndDate().toString() : null);
        vo.setProjectManagerId(p.getProjectManagerId() != null ? p.getProjectManagerId().toString() : null);
        vo.setStatus(p.getStatus());
        vo.setApprovalStatus(p.getApprovalStatus());
        vo.setCreatedBy(p.getCreatedBy() != null ? p.getCreatedBy().toString() : null);
        vo.setCreatedAt(p.getCreatedAt() != null ? DateTimeUtils.DTF.format(p.getCreatedAt()) : null);
        vo.setUpdatedAt(p.getUpdatedAt() != null ? DateTimeUtils.DTF.format(p.getUpdatedAt()) : null);
        vo.setRemark(p.getRemark());
        return vo;
    }
}
