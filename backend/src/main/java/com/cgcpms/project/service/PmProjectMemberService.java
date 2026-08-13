package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.dto.CreateProjectMemberRequest;
import com.cgcpms.project.dto.UpdateProjectMemberRequest;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.project.vo.PmProjectMemberVO;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.role.SystemRoleContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmProjectMemberService {

    private final PmProjectMemberMapper memberMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final SysUserMapper sysUserMapper;

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
        wrapper.orderByDesc(PmProjectMember::getCreatedAt);

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
    public Long create(Long projectId, CreateProjectMemberRequest request) {
        verifyProjectOwnership(projectId);
        validateRoleCode(request.roleCode(), null);
        validateTargetUser(request.userId());

        PmProjectMember member = new PmProjectMember();
        member.setTenantId(UserContext.getCurrentTenantId());
        member.setProjectId(projectId);
        member.setUserId(request.userId());
        member.setRoleCode(request.roleCode());
        member.setPositionName(request.positionName());
        member.setStartDate(request.startDate());
        member.setEndDate(request.endDate());
        member.setStatus(request.status() == null ? "ACTIVE" : request.status());
        member.setRemark(request.remark());

        Long existingId = memberMapper.selectIdIncludingDeleted(
                UserContext.getCurrentTenantId(), projectId, member.getUserId());
        if (existingId != null) {
            int restored = memberMapper.restoreDeleted(existingId, UserContext.getCurrentTenantId(), projectId,
                    member, UserContext.getCurrentUserId());
            if (restored == 0) {
                throw new BusinessException("MEMBER_ALREADY_EXISTS", "该用户已是本项目成员");
            }
            log.info("Restoring project member: userId={}, projectId={}", member.getUserId(), projectId);
            return existingId;
        }

        memberMapper.insert(member);
        log.info("Creating project member: userId={}, projectId={}", member.getUserId(), projectId);
        return member.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long projectId, Long id, UpdateProjectMemberRequest request) {
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
        if (!Objects.equals(request.userId(), existing.getUserId())) {
            throw new BusinessException("PROJECT_MEMBER_USER_IMMUTABLE", "项目成员用户不可修改");
        }
        validateRoleCode(request.roleCode(), existing.getRoleCode());

        PmProjectMember update = new PmProjectMember();
        update.setId(id);
        update.setRoleCode(request.roleCode());
        if (request.positionName() != null) update.setPositionName(request.positionName());
        if (request.startDate() != null) update.setStartDate(request.startDate());
        if (request.endDate() != null) update.setEndDate(request.endDate());
        if (request.status() != null) update.setStatus(request.status());
        if (request.remark() != null) update.setRemark(request.remark());
        memberMapper.updateById(update);
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

    private void validateRoleCode(String roleCode, String existingRoleCode) {
        if (roleCode != null && SystemRoleContract.PROJECT_SCOPED_ROLE_CODES.contains(roleCode)) return;
        if (roleCode != null && SystemRoleContract.LEGACY_PROJECT_ROLE_CODES.contains(roleCode)
                && Objects.equals(existingRoleCode, roleCode)) return;
        throw new BusinessException("PROJECT_MEMBER_ROLE_INVALID", "项目角色必须使用七类项目范围系统角色");
    }

    private void validateTargetUser(Long userId) {
        SysUser user = sysUserMapper.selectByTenantAndId(UserContext.getCurrentTenantId(), userId);
        if (user == null || !"ENABLE".equals(user.getStatus())) {
            throw new BusinessException("PROJECT_MEMBER_USER_INVALID", "项目成员用户不存在、已停用或不属于当前租户");
        }
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
        vo.setCreatedAt(m.getCreatedAt() != null ? DateTimeUtils.DTF.format(m.getCreatedAt()) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? DateTimeUtils.DTF.format(m.getUpdatedAt()) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }
}
