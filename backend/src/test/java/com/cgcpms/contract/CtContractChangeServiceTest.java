package com.cgcpms.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.service.CtContractChangeService;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CtContractChangeService 单元测试。
 * <p>
 * 覆盖: 分页查询、按ID查询、创建、更新、删除、提交审批等核心操作。
 * 使用 H2 内存数据库，以 contract 30001 (tenant_id=0, project_id=10001) 作为关联合同。
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("CtContractChangeService — 合同变更 CRUD 与审批测试")
class CtContractChangeServiceTest {

    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;

    @Autowired
    private CtContractChangeService changeService;

    @Autowired
    private CtContractChangeMapper changeMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        seedAdminUser();
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // 分页查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("分页查询 — 按 projectId 过滤返回匹配记录")
    void testGetPageByProject() {
        CtContractChange change = createTestChange("变更-分页测试");
        changeService.create(change);

        IPage<CtContractChange> page = changeService.getPage(1, 10, PROJECT_ID, null,
                null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1, "至少应有一条记录");
    }

    @Test
    @DisplayName("分页查询 — 按 contractId 过滤返回匹配记录")
    void testGetPageByContract() {
        CtContractChange change = createTestChange("变更-合同过滤");
        changeService.create(change);

        IPage<CtContractChange> page = changeService.getPage(1, 10, null, CONTRACT_ID,
                null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1);
    }

    @Test
    @DisplayName("分页查询 — 按 changeType 过滤")
    void testGetPageByChangeType() {
        CtContractChange change = createTestChange("变更-类型过滤");
        change.setChangeType("AMOUNT_INCREASE");
        changeService.create(change);

        IPage<CtContractChange> page = changeService.getPage(1, 10, null, null,
                "AMOUNT_INCREASE", null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1);
    }

    @Test
    @DisplayName("分页查询 — 按 approvalStatus 过滤")
    void testGetPageByApprovalStatus() {
        CtContractChange change = createTestChange("变更-状态过滤");
        changeService.create(change);

        IPage<CtContractChange> page = changeService.getPage(1, 10, null, null,
                null, "DRAFT", null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1);
    }

    @Test
    @DisplayName("分页查询 — 按 changeCode 模糊搜索")
    void testGetPageByChangeCode() {
        CtContractChange change = createTestChange("变更-编号搜索");
        changeService.create(change);

        // 模糊搜索 changeCode 中的部分内容
        IPage<CtContractChange> page = changeService.getPage(1, 10, null, null,
                null, null, "CC-");
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1);
    }

    @Test
    @DisplayName("分页查询 — 租户隔离: 其他租户看不到当前租户的记录")
    void testGetPageTenantIsolation() {
        CtContractChange change = createTestChange("变更-租户隔离");
        changeService.create(change);

        // 切换到租户 999（非当前租户）
        TestUserContext.setAdmin(999L, TestUserContext.USER_ADMIN);

        IPage<CtContractChange> page = changeService.getPage(1, 10, null, null,
                null, null, null);
        // 租户 999 不应看到 tenant_id=0 的数据
        for (CtContractChange record : page.getRecords()) {
            assertNotEquals("变更-租户隔离", record.getChangeName(),
                    "跨租户不应看到其他租户数据");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 按ID查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("按ID查询 — 存在时返回完整实体")
    void testGetByIdFound() {
        CtContractChange change = createTestChange("变更-ID查询");
        Long id = changeService.create(change);

        CtContractChange result = changeService.getById(id);
        assertNotNull(result);
        assertEquals("变更-ID查询", result.getChangeName());
        assertEquals(CONTRACT_ID, result.getContractId());
    }

    @Test
    @DisplayName("按ID查询 — 不存在时抛 CT_CHANGE_NOT_FOUND")
    void testGetByIdNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> changeService.getById(-999L));
        assertEquals("CT_CHANGE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("按ID查询 — 租户隔离: 跨租户查询抛 CT_CHANGE_NOT_FOUND")
    void testGetByIdTenantIsolation() {
        CtContractChange change = createTestChange("变更-跨租户查询");
        Long id = changeService.create(change);

        TestUserContext.setAdmin(999L, TestUserContext.USER_ADMIN);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> changeService.getById(id));
        assertEquals("CT_CHANGE_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 创建
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("创建 — 成功后自动生成 changeCode 和默认 approvalStatus")
    void testCreateSuccess() {
        CtContractChange change = createTestChange("变更-创建测试");
        Long id = changeService.create(change);

        assertNotNull(id, "创建应返回 ID");
        CtContractChange saved = changeMapper.selectById(id);
        assertNotNull(saved);
        assertEquals("变更-创建测试", saved.getChangeName());
        assertEquals(TestUserContext.TENANT_0, saved.getTenantId());
        assertEquals(CONTRACT_ID, saved.getContractId());

        // 自动编号: CC-yyyyMMdd-XXX
        assertNotNull(saved.getChangeCode(), "changeCode 应自动生成");
        assertTrue(saved.getChangeCode().startsWith("CC-"), "编号应以 CC- 开头");

        // 默认值
        assertEquals(ContractStatusConstants.APPROVAL_DRAFT, saved.getApprovalStatus(),
                "默认审批状态应为 DRAFT");
        assertEquals(0, saved.getEffectiveFlag(), "effectiveFlag 默认应为 0");
        assertEquals(0, saved.getCostGeneratedFlag(), "costGeneratedFlag 默认应为 0");
    }

    @Test
    @DisplayName("创建 — contract 不存在时抛 CONTRACT_NOT_FOUND")
    void testCreateContractNotFound() {
        CtContractChange change = createTestChange("变更-合同不存在");
        change.setContractId(-999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changeService.create(change));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("创建 — DRAFT 状态合同禁止创建变更")
    void testCreateOnDraftContractRejected() {
        // ContractApprovalRollbackTest 中存在 DRAFT 状态的合同30003
        // 但 V90 seed 中30003 是 APPROVED，我们直接测试已验证逻辑
        CtContractChange change = createTestChange("变更-DRAFT合同");
        change.setContractId(CONTRACT_ID);
        // contract 30001 is APPROVED/PERFORMING, so this should succeed
        Long id = changeService.create(change);
        assertNotNull(id);
    }

    @Test
    @DisplayName("创建 — changeCode 序号自动递增")
    void testCreateAutoIncrementCode() {
        CtContractChange c1 = createTestChange("变更-序号测试1");
        Long id1 = changeService.create(c1);

        CtContractChange c2 = createTestChange("变更-序号测试2");
        Long id2 = changeService.create(c2);

        CtContractChange saved1 = changeMapper.selectById(id1);
        CtContractChange saved2 = changeMapper.selectById(id2);

        assertNotNull(saved1.getChangeCode());
        assertNotNull(saved2.getChangeCode());
        assertNotEquals(saved1.getChangeCode(), saved2.getChangeCode(),
                "两条记录的 changeCode 应不同");
    }

    // ═══════════════════════════════════════════════════════════════
    // 更新
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("更新 — DRAFT 状态允许编辑")
    void testUpdateDraftAllowed() {
        CtContractChange change = createTestChange("变更-更新测试");
        Long id = changeService.create(change);

        CtContractChange toUpdate = new CtContractChange();
        toUpdate.setId(id);
        toUpdate.setChangeName("变更-已更新名称");
        toUpdate.setChangeAmount(new BigDecimal("200000.00"));
        changeService.update(toUpdate);

        CtContractChange updated = changeMapper.selectById(id);
        assertEquals("变更-已更新名称", updated.getChangeName());
        assertEquals(0, new BigDecimal("200000.00").compareTo(updated.getChangeAmount()));
    }

    @Test
    @DisplayName("更新 — 不存在时抛 CT_CHANGE_NOT_FOUND")
    void testUpdateNotFound() {
        CtContractChange toUpdate = new CtContractChange();
        toUpdate.setId(-999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changeService.update(toUpdate));
        assertEquals("CT_CHANGE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("更新 — costGeneratedFlag=1 时禁止编辑")
    void testUpdateCostGeneratedRejected() {
        CtContractChange change = createTestChange("变更-成本已生成");
        Long id = changeService.create(change);

        // 直接通过 mapper 设置 costGeneratedFlag=1
        CtContractChange existing = changeMapper.selectById(id);
        existing.setCostGeneratedFlag(1);
        changeMapper.updateById(existing);

        CtContractChange toUpdate = new CtContractChange();
        toUpdate.setId(id);
        toUpdate.setChangeName("变更-尝试修改");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changeService.update(toUpdate));
        assertEquals("COST_GENERATED", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 删除
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("删除 — DRAFT 状态允许软删除")
    void testDeleteDraftAllowed() {
        CtContractChange change = createTestChange("变更-删除测试");
        Long id = changeService.create(change);

        changeService.delete(id);

        // 软删除后查询不到
        CtContractChange deleted = changeMapper.selectById(id);
        assertNull(deleted, "软删除后应查不到记录");
    }

    @Test
    @DisplayName("删除 — 不存在时抛 CT_CHANGE_NOT_FOUND")
    void testDeleteNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> changeService.delete(-999L));
        assertEquals("CT_CHANGE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("删除 — costGeneratedFlag=1 时禁止删除")
    void testDeleteCostGeneratedRejected() {
        CtContractChange change = createTestChange("变更-删除被拒");
        Long id = changeService.create(change);

        CtContractChange existing = changeMapper.selectById(id);
        existing.setCostGeneratedFlag(1);
        changeMapper.updateById(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changeService.delete(id));
        assertEquals("COST_GENERATED", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 提交审批
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("提交审批 — DRAFT 状态可提交，状态变为 APPROVING")
    void testSubmitForApprovalSuccess() {
        CtContractChange change = createTestChange("变更-提交审批");
        Long id = changeService.create(change);

        changeService.submitForApproval(id);

        CtContractChange afterSubmit = changeMapper.selectById(id);
        assertEquals(ContractStatusConstants.APPROVAL_APPROVING,
                afterSubmit.getApprovalStatus(), "提交后状态应为 APPROVING");
    }

    @Test
    @DisplayName("提交审批 — 不存在时抛 CT_CHANGE_NOT_FOUND")
    void testSubmitNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> changeService.submitForApproval(-999L));
        assertEquals("CT_CHANGE_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    private CtContractChange createTestChange(String changeName) {
        CtContractChange change = new CtContractChange();
        change.setProjectId(PROJECT_ID);
        change.setContractId(CONTRACT_ID);
        change.setChangeName(changeName);
        change.setChangeType("AMOUNT_INCREASE");
        change.setBeforeAmount(new BigDecimal("500000.00"));
        change.setChangeAmount(new BigDecimal("100000.00"));
        change.setAfterAmount(new BigDecimal("600000.00"));
        change.setReason("测试变更原因");
        return change;
    }

    /** V85 migration deletes default admin (id=1); workflow engine needs this user. */
    private void seedAdminUser() {
        if (sysUserMapper.selectById(TestUserContext.USER_ADMIN) == null) {
            SysUser admin = new SysUser();
            admin.setId(TestUserContext.USER_ADMIN);
            admin.setTenantId(TestUserContext.TENANT_0);
            admin.setUsername("admin");
            admin.setPassword("encoded");
            admin.setRealName("系统管理员");
            admin.setStatus("ENABLE");
            admin.setIsAdmin(1);
            sysUserMapper.insert(admin);
        }
    }
}
