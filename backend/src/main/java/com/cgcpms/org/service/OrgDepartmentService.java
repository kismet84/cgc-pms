package com.cgcpms.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.org.entity.OrgDepartment;
import com.cgcpms.org.entity.OrgCompany;
import com.cgcpms.org.entity.OrgPosition;
import com.cgcpms.org.mapper.OrgCompanyMapper;
import com.cgcpms.org.mapper.OrgDepartmentMapper;
import com.cgcpms.org.mapper.OrgPositionMapper;
import com.cgcpms.org.vo.OrgDepartmentTreeNodeVO;
import com.cgcpms.org.vo.OrgDepartmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgDepartmentService {

    private final OrgDepartmentMapper orgDepartmentMapper;
    private final OrgCompanyMapper orgCompanyMapper;
    private final OrgPositionMapper orgPositionMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<OrgDepartmentTreeNodeVO> getTree() {
        return getTree(null);
    }

    public List<OrgDepartmentTreeNodeVO> getTree(Long companyId) {
        LambdaQueryWrapper<OrgDepartment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgDepartment::getTenantId, UserContext.getCurrentTenantId());
        if (companyId != null) {
            wrapper.eq(OrgDepartment::getCompanyId, companyId);
        }
        wrapper.orderByAsc(OrgDepartment::getOrderNum, OrgDepartment::getId);

        List<OrgDepartment> allDepts = orgDepartmentMapper.selectList(wrapper);

        // Group by parentId for efficient tree building
        Map<Long, List<OrgDepartment>> parentMap = allDepts.stream()
                .collect(Collectors.groupingBy(
                        dept -> dept.getParentId() != null ? dept.getParentId() : 0L));

        // Build tree starting from root nodes (parentId = null or 0)
        List<OrgDepartment> roots = parentMap.getOrDefault(0L, new ArrayList<>());
        return roots.stream()
                .map(root -> buildTreeNode(root, parentMap))
                .collect(Collectors.toList());
    }

    private OrgDepartmentTreeNodeVO buildTreeNode(OrgDepartment dept, Map<Long, List<OrgDepartment>> parentMap) {
        OrgDepartmentTreeNodeVO node = new OrgDepartmentTreeNodeVO();
        node.setId(dept.getId() != null ? dept.getId().toString() : null);
        node.setCompanyId(dept.getCompanyId() != null ? dept.getCompanyId().toString() : null);
        node.setParentId(dept.getParentId() != null ? dept.getParentId().toString() : "0");
        node.setDeptCode(dept.getDeptCode());
        node.setDeptName(dept.getDeptName());
        node.setOrderNum(dept.getOrderNum());
        node.setStatus(dept.getStatus());

        // Recursively build children
        List<OrgDepartment> children = parentMap.getOrDefault(dept.getId(), new ArrayList<>());
        node.setChildren(children.stream()
                .map(child -> buildTreeNode(child, parentMap))
                .collect(Collectors.toList()));

        return node;
    }

    public IPage<OrgDepartmentVO> getPage(long pageNo, long pageSize, Long companyId, String deptCode, String deptName, String status) {
        LambdaQueryWrapper<OrgDepartment> wrapper = new LambdaQueryWrapper<>();
        if (companyId != null) wrapper.eq(OrgDepartment::getCompanyId, companyId);
        if (StringUtils.hasText(deptCode)) wrapper.like(OrgDepartment::getDeptCode, deptCode);
        if (StringUtils.hasText(deptName)) wrapper.like(OrgDepartment::getDeptName, deptName);
        if (StringUtils.hasText(status)) wrapper.eq(OrgDepartment::getStatus, status);
        wrapper.eq(OrgDepartment::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByAsc(OrgDepartment::getOrderNum, OrgDepartment::getId);

        Page<OrgDepartment> page = orgDepartmentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public OrgDepartmentVO getById(Long id) {
        OrgDepartment dept = orgDepartmentMapper.selectById(id);
        if (dept == null) throw new BusinessException("ORG_DEPT_NOT_FOUND", "部门不存在");
        if (!dept.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "部门不存在");
        }
        return toVO(dept);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(OrgDepartment dept) {
        Long tenantId = UserContext.getCurrentTenantId();
        dept.setTenantId(tenantId);
        requireCompany(dept.getCompanyId(), tenantId);
        dept.setParentId(normalizeParent(null, dept.getParentId(), dept.getCompanyId(), tenantId));
        if (dept.getStatus() == null) dept.setStatus("ENABLE");
        validateStatus(dept.getStatus());
        if (dept.getOrderNum() == null) dept.setOrderNum(0);
        orgDepartmentMapper.insert(dept);
        return dept.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(OrgDepartment dept) {
        OrgDepartment existing = orgDepartmentMapper.selectById(dept.getId());
        if (existing == null)
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "部门不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "部门不存在");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        Long previousCompanyId = existing.getCompanyId();
        Long companyId = dept.getCompanyId() == null ? existing.getCompanyId() : dept.getCompanyId();
        requireCompany(companyId, tenantId);
        existing.setCompanyId(companyId);
        if (dept.getParentId() != null) {
            existing.setParentId(normalizeParent(existing.getId(), dept.getParentId(), companyId, tenantId));
        } else if (!companyId.equals(previousCompanyId)) {
            existing.setParentId(null);
        }
        if (StringUtils.hasText(dept.getDeptCode())) existing.setDeptCode(dept.getDeptCode().trim());
        if (StringUtils.hasText(dept.getDeptName())) existing.setDeptName(dept.getDeptName().trim());
        if (dept.getOrderNum() != null) existing.setOrderNum(dept.getOrderNum());
        if (dept.getStatus() != null) {
            validateStatus(dept.getStatus());
            existing.setStatus(dept.getStatus());
        }
        if (dept.getRemark() != null) existing.setRemark(dept.getRemark());
        orgDepartmentMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrgDepartment existing = orgDepartmentMapper.selectById(id);
        if (existing == null)
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "部门不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "部门不存在");
        }
        // Check no children exist
        LambdaQueryWrapper<OrgDepartment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgDepartment::getTenantId, UserContext.getCurrentTenantId());
        wrapper.eq(OrgDepartment::getParentId, id);
        Long childCount = orgDepartmentMapper.selectCount(wrapper);
        if (childCount > 0) {
            throw new BusinessException("ORG_DEPT_HAS_CHILDREN", "该部门下存在子部门，无法删除");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        boolean referenced = orgPositionMapper.selectCount(new LambdaQueryWrapper<OrgPosition>()
                .eq(OrgPosition::getTenantId, tenantId)
                .eq(OrgPosition::getDepartmentId, id)) > 0
                || referenceCount("sys_user", id, tenantId) > 0
                || referenceCount("pm_project", id, tenantId) > 0
                || referenceCount("ct_contract", id, tenantId) > 0
                || referenceCount("cost_item", id, tenantId) > 0;
        if (referenced) {
            throw new BusinessException("ORG_DEPT_REFERENCED", "部门已被岗位或业务数据引用，无法删除");
        }
        orgDepartmentMapper.deleteById(id);
    }

    private void requireCompany(Long companyId, Long tenantId) {
        if (companyId == null || orgCompanyMapper.selectOne(new LambdaQueryWrapper<OrgCompany>()
                .eq(OrgCompany::getId, companyId)
                .eq(OrgCompany::getTenantId, tenantId)) == null) {
            throw new BusinessException("ORG_COMPANY_NOT_FOUND", "所属公司不存在");
        }
    }

    private Long normalizeParent(Long currentId, Long parentId, Long companyId, Long tenantId) {
        if (parentId == null || parentId == 0L) return null;
        HashSet<Long> visited = new HashSet<>();
        if (currentId != null) visited.add(currentId);
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            if (!visited.add(cursor)) {
                throw new BusinessException("ORG_DEPT_PARENT_INVALID", "部门上级关系不能形成循环");
            }
            OrgDepartment parent = orgDepartmentMapper.selectById(cursor);
            if (parent == null || !tenantId.equals(parent.getTenantId())
                    || !companyId.equals(parent.getCompanyId())) {
                throw new BusinessException("ORG_DEPT_PARENT_NOT_FOUND", "父部门不存在或不属于所选公司");
            }
            cursor = parent.getParentId();
        }
        return parentId;
    }

    private int referenceCount(String table, Long id, Long tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND org_id=?",
                Integer.class, tenantId, id);
    }

    private void validateStatus(String status) {
        if (!"ENABLE".equals(status) && !"DISABLE".equals(status)) {
            throw new BusinessException("ORG_DEPT_STATUS_INVALID", "部门状态只允许ENABLE或DISABLE");
        }
    }

    private OrgDepartmentVO toVO(OrgDepartment d) {
        OrgDepartmentVO vo = new OrgDepartmentVO();
        vo.setId(d.getId() == null ? null : String.valueOf(d.getId()));
        vo.setCompanyId(d.getCompanyId() != null ? String.valueOf(d.getCompanyId()) : null);
        vo.setParentId(d.getParentId() != null ? String.valueOf(d.getParentId()) : "0");
        vo.setDeptCode(d.getDeptCode());
        vo.setDeptName(d.getDeptName());
        vo.setOrderNum(d.getOrderNum());
        vo.setStatus(d.getStatus());
        vo.setCreatedBy(d.getCreatedBy() != null ? String.valueOf(d.getCreatedBy()) : null);
        if (d.getCreatedTime() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(d.getCreatedTime()));
        if (d.getUpdatedTime() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(d.getUpdatedTime()));
        vo.setRemark(d.getRemark());
        return vo;
    }
}
