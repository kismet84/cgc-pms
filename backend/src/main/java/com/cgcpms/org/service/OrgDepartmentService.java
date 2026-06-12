package com.cgcpms.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.org.entity.OrgDepartment;
import com.cgcpms.org.mapper.OrgDepartmentMapper;
import com.cgcpms.org.vo.OrgDepartmentTreeNodeVO;
import com.cgcpms.org.vo.OrgDepartmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgDepartmentService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrgDepartmentMapper orgDepartmentMapper;

    public List<OrgDepartmentTreeNodeVO> getTree() {
        LambdaQueryWrapper<OrgDepartment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgDepartment::getTenantId, UserContext.getCurrentTenantId());
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

    @Transactional
    public Long create(OrgDepartment dept) {
        // Validate parent exists if specified
        if (dept.getParentId() != null && dept.getParentId() != 0L) {
            OrgDepartment parent = orgDepartmentMapper.selectById(dept.getParentId());
            if (parent == null) {
                throw new BusinessException("ORG_DEPT_PARENT_NOT_FOUND", "父部门不存在");
            }
            if (!parent.getTenantId().equals(UserContext.getCurrentTenantId())) {
                throw new BusinessException("ORG_DEPT_PARENT_NOT_FOUND", "父部门不存在");
            }
        } else {
            dept.setParentId(null);
        }
        if (dept.getStatus() == null) dept.setStatus("ENABLE");
        if (dept.getOrderNum() == null) dept.setOrderNum(0);
        orgDepartmentMapper.insert(dept);
        return dept.getId();
    }

    @Transactional
    public void update(OrgDepartment dept) {
        OrgDepartment existing = orgDepartmentMapper.selectById(dept.getId());
        if (existing == null)
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "部门不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_DEPT_NOT_FOUND", "部门不存在");
        }
        orgDepartmentMapper.updateById(dept);
    }

    @Transactional
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
        orgDepartmentMapper.deleteById(id);
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
        if (d.getCreatedTime() != null) vo.setCreatedAt(DTF.format(d.getCreatedTime()));
        if (d.getUpdatedTime() != null) vo.setUpdatedAt(DTF.format(d.getUpdatedTime()));
        vo.setRemark(d.getRemark());
        return vo;
    }
}
