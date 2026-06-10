package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.vo.PmProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PmProjectService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PmProjectMapper pmProjectMapper;

    public IPage<PmProjectVO> getPage(long pageNo, long pageSize, String projectCode, String projectName, String projectType, String status) {
        LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
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
        return toVO(project);
    }

    @Transactional
    public Long create(PmProject project) {
        pmProjectMapper.insert(project);
        return project.getId();
    }

    @Transactional
    public void update(PmProject project) {
        if (pmProjectMapper.selectById(project.getId()) == null)
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        pmProjectMapper.updateById(project);
    }

    @Transactional
    public void delete(Long id) {
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
