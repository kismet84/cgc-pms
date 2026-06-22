package com.cgcpms.org;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.org.entity.OrgCompany;
import com.cgcpms.org.entity.OrgDepartment;
import com.cgcpms.org.entity.OrgPosition;
import com.cgcpms.org.service.OrgCompanyService;
import com.cgcpms.org.service.OrgDepartmentService;
import com.cgcpms.org.service.OrgPositionService;
import com.cgcpms.org.vo.OrgCompanyVO;
import com.cgcpms.org.vo.OrgDepartmentTreeNodeVO;
import com.cgcpms.org.vo.OrgDepartmentVO;
import com.cgcpms.org.vo.OrgPositionVO;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrgServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private OrgCompanyService orgCompanyService;

    @Autowired
    private OrgDepartmentService orgDepartmentService;

    @Autowired
    private OrgPositionService orgPositionService;

    private Long createdCompanyId;
    private Long createdParentDeptId;
    private Long createdChildDeptId;
    private Long createdPositionId;

    /** Position test seed IDs (created in @BeforeEach) */
    private Long posTestCompanyId;
    private Long posTestDepartmentId;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .build());
        seedPositionTestRefs();
    }

    /** Seed company + department needed for OrgPosition CRUD test fixtures. */
    private void seedPositionTestRefs() {
        if (posTestCompanyId == null) {
            OrgCompany company = new OrgCompany();
            company.setCompanyCode("POS-TEST-COMP");
            company.setCompanyName("岗位测试公司");
            company.setStatus("ENABLE");
            posTestCompanyId = orgCompanyService.create(company);
        }
        if (posTestDepartmentId == null) {
            OrgDepartment dept = new OrgDepartment();
            dept.setCompanyId(posTestCompanyId);
            dept.setDeptCode("POS-TEST-DEPT");
            dept.setDeptName("岗位测试部门");
            dept.setStatus("ENABLE");
            dept.setParentId(0L);
            posTestDepartmentId = orgDepartmentService.create(dept);
        }
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // OrgCompany CRUD tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @Transactional
    @DisplayName("TC1: OrgCompany创建 → 验证雪花ID生成 + 审计字段自动填充")
    void test01_createCompany() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-001");
        company.setCompanyName("测试建筑公司");
        company.setStatus("ENABLE");

        Long id = orgCompanyService.create(company);
        assertNotNull(id, "公司ID不应为空");
        createdCompanyId = id;

        OrgCompanyVO vo = orgCompanyService.getById(id);
        assertNotNull(vo, "应能查询到刚创建的公司");
        assertEquals("COMP-001", vo.getCompanyCode());
        assertEquals("测试建筑公司", vo.getCompanyName());
        assertEquals("ENABLE", vo.getStatus());
        assertNotNull(vo.getId(), "VO的ID应为String类型");
        assertNotNull(vo.getCreatedAt(), "创建时间应自动填充");
        assertNotNull(vo.getUpdatedAt(), "更新时间应自动填充");

        System.out.println("✅ TC1 通过: companyId=" + vo.getId() + ", companyName=" + vo.getCompanyName());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("TC2: OrgCompany编码重复校验 → 相同tenant下不可重复")
    void test02_companyCodeDuplicate() {
        OrgCompany company1 = new OrgCompany();
        company1.setCompanyCode("COMP-DUP");
        company1.setCompanyName("重复测试公司1");
        Long id1 = orgCompanyService.create(company1);
        assertNotNull(id1);

        OrgCompany company2 = new OrgCompany();
        company2.setCompanyCode("COMP-DUP");
        company2.setCompanyName("重复测试公司2");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgCompanyService.create(company2),
                "重复编码应抛出BusinessException");
        assertEquals("ORG_COMPANY_CODE_EXISTS", ex.getCode());

        System.out.println("✅ TC2 通过: 编码重复正确拦截, code=" + ex.getCode());
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("TC3: OrgCompany分页列表 → tenantId过滤 + 模糊搜索")
    void test03_companyList() {
        // Create a company first
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-LIST");
        company.setCompanyName("列表测试公司");
        company.setStatus("ENABLE");
        orgCompanyService.create(company);

        // List with no filters
        IPage<OrgCompanyVO> page1 = orgCompanyService.getPage(1, 20, null, null, null);
        assertTrue(page1.getTotal() >= 1, "应至少有一条记录");

        // Filter by name
        IPage<OrgCompanyVO> page2 = orgCompanyService.getPage(1, 20, null, "列表", null);
        assertTrue(page2.getTotal() >= 1, "应按名称模糊匹配到记录");

        // Filter by code
        IPage<OrgCompanyVO> page3 = orgCompanyService.getPage(1, 20, "COMP-LIST", null, null);
        assertTrue(page3.getTotal() >= 1, "应按编码模糊匹配到记录");

        System.out.println("✅ TC3 通过: total=" + page1.getTotal()
                + ", nameFilter=" + page2.getTotal() + ", codeFilter=" + page3.getTotal());
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("TC4: OrgCompany更新 → tenantId校验后更新")
    void test04_companyUpdate() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-UPD");
        company.setCompanyName("原始名称");
        company.setStatus("ENABLE");
        Long id = orgCompanyService.create(company);

        OrgCompany update = new OrgCompany();
        update.setId(id);
        update.setCompanyName("更新后名称");
        update.setCompanyCode("COMP-UPD");
        update.setStatus("DISABLE");
        orgCompanyService.update(update);

        OrgCompanyVO vo = orgCompanyService.getById(id);
        assertEquals("更新后名称", vo.getCompanyName());
        assertEquals("DISABLE", vo.getStatus());

        System.out.println("✅ TC4 通过: companyName=" + vo.getCompanyName() + ", status=" + vo.getStatus());
    }

    @Test
    @Order(5)
    @Transactional
    @DisplayName("TC5: OrgCompany删除 → tenantId校验后逻辑删除")
    void test05_companyDelete() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-DEL");
        company.setCompanyName("待删除公司");
        company.setStatus("ENABLE");
        Long id = orgCompanyService.create(company);

        assertDoesNotThrow(() -> orgCompanyService.delete(id), "删除不应抛异常");

        // Verify deleted (logic delete via BaseEntity)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgCompanyService.getById(id),
                "逻辑删除后查询应抛出BusinessException");
        assertEquals("ORG_COMPANY_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC5 通过: 逻辑删除成功, 后续查询抛出ORG_COMPANY_NOT_FOUND");
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("TC6: OrgCompany跨租户隔离 → 不同tenantId无法查询")
    void test06_companyCrossTenantIsolation() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-XTNT");
        company.setCompanyName("跨租户测试");
        Long id = orgCompanyService.create(company);

        // Switch to another tenant
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgCompanyService.getById(id),
                "不同租户查询应抛出BusinessException");
        assertEquals("ORG_COMPANY_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC6 通过: 跨租户隔离正确, 租户999无法访问租户0的数据");
    }

    // ═══════════════════════════════════════════════════════════
    // OrgDepartment CRUD + Tree tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @Transactional
    @DisplayName("TC7: OrgDepartment创建根部门 → parentId=null为根节点")
    void test07_createRootDepartment() {
        // First create a company
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-DEPT");
        company.setCompanyName("部门测试公司");
        Long companyId = orgCompanyService.create(company);

        OrgDepartment dept = new OrgDepartment();
        dept.setCompanyId(companyId);
        dept.setParentId(null);
        dept.setDeptCode("DEPT-ROOT");
        dept.setDeptName("根部门");
        dept.setOrderNum(1);
        dept.setStatus("ENABLE");

        Long id = orgDepartmentService.create(dept);
        assertNotNull(id, "部门ID不应为空");
        createdParentDeptId = id;

        OrgDepartmentVO vo = orgDepartmentService.getById(id);
        assertNotNull(vo);
        assertEquals("DEPT-ROOT", vo.getDeptCode());
        assertEquals("根部门", vo.getDeptName());
        assertEquals("0", vo.getParentId(), "根部门parentId应为'0'");

        System.out.println("✅ TC7 通过: deptId=" + vo.getId() + ", deptName=" + vo.getDeptName()
                + ", parentId=" + vo.getParentId());
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("TC8: OrgDepartment创建子部门 + 树结构验证")
    void test08_createChildDeptAndTree() {
        // Create company
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-TREE");
        company.setCompanyName("树测试公司");
        Long companyId = orgCompanyService.create(company);

        // Create root dept
        OrgDepartment root = new OrgDepartment();
        root.setCompanyId(companyId);
        root.setDeptCode("DEPT-R");
        root.setDeptName("总部");
        root.setOrderNum(1);
        root.setStatus("ENABLE");
        Long rootId = orgDepartmentService.create(root);

        // Create child dept
        OrgDepartment child = new OrgDepartment();
        child.setCompanyId(companyId);
        child.setParentId(rootId);
        child.setDeptCode("DEPT-C1");
        child.setDeptName("技术部");
        child.setOrderNum(1);
        child.setStatus("ENABLE");
        Long childId = orgDepartmentService.create(child);
        createdChildDeptId = childId;

        // Create another child
        OrgDepartment child2 = new OrgDepartment();
        child2.setCompanyId(companyId);
        child2.setParentId(rootId);
        child2.setDeptCode("DEPT-C2");
        child2.setDeptName("财务部");
        child2.setOrderNum(2);
        child2.setStatus("ENABLE");
        orgDepartmentService.create(child2);

        // Get tree
        List<OrgDepartmentTreeNodeVO> tree = orgDepartmentService.getTree();
        assertNotNull(tree);
        assertFalse(tree.isEmpty(), "树不应为空");

        // Find our root in the tree
        boolean foundRoot = tree.stream()
                .anyMatch(n -> n.getDeptCode().equals("DEPT-R") && n.getChildren().size() >= 2);
        assertTrue(foundRoot, "树中应包含根部门及其2个子节点");

        System.out.println("✅ TC8 通过: tree size=" + tree.size() + ", root with children found=" + foundRoot);
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("TC9: OrgDepartment树结构递归验证 → 多层级嵌套")
    void test09_deepTreeStructure() {
        // Create company
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-DEEP");
        company.setCompanyName("深层树测试公司");
        Long companyId = orgCompanyService.create(company);

        // Level 1
        OrgDepartment l1 = new OrgDepartment();
        l1.setCompanyId(companyId);
        l1.setDeptCode("L1");
        l1.setDeptName("一级部门");
        l1.setOrderNum(1);
        Long l1Id = orgDepartmentService.create(l1);

        // Level 2
        OrgDepartment l2 = new OrgDepartment();
        l2.setCompanyId(companyId);
        l2.setParentId(l1Id);
        l2.setDeptCode("L2");
        l2.setDeptName("二级部门");
        l2.setOrderNum(1);
        Long l2Id = orgDepartmentService.create(l2);

        // Level 3
        OrgDepartment l3 = new OrgDepartment();
        l3.setCompanyId(companyId);
        l3.setParentId(l2Id);
        l3.setDeptCode("L3");
        l3.setDeptName("三级部门");
        l3.setOrderNum(1);
        orgDepartmentService.create(l3);

        // Get tree and verify depth
        List<OrgDepartmentTreeNodeVO> tree = orgDepartmentService.getTree();
        // Find L1 node
        OrgDepartmentTreeNodeVO l1Node = tree.stream()
                .filter(n -> "L1".equals(n.getDeptCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(l1Node, "应找到L1节点");
        assertEquals(1, l1Node.getChildren().size(), "L1应有1个子节点");

        OrgDepartmentTreeNodeVO l2Node = l1Node.getChildren().get(0);
        assertEquals("L2", l2Node.getDeptCode(), "L1的子节点应为L2");
        assertEquals(1, l2Node.getChildren().size(), "L2应有1个子节点");

        OrgDepartmentTreeNodeVO l3Node = l2Node.getChildren().get(0);
        assertEquals("L3", l3Node.getDeptCode(), "L2的子节点应为L3");
        assertTrue(l3Node.getChildren().isEmpty(), "L3应无子节点");

        System.out.println("✅ TC9 通过: 3层嵌套树结构验证正确 L1→L2→L3");
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("TC10: OrgDepartment删除有子部门时拒绝")
    void test10_deleteDeptWithChildren() {
        // Create company
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-DELCHK");
        company.setCompanyName("删除校验公司");
        Long companyId = orgCompanyService.create(company);

        // Create parent
        OrgDepartment parent = new OrgDepartment();
        parent.setCompanyId(companyId);
        parent.setDeptCode("PARENT-DEL");
        parent.setDeptName("父部门");
        Long parentId = orgDepartmentService.create(parent);

        // Create child
        OrgDepartment child = new OrgDepartment();
        child.setCompanyId(companyId);
        child.setParentId(parentId);
        child.setDeptCode("CHILD-DEL");
        child.setDeptName("子部门");
        orgDepartmentService.create(child);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgDepartmentService.delete(parentId),
                "有子部门时删除应抛出BusinessException");
        assertEquals("ORG_DEPT_HAS_CHILDREN", ex.getCode());

        System.out.println("✅ TC10 通过: 有子部门时删除正确拦截, code=" + ex.getCode());
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("TC11: OrgDepartment分页列表 → companyId筛选")
    void test11_deptPagedList() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-PG");
        company.setCompanyName("分页测试公司");
        Long companyId = orgCompanyService.create(company);

        OrgDepartment dept = new OrgDepartment();
        dept.setCompanyId(companyId);
        dept.setDeptCode("DEPT-PG");
        dept.setDeptName("分页部门");
        dept.setStatus("ENABLE");
        orgDepartmentService.create(dept);

        // List by companyId
        IPage<OrgDepartmentVO> page = orgDepartmentService.getPage(1, 20, companyId, null, null, null);
        assertTrue(page.getTotal() >= 1, "按companyId筛选应有结果");

        System.out.println("✅ TC11 通过: departmentPage total=" + page.getTotal());
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("TC12: OrgDepartment跨租户隔离")
    void test12_deptCrossTenantIsolation() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-DXTNT");
        company.setCompanyName("部门跨租户公司");
        Long companyId = orgCompanyService.create(company);

        OrgDepartment dept = new OrgDepartment();
        dept.setCompanyId(companyId);
        dept.setDeptCode("DEPT-XTNT");
        dept.setDeptName("跨租户部门");
        Long deptId = orgDepartmentService.create(dept);

        // Switch tenant
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 888L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgDepartmentService.getById(deptId),
                "不同租户查询部门应抛出BusinessException");
        assertEquals("ORG_DEPT_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC12 通过: 部门跨租户隔离正确");
    }

    // ═══════════════════════════════════════════════════════════
    // OrgPosition CRUD tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @Transactional
    @DisplayName("TC13: OrgPosition创建 → HR头衔无权限语义")
    void test13_createPosition() {
        OrgPosition position = new OrgPosition();
        position.setPositionCode("POS-MGR");
        position.setPositionName("项目经理");
        position.setCompanyId(posTestCompanyId);
        position.setDepartmentId(posTestDepartmentId);
        position.setStatus("ENABLE");

        Long id = orgPositionService.create(position);
        assertNotNull(id, "岗位ID不应为空");
        createdPositionId = id;

        OrgPositionVO vo = orgPositionService.getById(id);
        assertNotNull(vo);
        assertEquals("POS-MGR", vo.getPositionCode());
        assertEquals("项目经理", vo.getPositionName());
        assertEquals("ENABLE", vo.getStatus());
        assertNotNull(vo.getCreatedAt(), "创建时间应自动填充");

        System.out.println("✅ TC13 通过: positionId=" + vo.getId() + ", positionName=" + vo.getPositionName());
    }

    @Test
    @Order(14)
    @Transactional
    @DisplayName("TC14: OrgPosition编码重复校验")
    void test14_positionCodeDuplicate() {
        OrgPosition p1 = new OrgPosition();
        p1.setPositionCode("POS-DUP");
        p1.setPositionName("重复岗位1");
        p1.setCompanyId(posTestCompanyId);
        p1.setDepartmentId(posTestDepartmentId);
        orgPositionService.create(p1);

        OrgPosition p2 = new OrgPosition();
        p2.setPositionCode("POS-DUP");
        p2.setPositionName("重复岗位2");
        p2.setCompanyId(posTestCompanyId);
        p2.setDepartmentId(posTestDepartmentId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgPositionService.create(p2));
        assertEquals("ORG_POSITION_CODE_EXISTS", ex.getCode());

        System.out.println("✅ TC14 通过: 岗位编码重复正确拦截");
    }

    @Test
    @Order(15)
    @Transactional
    @DisplayName("TC15: OrgPosition分页列表 + 更新 + 删除")
    void test15_positionListUpdateDelete() {
        OrgPosition position = new OrgPosition();
        position.setPositionCode("POS-CRUD");
        position.setPositionName("全流程岗位");
        position.setCompanyId(posTestCompanyId);
        position.setDepartmentId(posTestDepartmentId);
        position.setStatus("ENABLE");
        Long id = orgPositionService.create(position);

        // List
        IPage<OrgPositionVO> page = orgPositionService.getPage(1L, 20L, null, null, null, null, null);
        assertTrue(page.getTotal() >= 1, "应有记录");

        // Update
        OrgPosition update = new OrgPosition();
        update.setId(id);
        update.setCompanyId(posTestCompanyId);
        update.setDepartmentId(posTestDepartmentId);
        update.setPositionName("更新后岗位");
        update.setStatus("DISABLE");
        orgPositionService.update(update);
        OrgPositionVO updated = orgPositionService.getById(id);
        assertEquals("更新后岗位", updated.getPositionName());
        assertEquals("DISABLE", updated.getStatus());

        // Delete
        orgPositionService.delete(id);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgPositionService.getById(id));
        assertEquals("ORG_POSITION_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC15 通过: 岗位CRUD全流程验证通过");
    }

    @Test
    @Order(16)
    @Transactional
    @DisplayName("TC16: OrgPosition跨租户隔离")
    void test16_positionCrossTenantIsolation() {
        OrgPosition position = new OrgPosition();
        position.setPositionCode("POS-XTNT");
        position.setPositionName("跨租户岗位");
        position.setCompanyId(posTestCompanyId);
        position.setDepartmentId(posTestDepartmentId);
        Long id = orgPositionService.create(position);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 777L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgPositionService.getById(id));
        assertEquals("ORG_POSITION_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC16 通过: 岗位跨租户隔离正确");
    }

    @Test
    @Order(17)
    @Transactional
    @DisplayName("TC17: OrgCompany状态默认值 → 不传status默认ENABLE")
    void test17_companyDefaultStatus() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-DFLT");
        company.setCompanyName("默认状态公司");

        Long id = orgCompanyService.create(company);
        OrgCompanyVO vo = orgCompanyService.getById(id);
        assertEquals("ENABLE", vo.getStatus(), "未指定status时应默认为ENABLE");

        System.out.println("✅ TC17 通过: 默认status=ENABLE");
    }

    @Test
    @Order(18)
    @Transactional
    @DisplayName("TC18: 查询不存在的实体 → 统一抛出BusinessException")
    void test18_notFound() {
        assertThrows(BusinessException.class, () -> orgCompanyService.getById(999999L));
        assertThrows(BusinessException.class, () -> orgDepartmentService.getById(999999L));
        assertThrows(BusinessException.class, () -> orgPositionService.getById(999999L));

        System.out.println("✅ TC18 通过: 不存在的实体统一抛出BusinessException");
    }

    @Test
    @Order(19)
    @Transactional
    @DisplayName("TC19: OrgDepartment更新部门信息")
    void test19_updateDepartment() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-UPDDEPT");
        company.setCompanyName("部门更新测试公司");
        Long companyId = orgCompanyService.create(company);

        OrgDepartment dept = new OrgDepartment();
        dept.setCompanyId(companyId);
        dept.setDeptCode("UPDATE-ME");
        dept.setDeptName("原始名称");
        dept.setStatus("ENABLE");
        Long deptId = orgDepartmentService.create(dept);

        OrgDepartment update = new OrgDepartment();
        update.setId(deptId);
        update.setDeptName("已更新名称");
        update.setStatus("DISABLE");
        update.setOrderNum(99);
        orgDepartmentService.update(update);

        OrgDepartmentVO vo = orgDepartmentService.getById(deptId);
        assertEquals("已更新名称", vo.getDeptName());
        assertEquals("DISABLE", vo.getStatus());
        assertEquals(99, vo.getOrderNum());

        System.out.println("✅ TC19 通过: 部门更新成功, name=" + vo.getDeptName() + " status=" + vo.getStatus());
    }

    @Test
    @Order(20)
    @Transactional
    @DisplayName("TC20: OrgDepartment无子部门时可成功删除")
    void test20_deleteLeafDepartment() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-DELLEAF");
        company.setCompanyName("叶子部门删除公司");
        Long companyId = orgCompanyService.create(company);

        OrgDepartment dept = new OrgDepartment();
        dept.setCompanyId(companyId);
        dept.setDeptCode("LEAF-DEPT");
        dept.setDeptName("叶子部门");
        dept.setStatus("ENABLE");
        Long deptId = orgDepartmentService.create(dept);

        // Should succeed - no children
        assertDoesNotThrow(() -> orgDepartmentService.delete(deptId));

        // Should now be not found
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orgDepartmentService.getById(deptId));
        assertEquals("ORG_DEPT_NOT_FOUND", ex.getCode());

        System.out.println("✅ TC20 通过: 叶子部门删除成功");
    }

    @Test
    @Order(21)
    @Transactional
    @DisplayName("TC21: OrgCompany更新公司信息")
    void test21_updateCompany() {
        OrgCompany company = new OrgCompany();
        company.setCompanyCode("COMP-UPDATE");
        company.setCompanyName("原始公司名");
        company.setStatus("ENABLE");
        Long companyId = orgCompanyService.create(company);

        OrgCompany update = new OrgCompany();
        update.setId(companyId);
        update.setCompanyName("已更新公司名");
        update.setStatus("DISABLE");
        orgCompanyService.update(update);

        OrgCompanyVO vo = orgCompanyService.getById(companyId);
        assertEquals("已更新公司名", vo.getCompanyName());
        assertEquals("DISABLE", vo.getStatus());

        System.out.println("✅ TC21 通过: 公司更新成功, name=" + vo.getCompanyName());
    }
}
