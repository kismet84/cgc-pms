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
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.vo.PmProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cgcpms.auth.context.UserContext;
import org.springframework.util.StringUtils;

import java.util.List;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PmProjectService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final CtContractItemMapper ctContractItemMapper;
    private final CtContractPaymentTermMapper ctContractPaymentTermMapper;
    private final SysFileMapper sysFileMapper;

    public IPage<PmProjectVO> getPage(long pageNo, long pageSize, String projectCode, String projectName, String projectType, String status) {
        LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmProject::getTenantId, UserContext.getCurrentTenantId());
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

    @Transactional
    public void delete(Long id) {
        PmProject existing = pmProjectMapper.selectById(id);
        if (existing == null) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }

        // Cascade: logical-delete associated files
        sysFileMapper.delete(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBusinessType, "PROJECT")
                .eq(SysFile::getBusinessId, id));

        // Cascade: logical-delete contracts and their children
        List<CtContract> contracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>().eq(CtContract::getProjectId, id));
        for (CtContract c : contracts) {
            ctContractItemMapper.delete(new LambdaQueryWrapper<CtContractItem>()
                    .eq(CtContractItem::getContractId, c.getId()));
            ctContractPaymentTermMapper.delete(new LambdaQueryWrapper<CtContractPaymentTerm>()
                    .eq(CtContractPaymentTerm::getContractId, c.getId()));
            sysFileMapper.delete(new LambdaQueryWrapper<SysFile>()
                    .eq(SysFile::getBusinessType, "CONTRACT")
                    .eq(SysFile::getBusinessId, c.getId()));
        }
        if (!contracts.isEmpty()) {
            ctContractMapper.delete(new LambdaQueryWrapper<CtContract>()
                    .eq(CtContract::getProjectId, id));
        }

        pmProjectMapper.deleteById(id);
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
        vo.setCreatedAt(p.getCreatedAt() != null ? DTF.format(p.getCreatedAt()) : null);
        vo.setUpdatedAt(p.getUpdatedAt() != null ? DTF.format(p.getUpdatedAt()) : null);
        vo.setRemark(p.getRemark());
        return vo;
    }
}
