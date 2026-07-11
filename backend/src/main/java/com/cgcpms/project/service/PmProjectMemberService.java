package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.project.vo.PmProjectMemberVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmProjectMemberService {

    private final PmProjectMemberMapper memberMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;

    /**
     * Verify the project exists and belongs to the current tenant.
     */
    private PmProject verifyProjectOwnership(Long projectId) {
        PmProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        if (!project.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        projectAccessChecker.checkAccess(projectId, "访问项目成员");
        return project;
    }

    public IPage<PmProjectMemberVO> getPage(Long projectId, long pageNo, long pageSize,
                                             String roleCode, String status) {
        verifyProjectOwnership(projectId);

        LambdaQueryWrapper<PmProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmProjectMember::getTenantId, UserContext.getCurrentTenantId())
               .eq(PmProjectMember::getProjectId, projectId);
        if (StringUtils.hasText(roleCode)) wrapper.eq(PmProjectMember::getRoleCode, roleCode);
        if (StringUtils.hasText(status)) wrapper.eq(PmProjectMember::getStatus, status);
        wrapper.orderByDesc(PmProjectMember::getCreatedTime);

        Page<PmProjectMember> page = memberMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public PmProjectMemberVO getById(Long projectId, Long id) {
        verifyProjectOwnership(projectId);

        PmProjectMember member = memberMapper.selectById(id);
        if (member == null) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        if (!member.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        if (!member.getProjectId().equals(projectId)) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        return toVO(member);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long projectId, PmProjectMember member) {
        verifyProjectOwnership(projectId);

        // Set tenant and project from context/path — ignore any client-supplied values
        member.setTenantId(UserContext.getCurrentTenantId());
        member.setProjectId(projectId);
        if (member.getStatus() == null) {
            member.setStatus("ACTIVE");
        }

        // Check duplicate: same project + same user
        Long exists = memberMapper.selectCount(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getTenantId, UserContext.getCurrentTenantId())
                .eq(PmProjectMember::getProjectId, projectId)
                .eq(PmProjectMember::getUserId, member.getUserId()));
        if (exists != null && exists > 0) {
            throw new BusinessException("MEMBER_ALREADY_EXISTS", "该用户已是本项目成员");
        }

        memberMapper.insert(member);
        log.info("Creating project member: userId={}, projectId={}", member.getUserId(), projectId);
        return member.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long projectId, Long id, PmProjectMember member) {
        verifyProjectOwnership(projectId);

        PmProjectMember existing = memberMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        if (!existing.getProjectId().equals(projectId)) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }

        // Preserve immutable fields
        member.setId(id);
        member.setTenantId(existing.getTenantId());
        member.setProjectId(existing.getProjectId());
        memberMapper.updateById(member);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long projectId, Long id) {
        verifyProjectOwnership(projectId);

        PmProjectMember existing = memberMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        if (!existing.getProjectId().equals(projectId)) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        memberMapper.deleteById(id);
    }

    private PmProjectMemberVO toVO(PmProjectMember m) {
        PmProjectMemberVO vo = new PmProjectMemberVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
        vo.setUserId(m.getUserId() != null ? m.getUserId().toString() : null);
        vo.setRoleCode(m.getRoleCode());
        vo.setPositionName(m.getPositionName());
        vo.setStartDate(m.getStartDate() != null ? m.getStartDate().toString() : null);
        vo.setEndDate(m.getEndDate() != null ? m.getEndDate().toString() : null);
        vo.setStatus(m.getStatus());
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedTime() != null ? DateTimeUtils.DTF.format(m.getCreatedTime()) : null);
        vo.setUpdatedAt(m.getUpdatedTime() != null ? DateTimeUtils.DTF.format(m.getUpdatedTime()) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }
}
