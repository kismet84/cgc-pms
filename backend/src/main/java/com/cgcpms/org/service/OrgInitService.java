package com.cgcpms.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.org.entity.OrgCompany;
import com.cgcpms.org.entity.OrgDepartment;
import com.cgcpms.org.mapper.OrgCompanyMapper;
import com.cgcpms.org.mapper.OrgDepartmentMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-shot service that creates a root OrgCompany + root OrgDepartment per tenant,
 * then backfills pm_project.org_id and ct_contract.org_id where they are NULL.
 * Safe to call multiple times — idempotent for both org creation and backfill.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgInitService {

    private static final String ROOT_COMPANY_CODE = "ROOT";
    private static final String ROOT_COMPANY_NAME = "默认公司";
    private static final String ROOT_DEPT_CODE = "ROOT_DEPT";
    private static final String ROOT_DEPT_NAME = "默认部门";

    private final OrgCompanyMapper orgCompanyMapper;
    private final OrgDepartmentMapper orgDepartmentMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    @Lazy
    @Autowired
    private OrgInitService self;

    /**
     * For every tenant that has at least one pm_project or ct_contract with a
     * NULL org_id: create the root company/department if missing, then set the
     * NULL org_id rows to the root company id.
     * <p>
     * 每个租户独立事务：通过 self 代理调用 backfillTenant，
     * 确保 REQUIRES_NEW 传播级别生效，一个租户的失败不会回滚其他已完成的租户。
     */
    public void initOrgAndBackfill() {
        Set<Long> tenantIds = collectTenantsNeedingBackfill();

        if (CollectionUtils.isEmpty(tenantIds)) {
            log.info("No tenants need org backfill — all org_id values are already set");
            return;
        }

        log.info("Found {} tenant(s) needing org backfill: {}", tenantIds.size(), tenantIds);

        for (Long tenantId : tenantIds) {
            self.backfillTenant(tenantId);
        }
    }

    // ── tenant discovery ──────────────────────────────────────────

    private Set<Long> collectTenantsNeedingBackfill() {
        Set<Long> tenantIds = new HashSet<>();

        List<PmProject> projectsWithNullOrg = pmProjectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .select(PmProject::getTenantId)
                        .isNull(PmProject::getOrgId)
        );
        tenantIds.addAll(projectsWithNullOrg.stream()
                .map(PmProject::getTenantId)
                .collect(Collectors.toSet()));

        List<CtContract> contractsWithNullOrg = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .select(CtContract::getTenantId)
                        .isNull(CtContract::getOrgId)
        );
        tenantIds.addAll(contractsWithNullOrg.stream()
                .map(CtContract::getTenantId)
                .collect(Collectors.toSet()));

        return tenantIds;
    }

    // ── per-tenant backfill ───────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void backfillTenant(Long tenantId) {
        OrgCompany rootCompany = getOrCreateRootCompany(tenantId);
        getOrCreateRootDepartment(tenantId, rootCompany.getId());

        int projectCount = backfillProjects(tenantId, rootCompany.getId());
        int contractCount = backfillContracts(tenantId, rootCompany.getId());

        log.info("Tenant {} backfill complete: rootCompany={}, projects updated={}, contracts updated={}",
                tenantId, rootCompany.getId(), projectCount, contractCount);
    }

    // ── org creation (idempotent) ─────────────────────────────────

    private OrgCompany getOrCreateRootCompany(Long tenantId) {
        List<OrgCompany> existing = orgCompanyMapper.selectList(
                new LambdaQueryWrapper<OrgCompany>()
                        .eq(OrgCompany::getTenantId, tenantId)
                        .eq(OrgCompany::getCompanyCode, ROOT_COMPANY_CODE)
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        OrgCompany company = new OrgCompany();
        company.setTenantId(tenantId);
        company.setCompanyCode(ROOT_COMPANY_CODE);
        company.setCompanyName(ROOT_COMPANY_NAME);
        company.setStatus("ENABLE");
        orgCompanyMapper.insert(company);
        log.info("Created root company for tenant {}: id={}", tenantId, company.getId());
        return company;
    }

    private OrgDepartment getOrCreateRootDepartment(Long tenantId, Long companyId) {
        List<OrgDepartment> existing = orgDepartmentMapper.selectList(
                new LambdaQueryWrapper<OrgDepartment>()
                        .eq(OrgDepartment::getTenantId, tenantId)
                        .eq(OrgDepartment::getDeptCode, ROOT_DEPT_CODE)
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        OrgDepartment dept = new OrgDepartment();
        dept.setTenantId(tenantId);
        dept.setCompanyId(companyId);
        dept.setParentId(null);
        dept.setDeptCode(ROOT_DEPT_CODE);
        dept.setDeptName(ROOT_DEPT_NAME);
        dept.setOrderNum(0);
        dept.setStatus("ENABLE");
        orgDepartmentMapper.insert(dept);
        log.info("Created root department for tenant {}: id={}", tenantId, dept.getId());
        return dept;
    }

    // ── null-orgId backfill (idempotent) ──────────────────────────

    private int backfillProjects(Long tenantId, Long rootCompanyId) {
        LambdaUpdateWrapper<PmProject> wrapper = new LambdaUpdateWrapper<PmProject>()
                .set(PmProject::getOrgId, rootCompanyId)
                .eq(PmProject::getTenantId, tenantId)
                .isNull(PmProject::getOrgId);
        return pmProjectMapper.update(null, wrapper);
    }

    private int backfillContracts(Long tenantId, Long rootCompanyId) {
        LambdaUpdateWrapper<CtContract> wrapper = new LambdaUpdateWrapper<CtContract>()
                .set(CtContract::getOrgId, rootCompanyId)
                .eq(CtContract::getTenantId, tenantId)
                .isNull(CtContract::getOrgId);
        return ctContractMapper.update(null, wrapper);
    }
}
