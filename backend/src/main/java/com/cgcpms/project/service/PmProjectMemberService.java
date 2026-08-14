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
import com.cgcpms.project.vo.PmProjectMemberOptionsVO;
import com.cgcpms.project.vo.PmProjectMemberRoleRowVO;
import com.cgcpms.project.vo.PmProjectMemberVO;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.role.SystemRoleContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmProjectMemberService {

    private static final List<String> PROJECT_ROLE_ORDER = List.of(
            SystemRoleContract.PROJECT_MANAGER,
            SystemRoleContract.PROJECT_ACCOUNTANT,
            SystemRoleContract.TECHNICAL_LEAD,
            SystemRoleContract.SAFETY_LEAD,
            SystemRoleContract.CONSTRUCTION_LEAD,
            SystemRoleContract.PROCUREMENT_LEAD,
            SystemRoleContract.EMPLOYEE);

    private final PmProjectMemberMapper memberMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

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
        if (page.getRecords().isEmpty()) return page.convert(member -> toVO(member, null, null));

        Long tenantId = UserContext.getCurrentTenantId();
        List<Long> userIds = page.getRecords().stream()
                .map(PmProjectMember::getUserId).filter(Objects::nonNull).distinct().toList();
        List<String> roleCodes = page.getRecords().stream()
                .map(PmProjectMember::getRoleCode).filter(StringUtils::hasText).distinct().toList();
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of() : sysUserMapper.selectList(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getTenantId, tenantId)
                                .in(SysUser::getId, userIds))
                .stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<String, SysRole> roles = roleCodes.isEmpty() ? Map.of() : sysRoleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>()
                                .eq(SysRole::getTenantId, tenantId)
                                .in(SysRole::getRoleCode, roleCodes))
                .stream().collect(Collectors.toMap(SysRole::getRoleCode, Function.identity()));
        return page.convert(member -> toVO(member, users.get(member.getUserId()), roles.get(member.getRoleCode())));
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
        SysUser user = sysUserMapper.selectByTenantAndId(UserContext.getCurrentTenantId(), member.getUserId());
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, UserContext.getCurrentTenantId())
                .eq(SysRole::getRoleCode, member.getRoleCode()));
        return toVO(member, user, role);
    }

    public PmProjectMemberOptionsVO getOptions(Long projectId) {
        return getOptions(projectId, "", null);
    }

    public PmProjectMemberOptionsVO getOptions(Long projectId, String keyword, Long includeUserId) {
        verifyProjectOwnership(projectId);
        Long tenantId = UserContext.getCurrentTenantId();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.length() > 100) {
            throw new BusinessException("PROJECT_MEMBER_SEARCH_INVALID", "候选成员搜索词不能超过100个字符");
        }
        if (includeUserId != null && memberMapper.selectCount(
                new LambdaQueryWrapper<PmProjectMember>()
                        .eq(PmProjectMember::getTenantId, tenantId)
                        .eq(PmProjectMember::getProjectId, projectId)
                        .eq(PmProjectMember::getUserId, includeUserId)) == 0) {
            throw new BusinessException("MEMBER_NOT_FOUND", "项目成员不存在");
        }
        List<PmProjectMemberOptionsVO.RoleOption> roles = sysRoleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>()
                                .eq(SysRole::getTenantId, tenantId)
                                .eq(SysRole::getStatus, "ENABLE")
                                .eq(SysRole::getDataScope, "PROJECT_MEMBER")
                                .in(SysRole::getRoleCode, PROJECT_ROLE_ORDER))
                .stream()
                .sorted(Comparator.comparingInt(role -> PROJECT_ROLE_ORDER.indexOf(role.getRoleCode())))
                .map(role -> new PmProjectMemberOptionsVO.RoleOption(role.getRoleCode(), role.getRoleName()))
                .toList();

        Map<Long, CandidateBuilder> candidates = new LinkedHashMap<>();
        Long includedCandidateUserId = includeUserId == null ? -1L : includeUserId;
        for (PmProjectMemberRoleRowVO row : memberMapper.selectEnabledProjectRoleRows(
                tenantId, projectId, normalizedKeyword, includedCandidateUserId)) {
            CandidateBuilder candidate = candidates.computeIfAbsent(row.getUserId(), ignored ->
                    new CandidateBuilder(row.getUserId(), row.getUsername(), row.getRealName()));
            candidate.roleCodes.add(row.getRoleCode());
        }
        boolean usersTruncated = candidates.size() > 100;
        List<PmProjectMemberOptionsVO.UserOption> users = candidates.values().stream()
                .limit(100)
                .map(CandidateBuilder::toVO)
                .toList();
        return new PmProjectMemberOptionsVO(roles, users, usersTruncated);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long projectId, CreateProjectMemberRequest request) {
        verifyProjectOwnership(projectId);
        validateRoleCode(request.roleCode(), null);
        validateTargetUser(request.userId());
        validateAssignableRole(request.userId(), request.roleCode());

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
        boolean roleChanged = !Objects.equals(request.roleCode(), existing.getRoleCode());
        boolean reactivating = "ACTIVE".equals(request.status()) && !"ACTIVE".equals(existing.getStatus());
        if (roleChanged || reactivating) {
            validateTargetUser(existing.getUserId());
            validateAssignableRole(existing.getUserId(), request.roleCode());
        }

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

    private void validateAssignableRole(Long userId, String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, UserContext.getCurrentTenantId())
                .eq(SysRole::getRoleCode, roleCode)
                .eq(SysRole::getStatus, "ENABLE")
                .eq(SysRole::getDataScope, "PROJECT_MEMBER"));
        if (role == null) {
            throw new BusinessException("PROJECT_MEMBER_ROLE_INVALID", "项目角色必须使用启用的项目范围系统角色");
        }
        if (!sysUserMapper.selectEnabledRoleCodesByTenantAndUserId(
                UserContext.getCurrentTenantId(), userId).contains(roleCode)) {
            throw new BusinessException("PROJECT_MEMBER_ROLE_MISMATCH", "项目角色必须与用户启用的系统角色一致");
        }
    }

    private PmProjectMemberVO toVO(PmProjectMember m, SysUser user, SysRole role) {
        PmProjectMemberVO vo = new PmProjectMemberVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
        vo.setUserId(m.getUserId() != null ? m.getUserId().toString() : null);
        vo.setUsername(user != null ? user.getUsername() : null);
        vo.setRealName(user != null ? user.getRealName() : null);
        vo.setRoleCode(m.getRoleCode());
        vo.setRoleName(role != null ? role.getRoleName() : null);
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

    private static final class CandidateBuilder {
        private final Long userId;
        private final String username;
        private final String realName;
        private final List<String> roleCodes = new ArrayList<>();

        private CandidateBuilder(Long userId, String username, String realName) {
            this.userId = userId;
            this.username = username;
            this.realName = realName;
        }

        private PmProjectMemberOptionsVO.UserOption toVO() {
            List<String> orderedRoles = roleCodes.stream()
                    .distinct()
                    .sorted(Comparator.comparingInt(PROJECT_ROLE_ORDER::indexOf))
                    .toList();
            return new PmProjectMemberOptionsVO.UserOption(
                    Objects.toString(userId), username, realName, orderedRoles);
        }
    }
}
