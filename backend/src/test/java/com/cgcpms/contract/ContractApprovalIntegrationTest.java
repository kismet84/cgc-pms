package com.cgcpms.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.handler.ContractWorkflowHandler;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.contract.vo.ContractApprovalRecordVO;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class ContractApprovalIntegrationTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    /** Demo data: DRAFT contract CT-2026-003 (id=30003) */
    private static final long DRAFT_CONTRACT_ID = 30003L;

    /** Demo data: APPROVED contract CT-2026-001 (id=30001, has items for cost idempotency) */
    private static final long APPROVED_CONTRACT_ID = 30001L;

    @Autowired
    private CtContractService contractService;

    @Autowired
    private ContractWorkflowHandler contractHandler;

    @Autowired
    private CostGenerationService costGenerationService;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private CtContractItemMapper contractItemMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private MdPartnerMapper partnerMapper;

    @Autowired
    private CostItemMapper costItemMapper;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private WfRecordMapper wfRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .build());
        seedAdminUser();
        seedRequiredContractData();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private void seedAdminUser() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, USER_ADMIN);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    USER_ADMIN, TENANT_0, "admin",
                    "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                    "系统管理员", "13800000000", "admin@cgc-pms.com",
                    "ENABLE", 1, USER_ADMIN, "测试种子数据");
        }
    }

    private void seedRequiredContractData() {
        if (projectMapper.selectById(10001L) == null) {
            PmProject project = new PmProject();
            project.setId(10001L);
            project.setProjectCode("PRJ-TEST-001");
            project.setProjectName("测试项目");
            project.setProjectType("CONSTRUCTION");
            project.setContractAmount(new BigDecimal("5000000.00"));
            project.setTargetCost(new BigDecimal("4200000.00"));
            project.setStatus("RUNNING");
            project.setApprovalStatus("APPROVED");
            projectMapper.insert(project);
        }

        if (partnerMapper.selectById(20001L) == null) {
            MdPartner partyA = new MdPartner();
            partyA.setId(20001L);
            partyA.setPartnerCode("PT-TEST-A");
            partyA.setPartnerName("测试甲方");
            partyA.setPartnerType("PARTY_A");
            partyA.setBlacklistFlag(0);
            partyA.setStatus("ENABLE");
            partnerMapper.insert(partyA);
        }

        if (partnerMapper.selectById(20002L) == null) {
            MdPartner partyB = new MdPartner();
            partyB.setId(20002L);
            partyB.setPartnerCode("PT-TEST-B");
            partyB.setPartnerName("测试乙方");
            partyB.setPartnerType("PARTY_B");
            partyB.setBlacklistFlag(0);
            partyB.setStatus("ENABLE");
            partnerMapper.insert(partyB);
        }

        ensureContract(DRAFT_CONTRACT_ID, "CT-TEST-APPROVAL-DRAFT", "工程造价咨询服务合同",
                ContractStatusConstants.STATUS_DRAFT, ContractStatusConstants.APPROVAL_DRAFT);
        ensureContract(APPROVED_CONTRACT_ID, "CT-TEST-APPROVAL-APPROVED", "已审批成本生成合同",
                ContractStatusConstants.STATUS_PERFORMING, ContractStatusConstants.APPROVAL_APPROVED);
    }

    private void ensureContract(Long id, String contractCode, String contractName,
                                String contractStatus, String approvalStatus) {
        CtContract existing = contractMapper.selectById(id);
        if (existing != null) {
            // Reset to the expected state — V90 demo data may have inserted it with a
            // different status, and a previous test may have modified it in a rolled-back
            // transaction that left the status stale in H2.
            existing.setContractStatus(contractStatus);
            existing.setApprovalStatus(approvalStatus);
            existing.setContractCode(contractCode);
            existing.setContractName(contractName);
            contractMapper.updateById(existing);
            return;
        }
        CtContract contract = new CtContract();
        contract.setId(id);
        contract.setProjectId(10001L);
        contract.setContractCode(contractCode);
        contract.setContractName(contractName);
        contract.setContractType("SUB");
        contract.setPartyAId(20001L);
        contract.setPartyBId(20002L);
        contract.setContractAmount(new BigDecimal("640000.00"));
        contract.setCurrentAmount(new BigDecimal("640000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setTaxRate(new BigDecimal("13.00"));
        contract.setTaxAmount(new BigDecimal("73628.32"));
        contract.setAmountWithoutTax(new BigDecimal("566371.68"));
        contract.setSignedDate(LocalDate.now());
        contract.setPaymentMethod("银行转账");
        contract.setSettlementMethod("按进度结算");
        contract.setContractStatus(contractStatus);
        contract.setApprovalStatus(approvalStatus);
        contract.setCostGeneratedFlag(0);
        contractMapper.insert(contract);
    }

    // ═══════════════════════════════════════════════════════════
    // 场景1: 提交合同审批
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("场景1: 提交合同审批 → 状态变为APPROVING，生成wf实例和记录")
    void test01_submitContractForApproval() {
        // 验证初始状态为 DRAFT
        CtContract before = contractMapper.selectById(DRAFT_CONTRACT_ID);
        assertNotNull(before, "合同 30003 应存在");
        assertEquals(ContractStatusConstants.APPROVAL_DRAFT, before.getApprovalStatus());

        // 提交审批
        contractService.submitForApproval(DRAFT_CONTRACT_ID);

        // 断言审批状态已变为 APPROVING
        CtContract after = contractMapper.selectById(DRAFT_CONTRACT_ID);
        assertEquals(ContractStatusConstants.APPROVAL_APPROVING, after.getApprovalStatus(),
                "提交后审批状态应为 APPROVING");

        // 断言 wf_instance 表中存在对应的审批实例
        WfInstance instance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL)
                        .eq(WfInstance::getBusinessId, DRAFT_CONTRACT_ID));
        assertNotNull(instance, "应生成审批实例");
        assertEquals("RUNNING", instance.getInstanceStatus(), "实例状态应为 RUNNING");
        assertEquals(1, instance.getCurrentRound(), "首次提交轮次应为 1");
        assertEquals(USER_ADMIN, instance.getInitiatorId());
        assertEquals("工程造价咨询服务合同", instance.getTitle());

        // 断言 wf_record 表中有 SUBMIT 记录
        List<WfRecord> records = wfRecordMapper.selectList(
                new LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getInstanceId, instance.getId())
                        .eq(WfRecord::getActionType, "SUBMIT"));
        assertFalse(records.isEmpty(), "应生成 SUBMIT 审批记录");

        System.out.println("✅ 场景1 通过: contractId=" + DRAFT_CONTRACT_ID
                + ", approvalStatus=" + after.getApprovalStatus()
                + ", instanceId=" + instance.getId()
                + ", submitRecords=" + records.size());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景2: 审批中守卫 — 非 DRAFT 状态禁止编辑
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("场景2: 审批中合同不可编辑 → 抛出 CONTRACT_NOT_EDITABLE")
    void test02_approvalStatusGuard() {
        // 先提交审批，使其进入 APPROVING 状态
        contractService.submitForApproval(DRAFT_CONTRACT_ID);

        // 构造编辑请求（尝试修改合同名称）
        CtContract editContract = new CtContract();
        editContract.setId(DRAFT_CONTRACT_ID);
        editContract.setContractName("尝试编辑审批中的合同名称");

        // 断言抛出 BusinessException
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            contractService.update(editContract);
        }, "审批中合同编辑应抛出异常");
        assertEquals("CONTRACT_NOT_EDITABLE", ex.getCode(), "错误码应为 CONTRACT_NOT_EDITABLE");
        assertTrue(ex.getMessage().contains("不可编辑"), "错误消息应包含'不可编辑'");

        System.out.println("✅ 场景2 通过: 审批中合同编辑被正确拦截, code=" + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景3: 成本生成幂等
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("场景3: 成本生成幂等 → 重复调用不产生重复记录")
    void test03_costGenerationIdempotent() {
        // 为 APPROVED 合同插入清单项（generateLockedCost 需要清单明细）
        CtContractItem item1 = new CtContractItem();
        item1.setTenantId(0L);
        item1.setContractId(APPROVED_CONTRACT_ID);
        item1.setItemCode("CI-TEST-001");
        item1.setItemName("测试清单项-砼浇筑");
        item1.setItemSpec("C30");
        item1.setUnit("m³");
        item1.setQuantity(new BigDecimal("1000.00"));
        item1.setUnitPrice(new BigDecimal("450.00"));
        item1.setAmount(new BigDecimal("450000.00"));
        item1.setTaxRate(new BigDecimal("13.00"));
        item1.setTaxAmount(new BigDecimal("51769.91"));
        item1.setAmountWithoutTax(new BigDecimal("398230.09"));
        item1.setSortOrder(1);
        contractItemMapper.insert(item1);

        CtContractItem item2 = new CtContractItem();
        item2.setTenantId(0L);
        item2.setContractId(APPROVED_CONTRACT_ID);
        item2.setItemCode("CI-TEST-002");
        item2.setItemName("测试清单项-钢筋");
        item2.setItemSpec("HRB400 φ25");
        item2.setUnit("t");
        item2.setQuantity(new BigDecimal("50.00"));
        item2.setUnitPrice(new BigDecimal("3800.00"));
        item2.setAmount(new BigDecimal("190000.00"));
        item2.setTaxRate(new BigDecimal("13.00"));
        item2.setTaxAmount(new BigDecimal("21858.41"));
        item2.setAmountWithoutTax(new BigDecimal("168141.59"));
        item2.setSortOrder(2);
        contractItemMapper.insert(item2);

        // 第一次调用：生成锁定成本
        costGenerationService.generateLockedCost(APPROVED_CONTRACT_ID);

        // 查询第一次生成的 cost_item
        List<CostItem> firstBatch = costItemMapper.selectList(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, CostGenerationService.SOURCE_TYPE_CONTRACT)
                        .eq(CostItem::getSourceId, APPROVED_CONTRACT_ID));
        assertFalse(firstBatch.isEmpty(), "第一次调用应生成成本记录");
        assertEquals(2, firstBatch.size(), "应为2条清单生成2条成本记录");

        // 验证 source_type 和 source_item_id
        for (CostItem cost : firstBatch) {
            assertEquals(CostGenerationService.SOURCE_TYPE_CONTRACT, cost.getSourceType());
            assertNotNull(cost.getSourceItemId(), "sourceItemId 应对应合同清单项");
            boolean matchesItem = cost.getSourceItemId().equals(item1.getId())
                    || cost.getSourceItemId().equals(item2.getId());
            assertTrue(matchesItem, "sourceItemId 应匹配插入的清单项ID: " + cost.getSourceItemId());
            assertEquals(CostGenerationService.COST_STATUS_CONFIRMED, cost.getCostStatus());
        }

        // 第二次调用：幂等 — 不产生重复记录
        costGenerationService.generateLockedCost(APPROVED_CONTRACT_ID);

        long countAfterSecond = costItemMapper.selectCount(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, CostGenerationService.SOURCE_TYPE_CONTRACT)
                        .eq(CostItem::getSourceId, APPROVED_CONTRACT_ID));
        assertEquals(2, countAfterSecond, "幂等：第二次调用不应产生重复记录（仍为2条）");

        System.out.println("✅ 场景3 通过: 首次生成" + firstBatch.size() + "条, 第二次后仍为" + countAfterSecond + "条");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景4: 重复提交拒绝
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("场景4: 重复提交审批 → 抛出 CONTRACT_ALREADY_SUBMITTED")
    void test04_submitDuplicateRejected() {
        // 第一次提交成功
        contractService.submitForApproval(DRAFT_CONTRACT_ID);
        CtContract afterFirst = contractMapper.selectById(DRAFT_CONTRACT_ID);
        assertEquals(ContractStatusConstants.APPROVAL_APPROVING, afterFirst.getApprovalStatus());

        // 第二次提交应失败
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            contractService.submitForApproval(DRAFT_CONTRACT_ID);
        }, "重复提交应抛出异常");
        assertEquals("CONTRACT_ALREADY_SUBMITTED", ex.getCode(), "错误码应为 CONTRACT_ALREADY_SUBMITTED");

        // 状态仍为 APPROVING（未被第二次提交改变）
        CtContract afterSecond = contractMapper.selectById(DRAFT_CONTRACT_ID);
        assertEquals(ContractStatusConstants.APPROVAL_APPROVING, afterSecond.getApprovalStatus(),
                "重复提交被拒绝后状态不应改变");

        System.out.println("✅ 场景4 通过: 重复提交被拒绝, code=" + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景5: 审批记录查询
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("场景5: 审批记录查询 → 返回审批记录（含 SUBMIT）")
    void test05_getApprovalRecords() {
        // 提交审批
        contractService.submitForApproval(DRAFT_CONTRACT_ID);

        // 查询审批记录
        List<ContractApprovalRecordVO> records = contractService.getApprovalRecords(DRAFT_CONTRACT_ID);

        assertNotNull(records, "审批记录列表不应为 null");
        assertFalse(records.isEmpty(), "提交后应至少有一条审批记录");

        // 验证至少有一条 SUBMIT 类型记录
        boolean hasSubmitRecord = records.stream()
                .anyMatch(r -> "SUBMIT".equals(r.getActionType()));
        assertTrue(hasSubmitRecord, "审批记录中应包含 SUBMIT 类型的记录");

        // 验证记录字段完整性
        ContractApprovalRecordVO firstRecord = records.get(0);
        assertNotNull(firstRecord.getActionType(), "操作类型不应为空");
        assertNotNull(firstRecord.getOperatorName(), "操作人不应为空");
        assertNotNull(firstRecord.getCreatedAt(), "操作时间不应为空");

        System.out.println("✅ 场景5 通过: 共 " + records.size() + " 条审批记录, "
                + "包含SUBMIT=" + hasSubmitRecord);
    }
}
