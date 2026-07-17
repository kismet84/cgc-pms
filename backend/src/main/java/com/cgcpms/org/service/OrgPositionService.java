package com.cgcpms.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.org.entity.OrgPosition;
import com.cgcpms.org.mapper.OrgCompanyMapper;
import com.cgcpms.org.mapper.OrgDepartmentMapper;
import com.cgcpms.org.mapper.OrgPositionMapper;
import com.cgcpms.org.vo.OrgPositionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

import java.util.List;
import java.util.LinkedHashSet;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

@Service
@RequiredArgsConstructor
public class OrgPositionService {

    private final OrgPositionMapper orgPositionMapper;
    private final OrgCompanyMapper orgCompanyMapper;
    private final OrgDepartmentMapper orgDepartmentMapper;
    private final JdbcTemplate jdbcTemplate;

    public IPage<OrgPositionVO> getPage(long pageNo, long pageSize, Long companyId, Long departmentId, String positionCode, String positionName, String status) {
        LambdaQueryWrapper<OrgPosition> wrapper = new LambdaQueryWrapper<>();
        if (companyId != null) wrapper.eq(OrgPosition::getCompanyId, companyId);
        if (departmentId != null) wrapper.eq(OrgPosition::getDepartmentId, departmentId);
        if (StringUtils.hasText(positionCode)) wrapper.like(OrgPosition::getPositionCode, positionCode);
        if (StringUtils.hasText(positionName)) wrapper.like(OrgPosition::getPositionName, positionName);
        if (StringUtils.hasText(status)) wrapper.eq(OrgPosition::getStatus, status);
        wrapper.eq(OrgPosition::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(OrgPosition::getCreatedTime);

        Page<OrgPosition> page = orgPositionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public OrgPositionVO getById(Long id) {
        OrgPosition position = orgPositionMapper.selectById(id);
        if (position == null) throw new BusinessException("ORG_POSITION_NOT_FOUND", "岗位不存在");
        if (!position.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_POSITION_NOT_FOUND", "岗位不存在");
        }
        return toVO(position);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(OrgPosition position) {
        if (position.getCompanyId() == null) {
            throw new BusinessException("ORG_POSITION_COMPANY_REQUIRED", "所属公司不能为空");
        }
        if (position.getDepartmentId() == null) {
            throw new BusinessException("ORG_POSITION_DEPT_REQUIRED", "所属部门不能为空");
        }
        // 强制绑定当前租户
        Long tenantId = UserContext.getCurrentTenantId();
        position.setTenantId(tenantId);

        // 校验公司属于当前租户
        if (orgCompanyMapper.selectOne(new LambdaQueryWrapper<com.cgcpms.org.entity.OrgCompany>()
                .eq(com.cgcpms.org.entity.OrgCompany::getId, position.getCompanyId())
                .eq(com.cgcpms.org.entity.OrgCompany::getTenantId, tenantId)) == null) {
            throw new BusinessException("ORG_COMPANY_NOT_FOUND", "所属公司不存在");
        }
        // 校验部门属于当前租户且属于所选公司
        if (orgDepartmentMapper.selectOne(new LambdaQueryWrapper<com.cgcpms.org.entity.OrgDepartment>()
                .eq(com.cgcpms.org.entity.OrgDepartment::getId, position.getDepartmentId())
                .eq(com.cgcpms.org.entity.OrgDepartment::getTenantId, tenantId)
                .eq(com.cgcpms.org.entity.OrgDepartment::getCompanyId, position.getCompanyId())) == null) {
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "所属部门不存在或不属于所选公司");
        }

        if (StringUtils.hasText(position.getPositionCode())) {
            Long count = orgPositionMapper.selectCount(new LambdaQueryWrapper<OrgPosition>()
                    .eq(OrgPosition::getPositionCode, position.getPositionCode())
                    .eq(OrgPosition::getTenantId, tenantId));
            if (count > 0) {
                throw new BusinessException("ORG_POSITION_CODE_EXISTS", "岗位编码已存在");
            }
        }
        if (position.getStatus() == null) position.setStatus("ENABLE");
        orgPositionMapper.insert(position);
        return position.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(OrgPosition position) {
        OrgPosition existing = orgPositionMapper.selectById(position.getId());
        if (existing == null)
            throw new BusinessException("ORG_POSITION_NOT_FOUND", "岗位不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_POSITION_NOT_FOUND", "岗位不存在");
        }
        if (position.getCompanyId() == null) {
            throw new BusinessException("ORG_POSITION_COMPANY_REQUIRED", "所属公司不能为空");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        // 校验公司属于当前租户
        if (orgCompanyMapper.selectOne(new LambdaQueryWrapper<com.cgcpms.org.entity.OrgCompany>()
                .eq(com.cgcpms.org.entity.OrgCompany::getId, position.getCompanyId())
                .eq(com.cgcpms.org.entity.OrgCompany::getTenantId, tenantId)) == null) {
            throw new BusinessException("ORG_COMPANY_NOT_FOUND", "所属公司不存在");
        }
        if (position.getDepartmentId() == null) {
            throw new BusinessException("ORG_POSITION_DEPT_REQUIRED", "所属部门不能为空");
        }
        // 校验部门属于当前租户且属于所选公司
        if (orgDepartmentMapper.selectOne(new LambdaQueryWrapper<com.cgcpms.org.entity.OrgDepartment>()
                .eq(com.cgcpms.org.entity.OrgDepartment::getId, position.getDepartmentId())
                .eq(com.cgcpms.org.entity.OrgDepartment::getTenantId, tenantId)
                .eq(com.cgcpms.org.entity.OrgDepartment::getCompanyId, position.getCompanyId())) == null) {
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "所属部门不存在或不属于所选公司");
        }
        // 强制保留原 tenantId
        position.setTenantId(existing.getTenantId());
        orgPositionMapper.updateById(position);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrgPosition existing = orgPositionMapper.selectById(id);
        if (existing == null)
            throw new BusinessException("ORG_POSITION_NOT_FOUND", "岗位不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_POSITION_NOT_FOUND", "岗位不存在");
        }
        orgPositionMapper.deleteById(id);
    }

    public List<Long> positionUsers(Long positionId) {
        getById(positionId);
        return jdbcTemplate.queryForList("""
                SELECT user_id FROM org_user_position
                WHERE tenant_id=? AND position_id=? AND status='ACTIVE'
                  AND (effective_from IS NULL OR effective_from<=CURRENT_DATE)
                  AND (effective_to IS NULL OR effective_to>=CURRENT_DATE)
                ORDER BY primary_flag DESC,user_id
                """, Long.class, UserContext.getCurrentTenantId(), positionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replacePositionUsers(Long positionId, List<Long> userIds) {
        getById(positionId);
        Long tenantId = UserContext.getCurrentTenantId();
        List<Long> normalized = new LinkedHashSet<>(userIds).stream().toList();
        if (!normalized.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(normalized.size(), "?"));
            java.util.ArrayList<Object> args = new java.util.ArrayList<>();
            args.add(tenantId); args.addAll(normalized);
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND status='ENABLE' AND deleted_flag=0 AND id IN(" + placeholders + ")",
                    Integer.class, args.toArray());
            if (count == null || count != normalized.size()) {
                throw new BusinessException("ORG_POSITION_USER_INVALID", "岗位用户不存在、已停用或不属于当前租户");
            }
        }
        jdbcTemplate.update("DELETE FROM org_user_position WHERE tenant_id=? AND position_id=?", tenantId, positionId);
        for (int index = 0; index < normalized.size(); index++) {
            jdbcTemplate.update("INSERT INTO org_user_position(id,tenant_id,user_id,position_id,primary_flag,status,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,'ACTIVE',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                    IdWorker.getId(), tenantId, normalized.get(index), positionId, index == 0 ? 1 : 0,
                    UserContext.getCurrentUserId(), UserContext.getCurrentUserId());
        }
    }

    private OrgPositionVO toVO(OrgPosition p) {
        OrgPositionVO vo = new OrgPositionVO();
        vo.setId(p.getId() == null ? null : String.valueOf(p.getId()));
        vo.setCompanyId(p.getCompanyId() != null ? String.valueOf(p.getCompanyId()) : null);
        vo.setDepartmentId(p.getDepartmentId() != null ? String.valueOf(p.getDepartmentId()) : null);
        vo.setPositionCode(p.getPositionCode());
        vo.setPositionName(p.getPositionName());
        vo.setStatus(p.getStatus());
        vo.setCreatedBy(p.getCreatedBy() != null ? String.valueOf(p.getCreatedBy()) : null);
        if (p.getCreatedTime() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(p.getCreatedTime()));
        if (p.getUpdatedTime() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(p.getUpdatedTime()));
        vo.setRemark(p.getRemark());
        return vo;
    }
}
