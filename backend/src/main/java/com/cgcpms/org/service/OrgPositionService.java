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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

@Service
@RequiredArgsConstructor
public class OrgPositionService {

    private final OrgPositionMapper orgPositionMapper;
    private final OrgCompanyMapper orgCompanyMapper;
    private final OrgDepartmentMapper orgDepartmentMapper;

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
