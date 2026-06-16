package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.vo.CostSubjectTreeNodeVO;
import com.cgcpms.cost.vo.CostSubjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cgcpms.common.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostSubjectService {

    private final CostSubjectMapper costSubjectMapper;

    public List<CostSubjectTreeNodeVO> getTree() {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByAsc(CostSubject::getSortOrder, CostSubject::getId);

        List<CostSubject> allSubjects = costSubjectMapper.selectList(wrapper);

        // Group by parentId for efficient tree building
        Map<Long, List<CostSubject>> parentMap = allSubjects.stream()
                .collect(Collectors.groupingBy(CostSubject::getParentId));

        // Build tree starting from root nodes (parentId = 0)
        List<CostSubject> roots = parentMap.getOrDefault(0L, new ArrayList<>());
        return roots.stream()
                .map(root -> buildTreeNode(root, parentMap))
                .collect(Collectors.toList());
    }

    private CostSubjectTreeNodeVO buildTreeNode(CostSubject subject, Map<Long, List<CostSubject>> parentMap) {
        CostSubjectTreeNodeVO node = new CostSubjectTreeNodeVO();
        node.setId(subject.getId() != null ? subject.getId().toString() : null);
        node.setSubjectCode(subject.getSubjectCode());
        node.setSubjectName(subject.getSubjectName());
        node.setSubjectType(subject.getSubjectType());
        node.setLevel(subject.getLevel());
        node.setStatus(subject.getStatus());
        node.setSortOrder(subject.getSortOrder());
        node.setParentId(subject.getParentId() != null ? subject.getParentId().toString() : "0");

        // Recursively build children
        List<CostSubject> children = parentMap.getOrDefault(subject.getId(), new ArrayList<>());
        node.setChildren(children.stream()
                .map(child -> buildTreeNode(child, parentMap))
                .collect(Collectors.toList()));

        return node;
    }

    public List<CostSubjectVO> getList() {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByAsc(CostSubject::getSortOrder, CostSubject::getId);

        List<CostSubject> subjects = costSubjectMapper.selectList(wrapper);
        return subjects.stream().map(this::toVO).collect(Collectors.toList());
    }

    public CostSubjectVO getById(Long id) {
        CostSubject subject = costSubjectMapper.selectById(id);
        if (subject == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }
        if (!subject.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }
        return toVO(subject);
    }

    @Transactional
    public Long create(CostSubject subject) {
        // Validate parent exists if not root
        if (subject.getParentId() != null && subject.getParentId() != 0L) {
            CostSubject parent = costSubjectMapper.selectById(subject.getParentId());
            if (parent == null) {
                throw new BusinessException("PARENT_NOT_FOUND", "父科目不存在");
            }
            if (!parent.getTenantId().equals(UserContext.getCurrentTenantId())) {
                throw new BusinessException("PARENT_NOT_FOUND", "父科目不存在");
            }
            // Auto-set level
            subject.setLevel(parent.getLevel() + 1);
        } else {
            // Root node
            subject.setParentId(0L);
            subject.setLevel(1);
        }

        // Validate unique subject_code within tenant among active rows only.
        Long count = costSubjectMapper.countByTenantAndCode(
                UserContext.getCurrentTenantId(), subject.getSubjectCode(), null);
        if (count > 0) {
            throw new BusinessException("SUBJECT_CODE_DUPLICATE", "科目编码已存在");
        }

        costSubjectMapper.insert(subject);
        return subject.getId();
    }

    @Transactional
    public void update(CostSubject subject) {
        CostSubject existing = costSubjectMapper.selectById(subject.getId());
        if (existing == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }

        // Validate unique subject_code within tenant among active rows only.
        Long count = costSubjectMapper.countByTenantAndCode(
                UserContext.getCurrentTenantId(), subject.getSubjectCode(), subject.getId());
        if (count > 0) {
            throw new BusinessException("SUBJECT_CODE_DUPLICATE", "科目编码已存在");
        }

        costSubjectMapper.updateById(subject);
    }

    @Transactional
    public void toggleStatus(Long id) {
        CostSubject existing = costSubjectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }

        // Toggle between ENABLE and DISABLE
        String newStatus = "ENABLE".equals(existing.getStatus()) ? "DISABLE" : "ENABLE";
        existing.setStatus(newStatus);
        costSubjectMapper.updateById(existing);
    }

    @Transactional
    public void delete(Long id) {
        CostSubject existing = costSubjectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }

        // Check no children exist
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, UserContext.getCurrentTenantId());
        wrapper.eq(CostSubject::getParentId, id);
        Long childCount = costSubjectMapper.selectCount(wrapper);
        if (childCount > 0) {
            throw new BusinessException("HAS_CHILDREN", "该科目下存在子科目，无法删除");
        }

        costSubjectMapper.deleteById(id);
    }

    private CostSubjectVO toVO(CostSubject subject) {
        CostSubjectVO vo = new CostSubjectVO();
        vo.setId(subject.getId() != null ? subject.getId().toString() : null);
        vo.setTenantId(subject.getTenantId() != null ? subject.getTenantId().toString() : null);
        vo.setParentId(subject.getParentId() != null ? subject.getParentId().toString() : "0");
        vo.setSubjectCode(subject.getSubjectCode());
        vo.setSubjectName(subject.getSubjectName());
        vo.setSubjectType(subject.getSubjectType());
        vo.setLevel(subject.getLevel());
        vo.setSortOrder(subject.getSortOrder());
        vo.setStatus(subject.getStatus());
        vo.setCreatedBy(subject.getCreatedBy() != null ? subject.getCreatedBy().toString() : null);
        vo.setCreatedAt(subject.getCreatedAt() != null ? DateTimeUtils.DTF.format(subject.getCreatedAt()) : null);
        vo.setUpdatedAt(subject.getUpdatedAt() != null ? DateTimeUtils.DTF.format(subject.getUpdatedAt()) : null);
        vo.setRemark(subject.getRemark());
        return vo;
    }
}
