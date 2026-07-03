package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.vo.CostSubjectTreeNodeVO;
import com.cgcpms.cost.vo.CostSubjectVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cgcpms.common.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostSubjectService {

    private final CostSubjectMapper costSubjectMapper;
    private final CostItemMapper costItemMapper;
    private final CostTargetItemMapper costTargetItemMapper;

    public List<CostSubjectTreeNodeVO> getTree(String accountCategory) {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, UserContext.getCurrentTenantId());
        if (accountCategory != null && !accountCategory.isEmpty()) {
            wrapper.eq(CostSubject::getAccountCategory, accountCategory);
        }
        wrapper.orderByAsc(CostSubject::getSortOrder, CostSubject::getId);

        List<CostSubject> allSubjects = costSubjectMapper.selectList(wrapper);

        // Group by parentId for efficient tree building
        Map<Long, List<CostSubject>> parentMap = allSubjects.stream()
                .collect(Collectors.groupingBy(CostSubject::getParentId));

        // Build tree starting from root nodes (parentId = 0)
        List<CostSubject> roots = parentMap.getOrDefault(0L, new ArrayList<>());
        return roots.stream()
                .map(root -> buildTreeNode(root, parentMap, new HashSet<>()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private CostSubjectTreeNodeVO buildTreeNode(CostSubject subject, Map<Long, List<CostSubject>> parentMap,
                                                Set<Long> visitedPath) {
        Long subjectId = subject.getId();
        if (subjectId == null) {
            log.warn("Skip cost subject with null id while building tree: code={}", subject.getSubjectCode());
            return null;
        }
        if (!visitedPath.add(subjectId)) {
            log.warn("Detected circular cost subject reference, skip branch: subjectId={}, parentId={}, path={}",
                    subjectId, subject.getParentId(), visitedPath);
            return null;
        }

        CostSubjectTreeNodeVO node = new CostSubjectTreeNodeVO();
        node.setId(subjectId.toString());
        node.setSubjectCode(subject.getSubjectCode());
        node.setSubjectName(subject.getSubjectName());
        node.setSubjectType(subject.getSubjectType());
        node.setAccountCategory(subject.getAccountCategory());
        node.setLevel(subject.getLevel());
        node.setStatus(subject.getStatus());
        node.setSortOrder(subject.getSortOrder());
        node.setParentId(subject.getParentId() != null ? subject.getParentId().toString() : "0");

        // Recursively build children
        List<CostSubject> children = parentMap.getOrDefault(subjectId, new ArrayList<>());
        node.setChildren(children.stream()
                .map(child -> buildTreeNode(child, parentMap, new HashSet<>(visitedPath)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        return node;
    }

    public List<CostSubjectVO> getList(String accountCategory) {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, UserContext.getCurrentTenantId());
        if (accountCategory != null && !accountCategory.isEmpty()) {
            wrapper.eq(CostSubject::getAccountCategory, accountCategory);
        }
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

    @Transactional(rollbackFor = Exception.class)
    public Long create(CostSubject subject) {
        validateParentForSave(subject, null);
        // Validate parent exists if not root
        if (subject.getParentId() != null && subject.getParentId() != 0L) {
            CostSubject parent = costSubjectMapper.selectById(subject.getParentId());
            if (parent == null) {
                throw new BusinessException("PARENT_NOT_FOUND", "父科目不存在");
            }
            if (!parent.getTenantId().equals(UserContext.getCurrentTenantId())) {
                throw new BusinessException("PARENT_NOT_FOUND", "父科目不存在");
            }
            // Auto-set level and account category from parent
            subject.setLevel(parent.getLevel() + 1);
            if (subject.getAccountCategory() == null || subject.getAccountCategory().isEmpty()) {
                subject.setAccountCategory(parent.getAccountCategory());
            }
        } else {
            // Root node
            subject.setParentId(0L);
            subject.setLevel(1);
        }

        // Validate unique subject_code within tenant among active rows only.
        Long count = costSubjectMapper.countByTenantAndCode(
                UserContext.getCurrentTenantId(), subject.getSubjectCode(),
                subject.getAccountCategory(), null);
        if (count > 0) {
            throw new BusinessException("SUBJECT_CODE_DUPLICATE", "科目编码已存在");
        }

        costSubjectMapper.insert(subject);
        return subject.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CostSubject subject) {
        CostSubject existing = costSubjectMapper.selectById(subject.getId());
        if (existing == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "成本科目不存在");
        }

        validateParentForSave(subject, existing.getId());

        // Validate unique subject_code within tenant among active rows only.
        Long count = costSubjectMapper.countByTenantAndCode(
                UserContext.getCurrentTenantId(), subject.getSubjectCode(),
                subject.getAccountCategory(), subject.getId());
        if (count > 0) {
            throw new BusinessException("SUBJECT_CODE_DUPLICATE", "科目编码已存在");
        }

        costSubjectMapper.updateById(subject);
    }

    @Transactional(rollbackFor = Exception.class)
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

    @Transactional(rollbackFor = Exception.class)
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

        // Check no cost item references exist (exclude soft-deleted items)
        long costItemCount = costItemMapper.selectCount(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getCostSubjectId, id)
                        .eq(CostItem::getDeletedFlag, 0));
        if (costItemCount > 0) {
            throw new BusinessException("COST_SUBJECT_REFERENCED",
                    "该成本科目被 " + costItemCount + " 条成本明细引用，无法删除");
        }

        // Check no cost target item references exist (exclude soft-deleted items)
        long targetItemCount = costTargetItemMapper.selectCount(
                new LambdaQueryWrapper<CostTargetItem>()
                        .eq(CostTargetItem::getCostSubjectId, id)
                        .eq(CostTargetItem::getDeletedFlag, 0));
        if (targetItemCount > 0) {
            throw new BusinessException("COST_SUBJECT_REFERENCED",
                    "该成本科目被 " + targetItemCount + " 条目标成本明细引用，无法删除");
        }

        // 检查是否已存在相同编码的已删除记录，避免唯一键冲突
        long existingDeletedCount = costSubjectMapper.selectCount(
                new LambdaQueryWrapper<CostSubject>()
                        .eq(CostSubject::getTenantId, existing.getTenantId())
                        .eq(CostSubject::getSubjectCode, existing.getSubjectCode())
                        .eq(CostSubject::getDeletedFlag, 1));
        if (existingDeletedCount > 0) {
            // 将 subject_code 重命名为唯一值以释放唯一键
            existing.setSubjectCode(existing.getSubjectCode() + "_DEL_" + id);
            costSubjectMapper.updateById(existing);
        }
        costSubjectMapper.deleteById(id);
    }

    private void validateParentForSave(CostSubject subject, Long currentId) {
        Long parentId = subject.getParentId();
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId < 0L) {
            throw new BusinessException("PARENT_INVALID", "父科目非法");
        }
        if (currentId != null && parentId.equals(currentId)) {
            throw new BusinessException("PARENT_INVALID", "父科目不能指向自身");
        }
        if (currentId != null) {
            assertParentDoesNotCreateCycle(parentId, currentId);
        }
    }

    private void assertParentDoesNotCreateCycle(Long parentId, Long currentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            if (!visited.add(cursor)) {
                throw new BusinessException("PARENT_INVALID", "父科目层级存在循环引用");
            }
            if (cursor.equals(currentId)) {
                throw new BusinessException("PARENT_INVALID", "父科目不能指向当前科目的子孙节点");
            }
            CostSubject parent = costSubjectMapper.selectById(cursor);
            if (parent == null) {
                return;
            }
            cursor = parent.getParentId();
        }
    }

    private CostSubjectVO toVO(CostSubject subject) {
        CostSubjectVO vo = new CostSubjectVO();
        vo.setId(subject.getId() != null ? subject.getId().toString() : null);
        vo.setTenantId(subject.getTenantId() != null ? subject.getTenantId().toString() : null);
        vo.setParentId(subject.getParentId() != null ? subject.getParentId().toString() : "0");
        vo.setSubjectCode(subject.getSubjectCode());
        vo.setSubjectName(subject.getSubjectName());
        vo.setSubjectType(subject.getSubjectType());
        vo.setAccountCategory(subject.getAccountCategory());
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
