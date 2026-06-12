package com.cgcpms.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.org.entity.OrgCompany;
import com.cgcpms.org.entity.OrgDepartment;
import com.cgcpms.org.mapper.OrgCompanyMapper;
import com.cgcpms.org.mapper.OrgDepartmentMapper;
import com.cgcpms.org.service.OrgInitService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrgInitServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TEST_TENANT = 999L;

    @Autowired
    private OrgInitService orgInitService;

    @Autowired
    private OrgCompanyMapper orgCompanyMapper;

    @Autowired
    private OrgDepartmentMapper orgDepartmentMapper;

    @Autowired
    private PmProjectMapper pmProjectMapper;

    @Autowired
    private CtContractMapper ctContractMapper;

    private Long projectWithNullOrgId;
    private Long contractWithNullOrgId;
    private Long projectWithOrgIdAlreadySet;

    @BeforeEach
    void setupContext() {
        // Use test tenant to avoid interfering with seed data (tenant 0)
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TEST_TENANT)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // Helper: create a PmProject row directly via mapper
    // ═══════════════════════════════════════════════════════════
    private PmProject createProject(Long tenantId, Long orgId, String code, String name) {
        PmProject p = new PmProject();
        p.setTenantId(tenantId);
        p.setOrgId(orgId);
        p.setProjectCode(code);
        p.setProjectName(name);
        p.setProjectType("施工总承包");
        p.setContractAmount(new BigDecimal("1000000.00"));
        p.setTargetCost(new BigDecimal("900000.00"));
        p.setPlannedStartDate(LocalDate.of(2026, 1, 1));
        p.setPlannedEndDate(LocalDate.of(2026, 12, 31));
        p.setStatus("进行中");
        p.setApprovalStatus("已批准");
        pmProjectMapper.insert(p);
        return p;
    }

    // ═══════════════════════════════════════════════════════════
    // Helper: create a CtContract row directly via mapper
    // ═══════════════════════════════════════════════════════════
    private CtContract createContract(Long tenantId, Long orgId, Long projectId, String code, String name) {
        CtContract c = new CtContract();
        c.setTenantId(tenantId);
        c.setOrgId(orgId);
        c.setProjectId(projectId);
        c.setContractCode(code);
        c.setContractName(name);
        c.setContractType("MAIN");
        c.setContractAmount(new BigDecimal("500000.00"));
        c.setCurrentAmount(new BigDecimal("500000.00"));
        c.setContractStatus("DRAFT");
        c.setApprovalStatus("DRAFT");
        ctContractMapper.insert(c);
        return c;
    }

    // ═══════════════════════════════════════════════════════════
    // TC1: Full backfill — creates root org, backfills NULL orgId
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(1)
    @DisplayName("TC1: Backfill creates ROOT company+dept and sets NULL orgId on project & contract")
    void test01_fullBackfill() {
        // Seed: project with NULL orgId
        PmProject p1 = createProject(TEST_TENANT, null, "PRJ-BF-001", "回填测试项目1");
        projectWithNullOrgId = p1.getId();

        // Seed: contract with NULL orgId
        CtContract c1 = createContract(TEST_TENANT, null, p1.getId(), "CT-BF-001", "回填测试合同1");
        contractWithNullOrgId = c1.getId();

        // Seed: project with orgId ALREADY SET (should NOT be overwritten)
        PmProject p2 = createProject(TEST_TENANT, 888888L, "PRJ-BF-002", "回填测试项目2-已有orgId");
        projectWithOrgIdAlreadySet = p2.getId();

        // ── Execute backfill ──
        orgInitService.initOrgAndBackfill();

        // ── Verify root company was created ──
        List<OrgCompany> companies = orgCompanyMapper.selectList(
                new LambdaQueryWrapper<OrgCompany>()
                        .eq(OrgCompany::getTenantId, TEST_TENANT)
                        .eq(OrgCompany::getCompanyCode, "ROOT")
        );
        assertEquals(1, companies.size(), "应恰好创建一个ROOT公司");
        OrgCompany rootCompany = companies.get(0);
        assertEquals("默认公司", rootCompany.getCompanyName());
        assertEquals("ENABLE", rootCompany.getStatus());

        // ── Verify root department was created ──
        List<OrgDepartment> departments = orgDepartmentMapper.selectList(
                new LambdaQueryWrapper<OrgDepartment>()
                        .eq(OrgDepartment::getTenantId, TEST_TENANT)
                        .eq(OrgDepartment::getDeptCode, "ROOT_DEPT")
        );
        assertEquals(1, departments.size(), "应恰好创建一个ROOT_DEPT部门");
        OrgDepartment rootDept = departments.get(0);
        assertEquals("默认部门", rootDept.getDeptName());
        assertEquals(rootCompany.getId(), rootDept.getCompanyId());
        assertNull(rootDept.getParentId(), "根部门parentId应为null");

        // ── Verify NULL orgId project was backfilled ──
        PmProject backfilledProject = pmProjectMapper.selectById(projectWithNullOrgId);
        assertNotNull(backfilledProject);
        assertEquals(rootCompany.getId(), backfilledProject.getOrgId(),
                "NULL orgId 的项目应被回填为ROOT公司ID");

        // ── Verify NULL orgId contract was backfilled ──
        CtContract backfilledContract = ctContractMapper.selectById(contractWithNullOrgId);
        assertNotNull(backfilledContract);
        assertEquals(rootCompany.getId(), backfilledContract.getOrgId(),
                "NULL orgId 的合同应被回填为ROOT公司ID");

        // ── Verify already-set orgId was NOT overwritten ──
        PmProject untouchedProject = pmProjectMapper.selectById(projectWithOrgIdAlreadySet);
        assertNotNull(untouchedProject);
        assertEquals(Long.valueOf(888888L), untouchedProject.getOrgId(),
                "已有orgId的项目不应被覆盖");

        System.out.println("✅ TC1 通过: rootCompany=" + rootCompany.getId()
                + ", projectBackfilled=" + backfilledProject.getOrgId()
                + ", contractBackfilled=" + backfilledContract.getOrgId()
                + ", untouchedProject orgId=" + untouchedProject.getOrgId());
    }

    // ═══════════════════════════════════════════════════════════
    // TC2: Idempotency — second call does not create duplicates
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(2)
    @DisplayName("TC2: 第二次调用backfill应幂等，不创建重复公司/部门")
    void test02_idempotentBackfill() {
        // Seed data for a fresh tenant
        PmProject p = createProject(998L, null, "PRJ-IDM-001", "幂等测试项目");
        createContract(998L, null, p.getId(), "CT-IDM-001", "幂等测试合同");

        // First call
        orgInitService.initOrgAndBackfill();

        // Count companies and departments after first call
        long companyCountAfterFirst = orgCompanyMapper.selectCount(
                new LambdaQueryWrapper<OrgCompany>().eq(OrgCompany::getTenantId, 998L));
        long deptCountAfterFirst = orgDepartmentMapper.selectCount(
                new LambdaQueryWrapper<OrgDepartment>().eq(OrgDepartment::getTenantId, 998L));

        // Second call — should be idempotent
        orgInitService.initOrgAndBackfill();

        // Count companies and departments after second call — should be unchanged
        long companyCountAfterSecond = orgCompanyMapper.selectCount(
                new LambdaQueryWrapper<OrgCompany>().eq(OrgCompany::getTenantId, 998L));
        long deptCountAfterSecond = orgDepartmentMapper.selectCount(
                new LambdaQueryWrapper<OrgDepartment>().eq(OrgDepartment::getTenantId, 998L));

        assertEquals(companyCountAfterFirst, companyCountAfterSecond,
                "第二次调用不应创建重复公司");
        assertEquals(deptCountAfterFirst, deptCountAfterSecond,
                "第二次调用不应创建重复部门");

        // Verify the project still has the correct orgId
        PmProject reloaded = pmProjectMapper.selectById(p.getId());
        assertNotNull(reloaded.getOrgId(), "orgId应保持已设置状态");

        System.out.println("✅ TC2 通过: 幂等验证 — 公司数=" + companyCountAfterSecond
                + ", 部门数=" + deptCountAfterSecond);
    }

    // ═══════════════════════════════════════════════════════════
    // TC3: No-op when all orgId values are already set
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(3)
    @DisplayName("TC3: 所有orgId已设置时，backfill不创建额外组织")
    void test03_noOpWhenAllSet() {
        // Create a project and contract with orgId already set
        PmProject p = createProject(997L, 777777L, "PRJ-NOOP-001", "无操作测试项目");
        createContract(997L, 777777L, p.getId(), "CT-NOOP-001", "无操作测试合同");

        long companyCountBefore = orgCompanyMapper.selectCount(
                new LambdaQueryWrapper<OrgCompany>().eq(OrgCompany::getTenantId, 997L));
        long deptCountBefore = orgDepartmentMapper.selectCount(
                new LambdaQueryWrapper<OrgDepartment>().eq(OrgDepartment::getTenantId, 997L));

        // Call backfill — should be a no-op for org creation
        orgInitService.initOrgAndBackfill();

        long companyCountAfter = orgCompanyMapper.selectCount(
                new LambdaQueryWrapper<OrgCompany>().eq(OrgCompany::getTenantId, 997L));
        long deptCountAfter = orgDepartmentMapper.selectCount(
                new LambdaQueryWrapper<OrgDepartment>().eq(OrgDepartment::getTenantId, 997L));

        assertEquals(companyCountBefore, companyCountAfter,
                "所有orgId已设置时不应创建公司");
        assertEquals(deptCountBefore, deptCountAfter,
                "所有orgId已设置时不应创建部门");

        // orgId should remain unchanged
        PmProject reloaded = pmProjectMapper.selectById(p.getId());
        assertEquals(Long.valueOf(777777L), reloaded.getOrgId(), "已有orgId不应被修改");

        System.out.println("✅ TC3 通过: 无NULL orgId时backfill为no-op");
    }
}
