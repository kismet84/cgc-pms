package com.cgcpms.project;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.service.PmProjectMemberService;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.project.vo.PmProjectMemberVO;
import com.cgcpms.project.vo.PmProjectVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectMemberServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private PmProjectService projectService;

    @Autowired
    private PmProjectMemberService memberService;

    private Long testProjectId;
    private Long createdMemberId;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    /**
     * Helper: create a test project.
     */
    private Long createTestProject() {
        PmProject project = new PmProject();
        project.setTenantId(TENANT_0);
        project.setProjectCode("TST-MEMBER-" + System.currentTimeMillis());
        project.setProjectName("成员测试项目");
        project.setProjectType("BUILDING");
        project.setStatus("DRAFT");
        project.setContractAmount(new BigDecimal("1000000.00"));
        return projectService.create(project);
    }

    // ═══════════════════════════════════════════════════════════
    // TC1-TC2: Create + duplicate check
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @Transactional
    @DisplayName("TC1: 创建项目成员 → 验证雪花ID生成 + 审计字段自动填充 + 默认状态ACTIVE")
    void test01_createMember() {
        testProjectId = createTestProject();

        PmProjectMember member = new PmProjectMember();
        member.setUserId(1001L);
        member.setRoleCode("PM");
        member.setPositionName("项目经理");

        Long id = memberService.create(testProjectId, member);
        assertNotNull(id, "成员ID不应为空");
        createdMemberId = id;

        PmProjectMemberVO vo = memberService.getById(testProjectId, id);
        assertNotNull(vo, "应能查询到刚创建的成员");
        assertEquals(testProjectId.toString(), vo.getProjectId());
        assertEquals("1001", vo.getUserId());
        assertEquals("PM", vo.getRoleCode());
        assertEquals("项目经理", vo.getPositionName());
        assertEquals("ACTIVE", vo.getStatus(), "未指定status时应默认为ACTIVE");
        assertNotNull(vo.getId(), "VO的ID应为String类型");
        assertNotNull(vo.getCreatedAt(), "创建时间应自动填充");
        assertNotNull(vo.getUpdatedAt(), "更新时间应自动填充");

        System.out.println("✅ TC1 通过: memberId=" + vo.getId() + ", roleCode=" + vo.getRoleCode() + ", status=" + vo.getStatus());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("TC2: 重复成员校验 → 同一项目+同一用户不可重复")
    void test02_duplicateMemberCheck() {
        testProjectId = createTestProject();

        // First member
        PmProjectMember m1 = new PmProjectMember();
        m1.setUserId(1002L);
        m1.setRoleCode("CM");
        m1.setPositionName("商务经理");
        Long id1 = memberService.create(testProjectId, m1);
        assertNotNull(id1);

        // Second member with same userId in same project
        PmProjectMember m2 = new PmProjectMember();
        m2.setUserId(1002L);
        m2.setRoleCode("CSTM");
        m2.setPositionName("成本经理");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> memberService.create(testProjectId, m2),
                "同一项目重复添加同一用户应抛出BusinessException");
        assertEquals("MEMBER_ALREADY_EXISTS", ex.getCode());

        System.out.println("✅ TC2 通过: 重复成员正确拦截, code=" + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // TC3: List with filters
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @Transactional
    @DisplayName("TC3: 分页列表 → tenantId + projectId 过滤 + roleCode/status筛选")
    void test03_listMembers() {
        testProjectId = createTestProject();

        // Create 3 members
        for (int i = 0; i < 3; i++) {
            PmProjectMember m = new PmProjectMember();
            m.setUserId(1100L + i);
            m.setRoleCode(i == 0 ? "PM" : "CM");
            m.setPositionName("成员" + (i + 1));
            m.setStatus(i < 2 ? "ACTIVE" : "INACTIVE");
            memberService.create(testProjectId, m);
        }

        // Page 1: no filter
        IPage<PmProjectMemberVO> page1 = memberService.getPage(testProjectId, 1, 20, null, null);
        assertTrue(page1.getTotal() >= 3, "应至少有3条记录");

        // Page 2: filter by roleCode
        IPage<PmProjectMemberVO> page2 = memberService.getPage(testProjectId, 1, 20, "PM", null);
        assertTrue(page2.getTotal() >= 1, "按PM角色筛选应有结果");

        // Page 3: filter by status
        IPage<PmProjectMemberVO> page3 = memberService.getPage(testProjectId, 1, 20, null, "ACTIVE");
        assertTrue(page3.getTotal() >= 2, "按ACTIVE状态筛选应有至少2条");

        // Page 4: filter by roleCode + status
        IPage<PmProjectMemberVO> page4 = memberService.getPage(testProjectId, 1, 20, "CM", "ACTIVE");
        assertTrue(page4.getTotal() >= 1, "按CM+ACTIVE筛选应有结果");

        System.out.println("✅ TC3 通过: total=" + page1.getTotal()
                + ", PM=" + page2.getTotal() + ", ACTIVE=" + page3.getTotal() + ", CM+ACTIVE=" + page4.getTotal());
    }

    // ═══════════════════════════════════════════════════════════
    // TC4-TC5: Update + Delete
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @Transactional
    @DisplayName("TC4: 更新成员 → roleCode/positionName/status可更新")
    void test04_updateMember() {
        testProjectId = createTestProject();

        PmProjectMember member = new PmProjectMember();
        member.setUserId(1003L);
        member.setRoleCode("PM");
        member.setPositionName("原始职位");
        member.setStatus("ACTIVE");
        Long id = memberService.create(testProjectId, member);

        PmProjectMember update = new PmProjectMember();
        update.setUserId(1003L);
        update.setRoleCode("CSTM");
        update.setPositionName("更新后职位");
        update.setStatus("INACTIVE");
        memberService.update(testProjectId, id, update);

        PmProjectMemberVO vo = memberService.getById(testProjectId, id);
        assertEquals("CSTM", vo.getRoleCode());
        assertEquals("更新后职位", vo.getPositionName());
        assertEquals("INACTIVE", vo.getStatus());

        System.out.println("✅ TC4 通过: roleCode=" + vo.getRoleCode() + ", status=" + vo.getStatus());
    }

    @Test
    @Order(5)
    @Transactional
    @DisplayName("TC5: 删除成员 → tenantId+projectId校验后逻辑删除")
    void test05_deleteMember() {
        testProjectId = createTestProject();

        PmProjectMember member = new PmProjectMember();
        member.setUserId(1004L);
        member.setRoleCode("FIN");
        member.setPositionName("财务");
        Long id = memberService.create(testProjectId, member);

        assertDoesNotThrow(() -> memberService.delete(testProjectId, id), "删除不应抛异常");

        // Verify deleted (logic delete via BaseEntity)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> memberService.getById(testProjectId, id),
                "逻辑删除后查询应抛出BusinessException");
        assertEquals("MEMBER_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC5 通过: 逻辑删除成功, 后续查询抛出MEMBER_NOT_FOUND");
    }

    // ═══════════════════════════════════════════════════════════
    // TC6-TC7: Tenant isolation
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @Transactional
    @DisplayName("TC6: 跨租户隔离 → 不同tenantId无法查询")
    void test06_crossTenantIsolation() {
        testProjectId = createTestProject();

        PmProjectMember member = new PmProjectMember();
        member.setUserId(1005L);
        member.setRoleCode("PM");
        member.setPositionName("隔离测试成员");
        Long id = memberService.create(testProjectId, member);

        // Switch to another tenant
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        // Attempt to get member from wrong tenant → should fail at project ownership check
        assertThrows(BusinessException.class,
                () -> memberService.getById(testProjectId, id),
                "不同租户查询应抛出BusinessException (project ownership)");

        // Attempt to list members
        assertThrows(BusinessException.class,
                () -> memberService.getPage(testProjectId, 1, 20, null, null),
                "不同租户list也应失败");

        System.out.println("✅ TC6 通过: 跨租户隔离正确, 租户999无法访问租户0的数据");
    }

    // ═══════════════════════════════════════════════════════════
    // TC8-TC9: Project ownership checks
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @Transactional
    @DisplayName("TC7: 跨项目隔离 → 通过projectA的路径访问projectB的成员应失败")
    void test07_crossProjectIsolation() {
        // Create two projects
        testProjectId = createTestProject();

        PmProject project2 = new PmProject();
        project2.setTenantId(TENANT_0);
        project2.setProjectCode("TST-MEMBER-B-" + System.currentTimeMillis());
        project2.setProjectName("测试项目B");
        project2.setProjectType("BUILDING");
        project2.setStatus("DRAFT");
        project2.setContractAmount(new BigDecimal("1000000.00"));
        Long projectBId = projectService.create(project2);

        // Create member in project A
        PmProjectMember member = new PmProjectMember();
        member.setUserId(1006L);
        member.setRoleCode("PM");
        member.setPositionName("项目A成员");
        Long memberId = memberService.create(testProjectId, member);

        // Try to access member from project B path
        BusinessException ex = assertThrows(BusinessException.class,
                () -> memberService.getById(projectBId, memberId),
                "通过项目B的路径访问项目A的成员应失败");
        assertEquals("MEMBER_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC7 通过: 跨项目隔离正确, 项目B无法访问项目A的成员");
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("TC8: 不存在的项目 → 操作应失败")
    void test08_nonExistentProject() {
        long fakeProjectId = 99999999L;

        // List should fail
        assertThrows(BusinessException.class,
                () -> memberService.getPage(fakeProjectId, 1, 20, null, null),
                "不存在的项目list应失败");

        // Create should fail
        PmProjectMember member = new PmProjectMember();
        member.setUserId(1007L);
        member.setRoleCode("PM");
        assertThrows(BusinessException.class,
                () -> memberService.create(fakeProjectId, member),
                "不存在的项目create应失败");

        System.out.println("✅ TC8 通过: 不存在的项目操作正确拦截");
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("TC9: 查询不存在的成员 → 统一抛出BusinessException")
    void test09_notFound() {
        testProjectId = createTestProject();

        assertThrows(BusinessException.class,
                () -> memberService.getById(testProjectId, 99999999L),
                "不存在的成员应抛出BusinessException");

        assertThrows(BusinessException.class,
                () -> memberService.delete(testProjectId, 99999999L),
                "删除不存在的成员应抛出BusinessException");

        System.out.println("✅ TC9 通过: 不存在的实体统一抛出BusinessException");
    }

    // ═══════════════════════════════════════════════════════════
    // TC10: Full CRUD lifecycle
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @Transactional
    @DisplayName("TC10: 完整CRUD生命周期 → 创建→查询→更新→删除→确认删除")
    void test10_fullLifecycle() {
        testProjectId = createTestProject();

        // Create
        PmProjectMember member = new PmProjectMember();
        member.setUserId(1008L);
        member.setRoleCode("SUBC");
        member.setPositionName("分包管理员");
        Long id = memberService.create(testProjectId, member);
        createdMemberId = id;

        // Read
        PmProjectMemberVO vo1 = memberService.getById(testProjectId, id);
        assertEquals("SUBC", vo1.getRoleCode());
        assertEquals("ACTIVE", vo1.getStatus());

        // Update
        PmProjectMember update = new PmProjectMember();
        update.setUserId(1008L);
        update.setRoleCode("MAT");
        update.setPositionName("材料员");
        update.setStatus("ACTIVE");
        memberService.update(testProjectId, id, update);

        PmProjectMemberVO vo2 = memberService.getById(testProjectId, id);
        assertEquals("MAT", vo2.getRoleCode());
        assertEquals("材料员", vo2.getPositionName());

        // Delete
        memberService.delete(testProjectId, id);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> memberService.getById(testProjectId, id));
        assertEquals("MEMBER_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC10 通过: 完整CRUD生命周期验证通过 SUBC→MAT→DELETE");
    }
}
