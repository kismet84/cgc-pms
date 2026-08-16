package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.constant.AccountingSubjectCatalog;
import com.cgcpms.cost.constant.TargetCostSubjectCatalog;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.vo.CostSubjectTreeNodeVO;
import com.cgcpms.cost.vo.CostSubjectVO;
import com.cgcpms.system.role.SystemRoleContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostSubjectService {

    private static final Set<String> ACCOUNT_CATEGORIES = Set.of(
            "ASSET", "LIABILITY", "EQUITY", "COST", "REVENUE", "SETTLEMENT", "RECEIVABLE");

    private final CostSubjectMapper costSubjectMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<CostSubjectTreeNodeVO> getTree(String accountCategory) {
        return getTree(accountCategory, false);
    }

    public List<CostSubjectTreeNodeVO> getAccountingTree(String accountCategory) {
        return getTree(accountCategory, true);
    }

    private List<CostSubjectTreeNodeVO> getTree(String accountCategory, boolean ledgerOnly) {
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, UserContext.getCurrentTenantId());
        if (ledgerOnly) {
            wrapper.eq(CostSubject::getLedgerFlag, 1);
        }
        if (accountCategory != null && !accountCategory.isEmpty()) {
            wrapper.eq(CostSubject::getAccountCategory, accountCategory);
            if ("COST".equals(accountCategory) && !ledgerOnly) wrapper.eq(CostSubject::getLedgerFlag, 0);
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
        node.setDefaultTargetRatio(subject.getDefaultTargetRatio());
        node.setLedgerFlag(subject.getLedgerFlag());

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
            if ("COST".equals(accountCategory)) wrapper.eq(CostSubject::getLedgerFlag, 0);
        }
        wrapper.orderByAsc(CostSubject::getSortOrder, CostSubject::getId);

        List<CostSubject> subjects = costSubjectMapper.selectList(wrapper);
        return subjects.stream().map(this::toVO).collect(Collectors.toList());
    }

    public Map<String, Object> getAccountingOverview() {
        Long tenantId = UserContext.getCurrentTenantId();
        List<Map<String, Object>> policies = jdbcTemplate.queryForList("""
                SELECT subject.subject_code subjectCode,subject.subject_name subjectName,
                       rule_row.project_requirement projectRequirement,
                       rule_row.contract_requirement contractRequirement,
                       rule_row.partner_requirement partnerRequirement,
                       rule_row.department_requirement departmentRequirement,
                       rule_row.employee_requirement employeeRequirement,
                       rule_row.allowed_contract_types allowedContractTypes,
                       rule_row.allowed_partner_types allowedPartnerTypes
                FROM accounting_subject_dimension_rule rule_row
                JOIN cost_subject subject ON subject.tenant_id=rule_row.tenant_id
                 AND subject.id=rule_row.accounting_subject_id AND subject.deleted_flag=0
                WHERE rule_row.tenant_id=?
                ORDER BY subject.subject_code
                """, tenantId);
        List<Map<String, Object>> mappings = jdbcTemplate.queryForList("""
                SELECT mapping.category_code categoryCode,mapping.category_name categoryName,
                       source.subject_code fulfillmentCode,source.subject_name fulfillmentName,
                       target.subject_code expenseCode,target.subject_name expenseName,mapping.status
                FROM accounting_cost_carryover_mapping mapping
                JOIN cost_subject source ON source.tenant_id=mapping.tenant_id AND source.id=mapping.fulfillment_subject_id
                JOIN cost_subject target ON target.tenant_id=mapping.tenant_id AND target.id=mapping.expense_subject_id
                WHERE mapping.tenant_id=? ORDER BY mapping.category_code
                """, tenantId);
        List<Map<String, Object>> legacyReviews = jdbcTemplate.queryForList("""
                SELECT source_subject_code sourceSubjectCode,source_subject_name sourceSubjectName,
                       suggested_subject_code suggestedSubjectCode,review_status reviewStatus,review_note reviewNote
                FROM accounting_subject_legacy_review
                WHERE tenant_id=? ORDER BY source_subject_code
                """, tenantId);
        List<Map<String, String>> reportRoutes = List.of(
                Map.of("label", "成本明细账", "path", "/cost/ledger"),
                Map.of("label", "成本汇总", "path", "/cost/summary"),
                Map.of("label", "项目利润", "path", "/cost/control"),
                Map.of("label", "收款与应收", "path", "/revenue-operations"),
                Map.of("label", "付款与应付", "path", "/payment/applications"),
                Map.of("label", "会计凭证", "path", "/accounting-entry"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policies", policies);
        result.put("carryoverMappings", mappings);
        result.put("legacyReviews", legacyReviews);
        result.put("reportRoutes", reportRoutes);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reviewAccountingLegacySubject(String sourceSubjectCode, String reviewStatus, String reviewNote) {
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        if (tenantId == null || userId == null) {
            throw new BusinessException("USER_CONTEXT_REQUIRED", "缺少用户或租户上下文");
        }
        if (!"CONFIRMED".equals(reviewStatus) && !"IGNORED".equals(reviewStatus)) {
            throw new BusinessException("ACCOUNTING_LEGACY_REVIEW_STATUS_INVALID", "历史科目复核状态不合法");
        }
        String code = sourceSubjectCode == null ? "" : sourceSubjectCode.trim();
        Map<String, Object> review = jdbcTemplate.query("""
                SELECT suggested_subject_code,review_status
                FROM accounting_subject_legacy_review
                WHERE tenant_id=? AND source_subject_code=? FOR UPDATE
                """, rs -> rs.next() ? Map.of(
                        "suggestedCode", rs.getString("suggested_subject_code") == null
                                ? "" : rs.getString("suggested_subject_code"),
                        "status", rs.getString("review_status")) : null,
                tenantId, code);
        if (review == null) {
            throw new BusinessException("ACCOUNTING_LEGACY_REVIEW_NOT_FOUND", "历史科目复核记录不存在");
        }
        if (!"PENDING".equals(review.get("status"))) {
            throw new BusinessException("ACCOUNTING_LEGACY_REVIEW_ALREADY_FINISHED", "历史科目已完成复核");
        }
        String suggestedCode = review.get("suggestedCode").toString();
        if ("CONFIRMED".equals(reviewStatus)) {
            if (!StringUtils.hasText(suggestedCode) || countReference("""
                    SELECT COUNT(*) FROM cost_subject subject
                    WHERE subject.tenant_id=? AND subject.subject_code=? AND subject.ledger_flag=1
                      AND subject.status='ENABLE' AND subject.deleted_flag=0
                      AND NOT EXISTS (SELECT 1 FROM cost_subject child
                                      WHERE child.tenant_id=subject.tenant_id AND child.parent_id=subject.id
                                        AND child.deleted_flag=0)
                    """, tenantId, suggestedCode) != 1) {
                throw new BusinessException("ACCOUNTING_LEGACY_REVIEW_TARGET_INVALID", "建议正式科目不存在、未启用或非末级");
            }
        }
        String note = StringUtils.hasText(reviewNote) ? reviewNote.trim()
                : "CONFIRMED".equals(reviewStatus)
                ? "已确认后续业务使用建议正式科目"
                : "保留历史快照，不建立统一正式科目映射";
        if (note.length() > 500) {
            throw new BusinessException("ACCOUNTING_LEGACY_REVIEW_NOTE_TOO_LONG", "复核说明不能超过500个字符");
        }
        int updated = jdbcTemplate.update("""
                UPDATE accounting_subject_legacy_review
                SET review_status=?,review_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND source_subject_code=? AND review_status='PENDING'
                """, reviewStatus, note, userId, tenantId, code);
        if (updated != 1) {
            throw new BusinessException("ACCOUNTING_LEGACY_REVIEW_CONCURRENT_MODIFICATION", "历史科目复核状态已变化，请刷新后重试");
        }
    }

    public List<CostSubjectVO> getBidOptions() {
        Long tenantId = UserContext.getCurrentTenantId();
        CostSubject parent = costSubjectMapper.selectOne(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getTenantId, tenantId)
                .eq(CostSubject::getSubjectCode, "5401.01"));
        if (parent == null) return List.of();
        return costSubjectMapper.selectList(new LambdaQueryWrapper<CostSubject>()
                        .eq(CostSubject::getTenantId, tenantId)
                        .eq(CostSubject::getParentId, parent.getId())
                        .eq(CostSubject::getStatus, "ENABLE")
                        .orderByAsc(CostSubject::getSortOrder, CostSubject::getId))
                .stream().map(this::toVO).toList();
    }

    public CostSubjectVO getById(Long id) {
        CostSubject subject = costSubjectMapper.selectById(id);
        if (subject == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "会计科目不存在");
        }
        if (!subject.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "会计科目不存在");
        }
        return toVO(subject);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CostSubject subject) {
        requireCompanyFinanceOperator();
        subject.setId(null);
        subject.setTenantId(UserContext.getCurrentTenantId());
        subject.setDeletedFlag(0);
        subject.setLedgerFlag(0);
        assertStandardAccountingSubjectNotCreated(subject.getSubjectCode());
        assertGenericStructureEditable(subject.getSubjectCode());
        validateParentForSave(subject, null);
        // Validate parent exists if not root
        if (subject.getParentId() != null && subject.getParentId() != 0L) {
            CostSubject parent = selectSubjectForUpdate(subject.getParentId());
            if (parent == null) {
                throw new BusinessException("PARENT_NOT_FOUND", "父科目不存在");
            }
            if (!parent.getTenantId().equals(UserContext.getCurrentTenantId())) {
                throw new BusinessException("PARENT_NOT_FOUND", "父科目不存在");
            }
            assertGenericStructureEditable(parent.getSubjectCode());
            assertAccountingStructureEditable(parent.getSubjectCode());
            Long childCount = costSubjectMapper.selectCount(new LambdaQueryWrapper<CostSubject>()
                    .eq(CostSubject::getTenantId, parent.getTenantId())
                    .eq(CostSubject::getParentId, parent.getId()));
            if (childCount == 0) {
                assertNoActiveReferences(parent, "新增子科目");
            }
            // Auto-set level and account category from parent
            subject.setLevel(parent.getLevel() + 1);
            if (subject.getAccountCategory() != null
                    && !subject.getAccountCategory().isEmpty()
                    && !Objects.equals(subject.getAccountCategory(), parent.getAccountCategory())) {
                throw new BusinessException("ACCOUNT_CATEGORY_PARENT_MISMATCH", "子科目分类必须与父科目一致");
            }
            subject.setAccountCategory(parent.getAccountCategory());
        } else {
            // Root node
            subject.setParentId(0L);
            subject.setLevel(1);
        }
        validateAccountCategory(subject.getAccountCategory());

        // Validate unique subject_code within tenant among active rows only.
        Long count = costSubjectMapper.countByTenantAndCode(
                UserContext.getCurrentTenantId(), subject.getSubjectCode(), null);
        if (count > 0) {
            throw new BusinessException("SUBJECT_CODE_DUPLICATE", "科目编码已存在");
        }

        costSubjectMapper.insert(subject);
        return subject.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CostSubject subject) {
        requireCompanyFinanceOperator();
        CostSubject existing = selectSubjectForUpdate(subject.getId());
        if (existing == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "会计科目不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "会计科目不存在");
        }
        subject.setTenantId(existing.getTenantId());
        if (subject.getAccountCategory() == null || subject.getAccountCategory().isEmpty()) {
            subject.setAccountCategory(existing.getAccountCategory());
        }
        if (subject.getParentId() == null) {
            subject.setParentId(existing.getParentId());
        }
        validateAccountCategory(subject.getAccountCategory());
        assertGovernedMetadataUpdate(existing, subject);
        assertAccountingMetadataUpdate(existing, subject);
        if (changesReferencedStructure(existing, subject)) {
            assertNoActiveReferences(existing, "修改编码、层级、类型或分类");
        }
        if ("ENABLE".equals(existing.getStatus()) && "DISABLE".equals(subject.getStatus())) {
            assertNoActiveReferences(existing, "停用");
        }

        validateParentForSave(subject, existing.getId());
        validateHierarchyForUpdate(subject, existing);

        // Validate unique subject_code within tenant among active rows only.
        Long count = costSubjectMapper.countByTenantAndCode(
                UserContext.getCurrentTenantId(), subject.getSubjectCode(), subject.getId());
        if (count > 0) {
            throw new BusinessException("SUBJECT_CODE_DUPLICATE", "科目编码已存在");
        }

        costSubjectMapper.updateById(subject);
    }

    private CostSubject selectSubjectForUpdate(Long id) {
        return costSubjectMapper.selectOne(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getId, id)
                .eq(CostSubject::getTenantId, UserContext.getCurrentTenantId())
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        requireCompanyFinanceOperator();
        CostSubject existing = selectSubjectForUpdate(id);
        if (existing == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "会计科目不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "会计科目不存在");
        }
        assertGenericStructureEditable(existing.getSubjectCode());

        String newStatus = "ENABLE".equals(existing.getStatus()) ? "DISABLE" : "ENABLE";
        if ("DISABLE".equals(newStatus)) {
            assertNoActiveReferences(existing, "停用");
        }
        existing.setStatus(newStatus);
        costSubjectMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireCompanyFinanceOperator();
        CostSubject existing = selectSubjectForUpdate(id);
        if (existing == null) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "会计科目不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_SUBJECT_NOT_FOUND", "会计科目不存在");
        }
        assertGenericStructureEditable(existing.getSubjectCode());
        assertAccountingStructureEditable(existing.getSubjectCode());

        // Check no children exist
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, UserContext.getCurrentTenantId());
        wrapper.eq(CostSubject::getParentId, id);
        Long childCount = costSubjectMapper.selectCount(wrapper);
        if (childCount > 0) {
            throw new BusinessException("HAS_CHILDREN", "该科目下存在子科目，无法删除");
        }

        assertNoActiveReferences(existing, "删除");

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

    @Transactional(rollbackFor = Exception.class)
    public List<CostSubjectVO> updateTargetRatios(List<TargetRatio> ratios) {
        requireCompanyFinanceOperator();
        if (ratios == null || ratios.size() != TargetCostSubjectCatalog.ITEMS.size()) {
            throw new BusinessException("TARGET_COST_RATIO_SET_INVALID", "必须一次提交全部10类目标成本比例");
        }
        Map<String, BigDecimal> requested = new LinkedHashMap<>();
        for (TargetRatio ratio : ratios) {
            if (ratio == null || ratio.subjectCode() == null || !TargetCostSubjectCatalog.CODES.contains(ratio.subjectCode())
                    || requested.putIfAbsent(ratio.subjectCode(), normalizeRatio(ratio.ratio())) != null) {
                throw new BusinessException("TARGET_COST_RATIO_SET_INVALID", "目标成本科目集合缺失、重复或包含非法编码");
            }
        }
        if (!requested.keySet().equals(TargetCostSubjectCatalog.CODES)
                || requested.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("100.00")) != 0) {
            throw new BusinessException("TARGET_COST_RATIO_SUM_INVALID", "10类目标成本比例合计必须为100% ");
        }

        List<CostSubject> subjects = costSubjectMapper.selectList(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getTenantId, UserContext.getCurrentTenantId())
                .in(CostSubject::getSubjectCode, TargetCostSubjectCatalog.CODES)
                .eq(CostSubject::getStatus, "ENABLE")
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        if (subjects.size() != TargetCostSubjectCatalog.ITEMS.size()
                || !subjects.stream().map(CostSubject::getSubjectCode).collect(Collectors.toSet()).equals(TargetCostSubjectCatalog.CODES)) {
            throw new BusinessException("TARGET_COST_SUBJECT_SET_INVALID", "10类目标成本科目未完整启用");
        }
        subjects.forEach(subject -> {
            subject.setDefaultTargetRatio(requested.get(subject.getSubjectCode()));
            costSubjectMapper.updateById(subject);
        });
        return subjects.stream().map(this::toVO).toList();
    }

    private static BigDecimal normalizeRatio(BigDecimal ratio) {
        if (ratio == null || ratio.signum() < 0 || ratio.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("TARGET_COST_RATIO_INVALID", "目标成本比例必须在0%至100%之间");
        }
        try {
            return ratio.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BusinessException("TARGET_COST_RATIO_INVALID", "目标成本比例最多保留2位小数");
        }
    }

    private void requireCompanyFinanceOperator() {
        Integer matches = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
                JOIN sys_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id
                WHERE u.tenant_id=? AND u.id=? AND u.status='ENABLE' AND u.deleted_flag=0
                  AND r.role_code=? AND r.status='ENABLE' AND r.deleted_flag=0
                """, Integer.class, UserContext.getCurrentTenantId(), UserContext.getCurrentUserId(),
                SystemRoleContract.COMPANY_FINANCE);
        if (matches == null || matches == 0) {
            throw new BusinessException("COST_COMPANY_FINANCE_REQUIRED", "仅公司财务可维护会计科目与成本治理配置");
        }
    }

    private static void assertGenericStructureEditable(String subjectCode) {
        if (subjectCode != null && (subjectCode.equals(TargetCostSubjectCatalog.PARENT_CODE)
                || subjectCode.startsWith(TargetCostSubjectCatalog.PARENT_CODE + "."))) {
            throw new BusinessException("TARGET_COST_SUBJECT_GOVERNED", "项目目标成本固定科目只能通过专用比例接口维护");
        }
    }

    private static void assertGovernedMetadataUpdate(CostSubject existing, CostSubject requested) {
        boolean existingGoverned = isGovernedSubject(existing.getSubjectCode());
        boolean requestedGoverned = isGovernedSubject(requested.getSubjectCode());
        if (!existingGoverned && !requestedGoverned) return;
        if (!existingGoverned || !requestedGoverned
                || !Objects.equals(existing.getSubjectCode(), requested.getSubjectCode())
                || !Objects.equals(existing.getParentId(), requested.getParentId())
                || !Objects.equals(existing.getSubjectType(), requested.getSubjectType())
                || !Objects.equals(existing.getAccountCategory(), requested.getAccountCategory())
                || !Objects.equals(existing.getStatus(), requested.getStatus())) {
            throw new BusinessException("TARGET_COST_SUBJECT_GOVERNED", "项目目标成本固定科目仅允许编辑名称和排序");
        }
    }

    private static boolean isGovernedSubject(String subjectCode) {
        return subjectCode != null && (subjectCode.equals(TargetCostSubjectCatalog.PARENT_CODE)
                || subjectCode.startsWith(TargetCostSubjectCatalog.PARENT_CODE + "."));
    }

    private static void validateAccountCategory(String accountCategory) {
        if (accountCategory == null || accountCategory.isBlank() || !ACCOUNT_CATEGORIES.contains(accountCategory)) {
            throw new BusinessException("ACCOUNT_CATEGORY_INVALID", "会计科目分类不合法");
        }
    }

    private static void assertStandardAccountingSubjectNotCreated(String subjectCode) {
        if (subjectCode != null && AccountingSubjectCatalog.GOVERNED_CODES.contains(subjectCode)) {
            throw new BusinessException("ACCOUNTING_SUBJECT_GOVERNED", "系统记账科目已由迁移统一建立");
        }
    }

    private static void assertAccountingStructureEditable(String subjectCode) {
        if (subjectCode != null && AccountingSubjectCatalog.GOVERNED_CODES.contains(subjectCode)) {
            throw new BusinessException("ACCOUNTING_SUBJECT_GOVERNED", "系统记账科目只允许编辑名称和排序");
        }
    }

    private static void assertAccountingMetadataUpdate(CostSubject existing, CostSubject requested) {
        if (!AccountingSubjectCatalog.GOVERNED_CODES.contains(existing.getSubjectCode())) return;
        if (!Objects.equals(existing.getSubjectCode(), requested.getSubjectCode())
                || !Objects.equals(existing.getParentId(), requested.getParentId())
                || !Objects.equals(existing.getSubjectType(), requested.getSubjectType())
                || !Objects.equals(existing.getAccountCategory(), requested.getAccountCategory())
                || !Objects.equals(existing.getStatus(), requested.getStatus())) {
            throw new BusinessException("ACCOUNTING_SUBJECT_GOVERNED", "系统记账科目只允许编辑名称和排序");
        }
    }

    private static boolean changesReferencedStructure(CostSubject existing, CostSubject requested) {
        return requested.getSubjectCode() != null
                    && !Objects.equals(existing.getSubjectCode(), requested.getSubjectCode())
                || requested.getParentId() != null
                    && !Objects.equals(existing.getParentId(), requested.getParentId())
                || requested.getSubjectType() != null
                    && !Objects.equals(existing.getSubjectType(), requested.getSubjectType())
                || requested.getAccountCategory() != null
                    && !Objects.equals(existing.getAccountCategory(), requested.getAccountCategory());
    }

    private void assertNoActiveReferences(CostSubject subject, String action) {
        Long subjectId = subject.getId();
        Long tenantId = subject.getTenantId();
        Map<String, Long> referenceCounts = new LinkedHashMap<>();
        referenceCounts.put("成本明细", countReference(
                "SELECT COUNT(*) FROM cost_item WHERE tenant_id=? AND (cost_subject_id=? OR original_cost_subject_id=?) AND deleted_flag=0",
                tenantId, subjectId, subjectId));
        referenceCounts.put("目标成本明细", countReference(
                "SELECT COUNT(*) FROM cost_target_item WHERE tenant_id=? AND cost_subject_id=? AND deleted_flag=0", tenantId, subjectId));
        referenceCounts.put("完工成本预测", countReference(
                "SELECT COUNT(*) FROM cost_forecast_item WHERE tenant_id=? AND cost_subject_id=?", tenantId, subjectId));
        referenceCounts.put("项目预算明细", countReference(
                "SELECT COUNT(*) FROM project_budget_line WHERE tenant_id=? AND cost_subject_id=? AND deleted_flag=0", tenantId, subjectId));
        referenceCounts.put("付款申请", countReference(
                "SELECT COUNT(*) FROM pay_application WHERE tenant_id=? AND cost_subject_id=? AND deleted_flag=0", tenantId, subjectId));
        referenceCounts.put("费用申请", countReference(
                "SELECT COUNT(*) FROM expense_application WHERE tenant_id=? AND cost_subject_id=? AND deleted_flag=0", tenantId, subjectId));
        referenceCounts.put("结算明细", countReference(
                "SELECT COUNT(*) FROM stl_settlement_item WHERE tenant_id=? AND cost_subject_id=? AND deleted_flag=0", tenantId, subjectId));
        Long accountingLines = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounting_entry_line WHERE tenant_id=? AND deleted_flag=0 "
                        + "AND (cost_subject_id=? OR accounting_subject_id=? OR account_code=?)",
                Long.class, tenantId, subjectId, subjectId, subject.getSubjectCode());
        referenceCounts.put("会计凭证明细", accountingLines == null ? 0L : accountingLines);
        referenceCounts.put("V2历史映射", countReference(
                "SELECT COUNT(*) FROM cost_subject_mapping_item WHERE tenant_id=? AND (source_subject_id=? OR target_subject_id=?)",
                tenantId, subjectId, subjectId));
        referenceCounts.put("V2归集规则", countReference(
                "SELECT COUNT(*) FROM cost_subject_assignment_rule WHERE tenant_id=? AND cost_subject_id=?", tenantId, subjectId));
        referenceCounts.put("项目适用范围", countReference(
                "SELECT COUNT(*) FROM project_cost_subject_scope WHERE tenant_id=? AND cost_subject_id=?", tenantId, subjectId));
        referenceCounts.put("项目配置历史", countReference(
                "SELECT COUNT(*) FROM project_cost_subject_scope_history WHERE tenant_id=? AND cost_subject_id=?", tenantId, subjectId));
        referenceCounts.put("项目配置申请", countReference(
                "SELECT COUNT(*) FROM cost_project_config_request_line WHERE tenant_id=? AND cost_subject_id=?", tenantId, subjectId));
        referenceCounts.put("归类冻结快照", countReference(
                "SELECT COUNT(*) FROM cost_classification_snapshot WHERE tenant_id=? AND (original_cost_subject_id=? OR matched_cost_subject_id=?)",
                tenantId, subjectId, subjectId));
        referenceCounts.put("待归类业务", countReference(
                "SELECT COUNT(*) FROM cost_unclassified_case WHERE tenant_id=? AND original_cost_subject_id=?",
                tenantId, subjectId));
        referenceCounts.put("财务归类覆盖", countReference(
                "SELECT COUNT(*) FROM cost_classification_override WHERE tenant_id=? AND (original_cost_subject_id=? OR matched_cost_subject_id=? OR override_cost_subject_id=?)",
                tenantId, subjectId, subjectId, subjectId));
        referenceCounts.put("历史重算明细", countReference(
                "SELECT COUNT(*) FROM cost_recalculation_line WHERE tenant_id=? AND (old_cost_subject_id=? OR new_cost_subject_id=?)",
                tenantId, subjectId, subjectId));
        referenceCounts.put("投标转入申请", countReference(
                "SELECT COUNT(*) FROM bid_cost_target_transfer_request_line WHERE tenant_id=? AND (source_subject_id=? OR target_subject_id=?)",
                tenantId, subjectId, subjectId));
        referenceCounts.put("投标转入事实", countReference(
                "SELECT COUNT(*) FROM bid_cost_target_transfer_line WHERE tenant_id=? AND (source_subject_id=? OR target_subject_id=?)",
                tenantId, subjectId, subjectId));
        referenceCounts.put("财务分摊申请", countReference(
                "SELECT COUNT(*) FROM finance_cost_allocation_request WHERE tenant_id=? AND (cost_subject_id=? OR matched_cost_subject_id=?)",
                tenantId, subjectId, subjectId));
        referenceCounts.put("财务分摊申请明细", countReference(
                "SELECT COUNT(*) FROM finance_cost_allocation_request_line WHERE tenant_id=? AND (matched_cost_subject_id=? OR selected_cost_subject_id=?)",
                tenantId, subjectId, subjectId));
        referenceCounts.put("间接费分摊规则", countReference(
                "SELECT COUNT(*) FROM overhead_allocation_rule WHERE tenant_id=? AND cost_subject_id=? AND deleted_flag=0", tenantId, subjectId));
        referenceCounts.put("成本汇总", countReference(
                "SELECT COUNT(*) FROM cost_summary WHERE tenant_id=? AND cost_subject_id=? AND deleted_flag=0", tenantId, subjectId));
        referenceCounts.put("质量安全后果", countReference(
                "SELECT COUNT(*) FROM qs_consequence WHERE tenant_id=? AND cost_subject_id=? AND deleted_flag=0", tenantId, subjectId));
        referenceCounts.put("财务费用分摊", countReference(
                "SELECT COUNT(*) FROM finance_cost_allocation_batch WHERE tenant_id=? AND cost_subject_id=?", tenantId, subjectId));

        List<String> references = referenceCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey() + entry.getValue() + "条")
                .toList();
        if (!references.isEmpty()) {
            throw new BusinessException("COST_SUBJECT_REFERENCED",
                    "该会计科目被" + String.join("、", references) + "引用，无法" + action);
        }
    }

    private long countReference(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
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

    private void validateHierarchyForUpdate(CostSubject requested, CostSubject existing) {
        Long parentId = requested.getParentId();
        Long childCount = costSubjectMapper.selectCount(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getTenantId, existing.getTenantId())
                .eq(CostSubject::getParentId, existing.getId()));
        boolean parentChanged = requested.getParentId() != null
                && !Objects.equals(existing.getParentId(), requested.getParentId());
        if (childCount > 0 && !Objects.equals(existing.getAccountCategory(), requested.getAccountCategory())) {
            throw new BusinessException("ACCOUNT_CATEGORY_CHILDREN_MISMATCH", "存在子科目的会计科目不能变更分类");
        }
        if (childCount > 0 && parentChanged) {
            throw new BusinessException("COST_SUBJECT_WITH_CHILDREN_REPARENT_FORBIDDEN", "存在子科目的会计科目不能调整父级");
        }

        Map<Long, CostSubject> lockedParents = new LinkedHashMap<>();
        if (parentChanged) {
            java.util.stream.Stream.of(existing.getParentId(), parentId)
                    .filter(id -> id != null && id != 0L)
                    .distinct().sorted()
                    .forEach(id -> lockedParents.put(id, selectSubjectForUpdate(id)));
        }
        if (parentId == null || parentId == 0L) {
            requested.setParentId(0L);
            requested.setLevel(1);
            return;
        }
        CostSubject parent = parentChanged ? lockedParents.get(parentId) : costSubjectMapper.selectById(parentId);
        if (parent == null || !Objects.equals(parent.getTenantId(), existing.getTenantId())) {
            throw new BusinessException("PARENT_NOT_FOUND", "父科目不存在");
        }
        if (parentChanged) {
            Long newParentChildCount = costSubjectMapper.selectCount(new LambdaQueryWrapper<CostSubject>()
                    .eq(CostSubject::getTenantId, existing.getTenantId())
                    .eq(CostSubject::getParentId, parentId));
            if (newParentChildCount == 0) {
                assertNoActiveReferences(parent, "新增子科目");
            }
        }
        boolean historicalRelationshipUnchanged = Objects.equals(existing.getParentId(), parentId)
                && Objects.equals(existing.getAccountCategory(), requested.getAccountCategory());
        if (!historicalRelationshipUnchanged
                && !Objects.equals(parent.getAccountCategory(), requested.getAccountCategory())) {
            throw new BusinessException("ACCOUNT_CATEGORY_PARENT_MISMATCH", "子科目分类必须与父科目一致");
        }
        requested.setLevel(parent.getLevel() + 1);
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
        vo.setDefaultTargetRatio(subject.getDefaultTargetRatio());
        vo.setLedgerFlag(subject.getLedgerFlag());
        vo.setCreatedBy(subject.getCreatedBy() != null ? subject.getCreatedBy().toString() : null);
        vo.setCreatedAt(subject.getCreatedAt() != null ? DateTimeUtils.DTF.format(subject.getCreatedAt()) : null);
        vo.setUpdatedAt(subject.getUpdatedAt() != null ? DateTimeUtils.DTF.format(subject.getUpdatedAt()) : null);
        vo.setRemark(subject.getRemark());
        return vo;
    }

    public record TargetRatio(String subjectCode, BigDecimal ratio) {
    }
}
