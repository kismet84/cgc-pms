package com.cgcpms.cost.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.cost.vo.CostSubjectTreeNodeVO;
import com.cgcpms.cost.vo.CostSubjectVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("CostSubjectService 单元测试")
class CostSubjectServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired
    private CostSubjectService costSubjectService;

    @Autowired
    private CostSubjectMapper costSubjectMapper;

    @Autowired
    private CostItemMapper costItemMapper;

    @Autowired
    private CostTargetItemMapper costTargetItemMapper;

    @Autowired
    private CostTargetMapper costTargetMapper;

    @BeforeEach
    void setUp() {
        var claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .build();
        UserContext.set(claims);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // getTree — 树形结构查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("getTree：不存在的分类返回空列表")
    void getTreeWithSentinelAccountCategoryReturnsEmpty() {
        List<CostSubjectTreeNodeVO> tree = costSubjectService.getTree("NONEXISTENT_CAT");
        assertNotNull(tree, "应返回非null列表");
        assertTrue(tree.isEmpty(), "不存在的分类应返回空列表");
    }

    @Test
    @Transactional
    @DisplayName("getTree：单层平级科目构建正确的树结构")
    void getTreeFlatSubjectsReturnsAllRoots() {
        String code1 = "TSTGT_F1_" + System.nanoTime();
        String code2 = "TSTGT_F2_" + System.nanoTime();
        String code3 = "TSTGT_F3_" + System.nanoTime();
        createSubject(code1, "科目1", 0L, "COST", 1, 1);
        createSubject(code2, "科目2", 0L, "COST", 1, 2);
        createSubject(code3, "科目3", 0L, "COST", 1, 3);

        List<CostSubjectTreeNodeVO> tree = costSubjectService.getTree("COST");
        assertTrue(tree.size() >= 3, "应至少有 3 个根节点");
        assertTrue(tree.stream().anyMatch(n -> code1.equals(n.getSubjectCode())));
    }

    @Test
    @Transactional
    @DisplayName("getTree：父子层级构建正确的嵌套结构")
    void getTreeParentChildHierarchy() {
        String rootCode = "TSTGT_ROOT_" + System.nanoTime();
        String child1Code = "TSTGT_CH1_" + System.nanoTime();
        String child2Code = "TSTGT_CH2_" + System.nanoTime();
        String grandCode = "TSTGT_GR_" + System.nanoTime();

        CostSubject root = createSubject(rootCode, "根科目", 0L, "COST", 1, 1);
        createSubject(child1Code, "子科目1", root.getId(), "COST", 2, 1);
        createSubject(child2Code, "子科目2", root.getId(), "COST", 2, 2);

        List<CostSubjectTreeNodeVO> tree = costSubjectService.getTree("COST");
        CostSubjectTreeNodeVO rootNode = findNodeByCode(tree, rootCode);
        assertNotNull(rootNode, "应能找到我们的根节点");
        assertEquals(2, rootNode.getChildren().size(), "根节点应有2个子节点");

        // 创建孙子科目
        CostSubject child1 = findSubjectByCode(child1Code);
        createSubject(grandCode, "孙子科目", child1.getId(), "COST", 3, 1);

        // 刷新树
        tree = costSubjectService.getTree("COST");
        rootNode = findNodeByCode(tree, rootCode);
        assertNotNull(rootNode, "刷新后仍应能找到根节点");
        boolean foundGrand = rootNode.getChildren().get(0).getChildren().stream()
                .anyMatch(n -> grandCode.equals(n.getSubjectCode()));
        assertTrue(foundGrand, "子科目1应有1个孙子节点");
    }

    @Test
    @Transactional
    @DisplayName("getTree：按 accountCategory 过滤科目")
    void getTreeFiltersByAccountCategory() {
        String costCode = "TSTGTFC_C_" + System.nanoTime();
        String revCode = "TSTGTFC_R_" + System.nanoTime();
        createSubject(costCode, "成本科目", 0L, "COST", 1, 1);
        createSubject(revCode, "收入科目", 0L, "REVENUE", 1, 1);

        List<CostSubjectTreeNodeVO> costTree = costSubjectService.getTree("COST");
        assertTrue(costTree.stream().anyMatch(n -> costCode.equals(n.getSubjectCode())),
                "COST 树应包含成本科目");
        assertTrue(costTree.stream().noneMatch(n -> revCode.equals(n.getSubjectCode())),
                "COST 树不应包含收入科目");

        List<CostSubjectTreeNodeVO> revTree = costSubjectService.getTree("REVENUE");
        assertTrue(revTree.stream().anyMatch(n -> revCode.equals(n.getSubjectCode())),
                "REVENUE 树应包含收入科目");
    }

    @Test
    @Transactional
    @DisplayName("getTree：accountCategory 为 null 返回所有科目")
    void getTreeNullAccountCategoryReturnsAll() {
        String code1 = "TSTGTNA1_" + System.nanoTime();
        String code2 = "TSTGTNA2_" + System.nanoTime();
        createSubject(code1, "全部科目1", 0L, "COST", 1, 1);
        createSubject(code2, "全部科目2", 0L, "REVENUE", 1, 1);

        List<CostSubjectTreeNodeVO> tree = costSubjectService.getTree(null);
        assertTrue(tree.size() >= 2, "应至少包含创建的2个科目");
        assertTrue(tree.stream().anyMatch(n -> code1.equals(n.getSubjectCode())));
        assertTrue(tree.stream().anyMatch(n -> code2.equals(n.getSubjectCode())));
    }

    // ═══════════════════════════════════════════════════════════════
    // getList — 平铺列表查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("getList：返回所有科目并正确转换为 VO")
    void getListReturnsSubjectsAsVO() {
        String codeA = "TSTGLA_" + System.nanoTime();
        String codeB = "TSTGLB_" + System.nanoTime();
        createSubject(codeA, "列表科目A", 0L, "COST", 1, 1);
        createSubject(codeB, "列表科目B", 0L, "COST", 1, 2);

        List<CostSubjectVO> list = costSubjectService.getList("COST");
        assertTrue(list.stream().anyMatch(v -> codeA.equals(v.getSubjectCode())));
        assertTrue(list.stream().anyMatch(v -> codeB.equals(v.getSubjectCode())));

        CostSubjectVO voA = list.stream()
                .filter(v -> codeA.equals(v.getSubjectCode()))
                .findFirst().orElseThrow();
        assertNotNull(voA.getId(), "VO 应有 id");
        assertEquals("0", voA.getParentId(), "根节点 parentId 应为 '0'");
        assertEquals(codeA, voA.getSubjectCode());
    }

    @Test
    @Transactional
    @DisplayName("getList：按 accountCategory 过滤")
    void getListFiltersByAccountCategory() {
        String codeC = "TSTGLF_C_" + System.nanoTime();
        String codeR = "TSTGLF_R_" + System.nanoTime();
        createSubject(codeC, "成本1", 0L, "COST", 1, 1);
        createSubject(codeR, "收入1", 0L, "REVENUE", 1, 1);

        List<CostSubjectVO> costList = costSubjectService.getList("COST");
        assertTrue(costList.stream().anyMatch(v -> codeC.equals(v.getSubjectCode())));
        assertTrue(costList.stream().noneMatch(v -> codeR.equals(v.getSubjectCode())));

        List<CostSubjectVO> revList = costSubjectService.getList("REVENUE");
        assertTrue(revList.stream().anyMatch(v -> codeR.equals(v.getSubjectCode())));
        assertTrue(revList.stream().noneMatch(v -> codeC.equals(v.getSubjectCode())));
    }

    // ═══════════════════════════════════════════════════════════════
    // getById — 单条查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("getById：正常返回科目详情")
    void getByIdReturnsSubjectVO() {
        String code = "TSTGBI_" + System.nanoTime();
        CostSubject entity = createSubject(code, "查询科目", 0L, "COST", 1, 1);

        CostSubjectVO vo = costSubjectService.getById(entity.getId());
        assertNotNull(vo);
        assertEquals(code, vo.getSubjectCode());
        assertEquals("COST", vo.getAccountCategory());
        assertEquals(1, vo.getLevel());
        assertNotNull(vo.getCreatedAt(), "createdAt 应为非空");
    }

    @Test
    @Transactional
    @DisplayName("getById：不存在的 ID 抛 BusinessException")
    void getByIdNotFoundThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.getById(999999L));
        assertEquals("COST_SUBJECT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("getById：跨租户查询抛 BusinessException")
    void getByIdCrossTenantThrowsBusinessException() {
        String code = "TSTGBIXT_" + System.nanoTime();
        CostSubject entity = createSubject(code, "租户科目", 0L, "COST", 1, 1);

        UserContext.clear();
        UserContext.set(Jwts.claims()
                .subject("other")
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.getById(entity.getId()));
        assertEquals("COST_SUBJECT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // create — 创建科目
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("create：根科目创建成功，parentId 不设时强制为 0，level 强制为 1")
    void createRootSubjectSuccess() {
        String code = "TSTCRT_" + System.nanoTime();
        CostSubject subject = new CostSubject();
        subject.setSubjectCode(code);
        subject.setSubjectName("新建根科目");
        subject.setSubjectType("MATERIAL");
        subject.setAccountCategory("COST");
        subject.setSortOrder(1);
        subject.setStatus("ENABLE");
        // 不设 parentId 和 level，由 service 自动设置

        Long id = costSubjectService.create(subject);
        assertNotNull(id, "应返回生成的 ID");

        CostSubject saved = costSubjectMapper.selectById(id);
        assertEquals(0L, saved.getParentId(), "根科目 parentId 应为 0");
        assertEquals(1, saved.getLevel(), "根科目 level 应为 1");
    }

    @Test
    @Transactional
    @DisplayName("create：子科目继承父科目 level+1 和 accountCategory")
    void createChildSubjectInheritsFromParent() {
        String parentCode = "TSTCP_" + System.nanoTime();
        String childCode = "TSTCC_" + System.nanoTime();
        CostSubject parent = createSubject(parentCode, "父科目", 0L, "COST", 1, 1);

        CostSubject child = new CostSubject();
        child.setSubjectCode(childCode);
        child.setSubjectName("子科目");
        child.setSubjectType("MATERIAL");
        child.setSortOrder(1);
        child.setStatus("ENABLE");
        child.setParentId(parent.getId());

        Long id = costSubjectService.create(child);
        CostSubject saved = costSubjectMapper.selectById(id);
        assertEquals(parent.getId(), saved.getParentId());
        assertEquals(2, saved.getLevel(), "子科目 level 应为父+1=2");
        assertEquals("COST", saved.getAccountCategory(), "应继承父科目的 accountCategory");
    }

    @Test
    @Transactional
    @DisplayName("create：子科目可覆盖 accountCategory")
    void createChildSubjectOverrideAccountCategory() {
        String parentCode = "TSTCP2_" + System.nanoTime();
        String childCode = "TSTCC2_" + System.nanoTime();
        CostSubject parent = createSubject(parentCode, "父科目2", 0L, "COST", 1, 1);

        CostSubject child = new CostSubject();
        child.setSubjectCode(childCode);
        child.setSubjectName("子科目2");
        child.setSubjectType("MATERIAL");
        child.setAccountCategory("REVENUE");
        child.setSortOrder(1);
        child.setStatus("ENABLE");
        child.setParentId(parent.getId());

        Long id = costSubjectService.create(child);
        CostSubject saved = costSubjectMapper.selectById(id);
        assertEquals("REVENUE", saved.getAccountCategory(), "显式设置的 accountCategory 应保留");
    }

    @Test
    @Transactional
    @DisplayName("create：父科目不存在抛 PARENT_NOT_FOUND")
    void createParentNotFoundThrowsException() {
        String code = "TSTCOPH_" + System.nanoTime();
        CostSubject subject = new CostSubject();
        subject.setSubjectCode(code);
        subject.setSubjectName("孤儿科目");
        subject.setSubjectType("MATERIAL");
        subject.setSortOrder(1);
        subject.setStatus("ENABLE");
        subject.setParentId(999999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.create(subject));
        assertEquals("PARENT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("create：父科目属于其他租户抛 PARENT_NOT_FOUND")
    void createParentCrossTenantThrowsException() {
        String parentCode = "TSTCPXT_" + System.nanoTime();
        String childCode = "TSTCCXT_" + System.nanoTime();
        CostSubject parent = createSubject(parentCode, "跨租户父", 0L, "COST", 1, 1);

        UserContext.clear();
        UserContext.set(Jwts.claims()
                .subject("other")
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        CostSubject child = new CostSubject();
        child.setSubjectCode(childCode);
        child.setSubjectName("跨租户子");
        child.setSubjectType("MATERIAL");
        child.setSortOrder(1);
        child.setStatus("ENABLE");
        child.setParentId(parent.getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.create(child));
        assertEquals("PARENT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("create：科目编码重复抛 SUBJECT_CODE_DUPLICATE")
    void createDuplicateCodeThrowsException() {
        String code = "TSTCDUP_" + System.nanoTime();
        createSubject(code, "已有科目", 0L, "COST", 1, 1);

        CostSubject duplicate = new CostSubject();
        duplicate.setSubjectCode(code);
        duplicate.setSubjectName("重复编码科目");
        duplicate.setSubjectType("MATERIAL");
        duplicate.setAccountCategory("COST");
        duplicate.setSortOrder(1);
        duplicate.setStatus("ENABLE");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.create(duplicate));
        assertEquals("SUBJECT_CODE_DUPLICATE", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("create：逻辑删除后可用相同编码创建新科目（deleted_flag 区分唯一键）")
    void createDuplicateCodeOccupiedByDeletedSubject() {
        setAdminContext();
        String code = "TSTCDELDUP_" + System.nanoTime();
        CostSubject deletedSubject = new CostSubject();
        deletedSubject.setTenantId(TENANT_ID);
        deletedSubject.setParentId(0L);
        deletedSubject.setSubjectCode(code);
        deletedSubject.setSubjectName("已删除科目");
        deletedSubject.setSubjectType("MATERIAL");
        deletedSubject.setAccountCategory("COST");
        deletedSubject.setLevel(1);
        deletedSubject.setSortOrder(1);
        deletedSubject.setStatus("ENABLE");
        costSubjectMapper.insert(deletedSubject);
        costSubjectMapper.deleteById(deletedSubject.getId());

        // 用相同编码创建新科目，应成功（因为 deleted_flag 不同，唯一键不冲突）
        CostSubject duplicate = new CostSubject();
        duplicate.setSubjectCode(code);
        duplicate.setSubjectName("重建科目");
        duplicate.setSubjectType("MATERIAL");
        duplicate.setAccountCategory("COST");
        duplicate.setSortOrder(2);
        duplicate.setStatus("ENABLE");

        Long newId = costSubjectService.create(duplicate);
        assertNotNull(newId, "逻辑删除后相同编码应能创建成功");

        CostSubject saved = costSubjectMapper.selectById(newId);
        assertEquals(code, saved.getSubjectCode());
        assertEquals("重建科目", saved.getSubjectName());
    }

    // ═══════════════════════════════════════════════════════════════
    // update — 更新科目
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("update：正常更新科目名称和状态")
    void updateSubjectSuccess() {
        String oldCode = "TSTUPO_" + System.nanoTime();
        String newCode = "TSTUPN_" + System.nanoTime();
        CostSubject entity = createSubject(oldCode, "原名称", 0L, "COST", 1, 1);

        CostSubject update = new CostSubject();
        update.setId(entity.getId());
        update.setSubjectCode(newCode);
        update.setSubjectName("新名称");
        update.setStatus("DISABLE");

        costSubjectService.update(update);

        CostSubject saved = costSubjectMapper.selectById(entity.getId());
        assertEquals(newCode, saved.getSubjectCode());
        assertEquals("新名称", saved.getSubjectName());
        assertEquals("DISABLE", saved.getStatus());
    }

    @Test
    @Transactional
    @DisplayName("update：科目不存在抛 COST_SUBJECT_NOT_FOUND")
    void updateNotFoundThrowsException() {
        CostSubject update = new CostSubject();
        update.setId(999999L);
        update.setSubjectCode("TSTUPNF_" + System.nanoTime());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.update(update));
        assertEquals("COST_SUBJECT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("update：跨租户更新抛 COST_SUBJECT_NOT_FOUND")
    void updateCrossTenantThrowsException() {
        String code = "TSTUPXT_" + System.nanoTime();
        CostSubject entity = createSubject(code, "租户科目", 0L, "COST", 1, 1);

        UserContext.clear();
        UserContext.set(Jwts.claims()
                .subject("other")
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        CostSubject update = new CostSubject();
        update.setId(entity.getId());
        update.setSubjectCode(code + "_NEW");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.update(update));
        assertEquals("COST_SUBJECT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("update：编码与其他记录重复抛 SUBJECT_CODE_DUPLICATE（排除自身）")
    void updateDuplicateCodeExcludingSelfThrowsException() {
        String existingCode = "TSTUPEX_" + System.nanoTime();
        String targetCode = "TSTUPTG_" + System.nanoTime();
        createSubject(existingCode, "已有科目", 0L, "COST", 1, 1);
        CostSubject target = createSubject(targetCode, "目标科目", 0L, "COST", 1, 2);

        CostSubject update = new CostSubject();
        update.setId(target.getId());
        update.setSubjectCode(existingCode);
        update.setAccountCategory("COST");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.update(update));
        assertEquals("SUBJECT_CODE_DUPLICATE", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("update：相同编码更新自身不抛重复异常（excludeId 生效）")
    void updateSameCodeSelfNoDuplicateError() {
        String code = "TSTUPSLF_" + System.nanoTime();
        CostSubject entity = createSubject(code, "自身科目", 0L, "COST", 1, 1);

        CostSubject update = new CostSubject();
        update.setId(entity.getId());
        update.setSubjectCode(code);
        update.setSubjectName("改名后的自身");

        assertDoesNotThrow(() -> costSubjectService.update(update));

        CostSubject saved = costSubjectMapper.selectById(entity.getId());
        assertEquals("改名后的自身", saved.getSubjectName());
    }

    // ═══════════════════════════════════════════════════════════════
    // toggleStatus — 切换启用/禁用
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("toggleStatus：ENABLE 切换为 DISABLE")
    void toggleStatusEnableToDisable() {
        String code = "TSTTOG1_" + System.nanoTime();
        CostSubject entity = createSubject(code, "待切换科目", 0L, "COST", 1, 1);

        costSubjectService.toggleStatus(entity.getId());

        CostSubject saved = costSubjectMapper.selectById(entity.getId());
        assertEquals("DISABLE", saved.getStatus());
    }

    @Test
    @Transactional
    @DisplayName("toggleStatus：DISABLE 切换回 ENABLE")
    void toggleStatusDisableToEnable() {
        String code = "TSTTOG2_" + System.nanoTime();
        CostSubject entity = createSubject(code, "已禁用科目", 0L, "COST", 1, 1);
        entity.setStatus("DISABLE");
        costSubjectMapper.updateById(entity);

        costSubjectService.toggleStatus(entity.getId());

        CostSubject saved = costSubjectMapper.selectById(entity.getId());
        assertEquals("ENABLE", saved.getStatus());
    }

    @Test
    @Transactional
    @DisplayName("toggleStatus：不存在的科目抛 COST_SUBJECT_NOT_FOUND")
    void toggleStatusNotFoundThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.toggleStatus(999999L));
        assertEquals("COST_SUBJECT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("toggleStatus：跨租户切换抛 COST_SUBJECT_NOT_FOUND")
    void toggleStatusCrossTenantThrowsException() {
        String code = "TSTTOGXT_" + System.nanoTime();
        CostSubject entity = createSubject(code, "跨租户科目", 0L, "COST", 1, 1);

        UserContext.clear();
        UserContext.set(Jwts.claims()
                .subject("other")
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.toggleStatus(entity.getId()));
        assertEquals("COST_SUBJECT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // delete — 删除科目
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("delete：无子节点和引用的科目可正常删除（逻辑删除）")
    void deleteSubjectSuccess() {
        String code = "TSTDEL_" + System.nanoTime();
        CostSubject entity = createSubject(code, "待删除科目", 0L, "COST", 1, 1);

        costSubjectService.delete(entity.getId());

        CostSubject deleted = costSubjectMapper.selectById(entity.getId());
        assertNull(deleted, "逻辑删除后 selectById 应返回 null");
    }

    @Test
    @Transactional
    @DisplayName("delete：科目不存在抛 COST_SUBJECT_NOT_FOUND")
    void deleteNotFoundThrowsException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.delete(999999L));
        assertEquals("COST_SUBJECT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("delete：跨租户删除抛 COST_SUBJECT_NOT_FOUND")
    void deleteCrossTenantThrowsException() {
        String code = "TSTDELXT_" + System.nanoTime();
        CostSubject entity = createSubject(code, "跨租户删除", 0L, "COST", 1, 1);

        UserContext.clear();
        UserContext.set(Jwts.claims()
                .subject("other")
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.delete(entity.getId()));
        assertEquals("COST_SUBJECT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("delete：存在子科目时抛 HAS_CHILDREN")
    void deleteWithChildrenThrowsException() {
        String parentCode = "TSTDELP_" + System.nanoTime();
        String childCode = "TSTDELC_" + System.nanoTime();
        CostSubject parent = createSubject(parentCode, "有子科目的父", 0L, "COST", 1, 1);
        createSubject(childCode, "子科目", parent.getId(), "COST", 2, 1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.delete(parent.getId()));
        assertEquals("HAS_CHILDREN", ex.getCode());

        assertNotNull(costSubjectMapper.selectById(parent.getId()), "父科目应未被删除");
    }

    @Test
    @Transactional
    @DisplayName("delete：被成本明细引用时抛 COST_SUBJECT_REFERENCED")
    void deleteWithCostItemReferencesThrowsException() {
        String code = "TSTDELCI_" + System.nanoTime();
        CostSubject subject = createSubject(code, "被引用的科目", 0L, "COST", 1, 1);

        CostItem costItem = new CostItem();
        costItem.setTenantId(TENANT_ID);
        costItem.setProjectId(10001L);
        costItem.setOrgId(1L);
        costItem.setCostSubjectId(subject.getId());
        costItem.setCostType("MATERIAL");
        costItem.setSourceType("MAT_RECEIPT");
        costItem.setSourceId(1L);
        costItem.setCostDate(java.time.LocalDate.now());
        costItem.setDeletedFlag(0);
        costItemMapper.insert(costItem);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.delete(subject.getId()));
        assertEquals("COST_SUBJECT_REFERENCED", ex.getCode());
        assertTrue(ex.getMessage().contains("成本明细引用"));
    }

    @Test
    @Transactional
    @DisplayName("delete：被目标成本明细引用时抛 COST_SUBJECT_REFERENCED")
    void deleteWithCostTargetItemReferencesThrowsException() {
        String code = "TSTDELCTI_" + System.nanoTime();
        CostSubject subject = createSubject(code, "被目标引用的科目", 0L, "COST", 1, 1);

        CostTarget target = new CostTarget();
        target.setTenantId(TENANT_ID);
        target.setProjectId(10001L);
        target.setVersionNo("TST-REF-" + System.nanoTime());
        target.setVersionName("科目引用保护测试");
        target.setTotalBidCostAmount(java.math.BigDecimal.ZERO);
        target.setTotalTargetAmount(java.math.BigDecimal.ZERO);
        target.setTotalResponsibilityAmount(java.math.BigDecimal.ZERO);
        target.setIsActive(0);
        target.setApprovalStatus("DRAFT");
        target.setStatus("DRAFT");
        target.setDeletedFlag(0);
        costTargetMapper.insert(target);

        CostTargetItem targetItem = new CostTargetItem();
        targetItem.setTenantId(TENANT_ID);
        targetItem.setTargetId(target.getId());
        targetItem.setProjectId(10001L);
        targetItem.setCostSubjectId(subject.getId());
        targetItem.setDeletedFlag(0);
        costTargetItemMapper.insert(targetItem);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSubjectService.delete(subject.getId()));
        assertEquals("COST_SUBJECT_REFERENCED", ex.getCode());
        assertTrue(ex.getMessage().contains("目标成本明细引用"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 跨租户隔离
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("跨租户隔离：getList 只返回当前租户数据")
    void crossTenantIsolationGetList() {
        String code0 = "TSTISO0_" + System.nanoTime();
        createSubject(code0, "租户0科目", 0L, "COST", 1, 1);

        UserContext.clear();
        UserContext.set(Jwts.claims()
                .subject("other")
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        String code999 = "TSTISO999_" + System.nanoTime();
        createSubject(code999, "租户999科目", 0L, "COST", 1, 1);

        List<CostSubjectVO> list999 = costSubjectService.getList("COST");
        assertTrue(list999.stream().anyMatch(v -> code999.equals(v.getSubjectCode())),
                "租户999 应能看到自己创建的科目");
        assertTrue(list999.stream().noneMatch(v -> code0.equals(v.getSubjectCode())),
                "租户999 不应看到租户0的科目");

        UserContext.clear();
        setAdminContext();

        List<CostSubjectVO> list0 = costSubjectService.getList("COST");
        assertTrue(list0.stream().anyMatch(v -> code0.equals(v.getSubjectCode())),
                "租户0 应能看到自己创建的科目");
        assertTrue(list0.stream().noneMatch(v -> code999.equals(v.getSubjectCode())),
                "租户0 不应看到租户999的科目");
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    private void setAdminContext() {
        var claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .build();
        UserContext.set(claims);
    }

    private CostSubject createSubject(String code, String name, Long parentId,
                                      String accountCategory, int level, int sortOrder) {
        CostSubject subject = new CostSubject();
        subject.setTenantId(UserContext.getCurrentTenantId());
        subject.setParentId(parentId);
        subject.setSubjectCode(code);
        subject.setSubjectName(name);
        subject.setSubjectType("MATERIAL");
        subject.setAccountCategory(accountCategory);
        subject.setLevel(level);
        subject.setSortOrder(sortOrder);
        subject.setStatus("ENABLE");
        costSubjectMapper.insert(subject);
        return subject;
    }

    private CostSubject findSubjectByCode(String code) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostSubject>();
        wrapper.eq(CostSubject::getTenantId, TENANT_ID);
        wrapper.eq(CostSubject::getSubjectCode, code);
        return costSubjectMapper.selectOne(wrapper);
    }

    private CostSubjectTreeNodeVO findNodeByCode(List<CostSubjectTreeNodeVO> nodes, String code) {
        for (var node : nodes) {
            if (code.equals(node.getSubjectCode())) return node;
            var found = findNodeByCode(node.getChildren(), code);
            if (found != null) return found;
        }
        return null;
    }
}
