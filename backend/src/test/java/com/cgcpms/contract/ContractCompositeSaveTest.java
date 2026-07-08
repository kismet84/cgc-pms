package com.cgcpms.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.dto.ContractSaveRequest;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.contract.service.CtContractItemService;
import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GREEN phase — 验证复合原子保存 (compositeSave) 的事务一致性。
 * <p>
 * 所有调用走 contractService.compositeSave()，确保 header + items + paymentTerms
 * 在同一 @Transactional 中完成，任一子操作失败即整体回滚。
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("ContractCompositeSave — composite atomic save behavior")
class ContractCompositeSaveTest {

    private static final long PROJECT_ID = 10001L;
    private static final long PARTY_A_ID = 20001L;
    private static final long PARTY_B_ID = 20002L;

    @Autowired
    private CtContractService contractService;

    @Autowired
    private CtContractItemService contractItemService;

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private CtContractItemMapper contractItemMapper;

    @Autowired
    private CtContractPaymentTermMapper paymentTermMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private MdPartnerMapper partnerMapper;

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        seedReferenceData();
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    private void seedReferenceData() {
        if (projectMapper.selectById(PROJECT_ID) == null) {
            PmProject project = new PmProject();
            project.setId(PROJECT_ID);
            project.setProjectCode("PRJ-TEST-CS");
            project.setProjectName("原子保存测试项目");
            project.setProjectType("CONSTRUCTION");
            project.setContractAmount(new BigDecimal("5000000.00"));
            project.setTargetCost(new BigDecimal("4200000.00"));
            project.setStatus("RUNNING");
            project.setApprovalStatus("APPROVED");
            projectMapper.insert(project);
        }

        if (partnerMapper.selectById(PARTY_A_ID) == null) {
            MdPartner partyA = new MdPartner();
            partyA.setId(PARTY_A_ID);
            partyA.setPartnerCode("PT-TEST-CS-A");
            partyA.setPartnerName("原子保存测试甲方");
            partyA.setPartnerType("PARTY_A");
            partyA.setBlacklistFlag(0);
            partyA.setStatus("ENABLE");
            partnerMapper.insert(partyA);
        }

        if (partnerMapper.selectById(PARTY_B_ID) == null) {
            MdPartner partyB = new MdPartner();
            partyB.setId(PARTY_B_ID);
            partyB.setPartnerCode("PT-TEST-CS-B");
            partyB.setPartnerName("原子保存测试乙方");
            partyB.setPartnerType("PARTY_B");
            partyB.setBlacklistFlag(0);
            partyB.setStatus("ENABLE");
            partnerMapper.insert(partyB);
        }
    }

    private CtContract buildContract(String code, String name) {
        CtContract contract = new CtContract();
        contract.setProjectId(PROJECT_ID);
        contract.setContractCode(code);
        contract.setContractName(name);
        contract.setContractType("SUB");
        contract.setPartyAId(PARTY_A_ID);
        contract.setPartyBId(PARTY_B_ID);
        contract.setContractAmount(new BigDecimal("640000.00"));
        contract.setCurrentAmount(new BigDecimal("640000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setTaxRate(new BigDecimal("13.00"));
        contract.setTaxAmount(new BigDecimal("73628.32"));
        contract.setAmountWithoutTax(new BigDecimal("566371.68"));
        contract.setSignedDate(LocalDate.now());
        contract.setPaymentMethod("银行转账");
        contract.setSettlementMethod("按进度结算");
        contract.setContractStatus("DRAFT");
        contract.setApprovalStatus("DRAFT");
        return contract;
    }

    private CtContractItem buildItem(String code, String name, BigDecimal qty, BigDecimal price) {
        CtContractItem item = new CtContractItem();
        item.setItemCode(code);
        item.setItemName(name);
        item.setItemSpec("标准规格");
        item.setUnit("m³");
        item.setQuantity(qty);
        item.setUnitPrice(price);
        item.setAmount(qty.multiply(price));
        item.setTaxRate(new BigDecimal("13.00"));
        item.setTaxAmount(qty.multiply(price).multiply(new BigDecimal("0.13")));
        item.setAmountWithoutTax(qty.multiply(price).multiply(new BigDecimal("0.87")));
        item.setSortOrder(1);
        return item;
    }

    private CtContractPaymentTerm buildTerm(String name, BigDecimal ratio, int order) {
        CtContractPaymentTerm term = new CtContractPaymentTerm();
        term.setTermName(name);
        term.setPaymentRatio(ratio);
        term.setPaymentAmount(new BigDecimal("640000.00").multiply(ratio).divide(new BigDecimal("100")));
        term.setPaymentCondition("工程进度达到约定节点");
        term.setPlannedDate(LocalDate.now().plusMonths(order));
        term.setTermStatus("PENDING");
        term.setSortOrder(order);
        return term;
    }

    private ContractSaveRequest buildRequest(String contractName, List<CtContractItem> items,
                                             List<CtContractPaymentTerm> terms, boolean submit) {
        ContractSaveRequest request = new ContractSaveRequest();
        request.setContract(buildContract(null, contractName));
        request.setItems(items);
        request.setPaymentTerms(terms);
        request.setSubmitForApproval(submit);
        return request;
    }

    private void assertMoneyEquals(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "金额应一致");
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-1: 复合原子保存 — header + items + terms 全量持久化
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GREEN-1: compositeSave persists header + items + terms atomically")
    void testCompositeSaveAllThreePersist() {
        CtContractItem item = buildItem("CI-GRN1-001", "测试清单项-混凝土",
                new BigDecimal("100.00"), new BigDecimal("450.00"));
        CtContractPaymentTerm term = buildTerm("预付款", new BigDecimal("30.00"), 1);

        ContractSaveRequest request = buildRequest("原子保存测试-GREEN1-全量保存",
                List.of(item), List.of(term), false);

        Long contractId = contractService.compositeSave(request);
        assertNotNull(contractId, "复合保存应返回合同 ID");

        // 验证 header
        CtContract saved = contractMapper.selectById(contractId);
        assertNotNull(saved, "合同头应持久化");
        assertEquals("DRAFT", saved.getApprovalStatus());
        assertNotNull(saved.getContractCode(), "合同编号应已生成");

        // 验证 items
        List<CtContractItem> items = contractItemMapper.selectList(
                new LambdaQueryWrapper<CtContractItem>()
                        .eq(CtContractItem::getContractId, contractId));
        assertEquals(1, items.size(), "应持久化 1 条明细项");
        assertEquals("CI-GRN1-001", items.get(0).getItemCode());

        // 验证 terms
        List<CtContractPaymentTerm> terms = paymentTermMapper.selectList(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getContractId, contractId));
        assertEquals(1, terms.size(), "应持久化 1 条付款条款");
        assertEquals("预付款", terms.get(0).getTermName());
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-2: 事务回滚 — 付款条款保存失败时整体回滚
    // 构造 termName 超过 VARCHAR(200) 触发 SQL 错误 → 整个事务回滚
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GREEN-2: transactional rollback on child failure — no orphan header")
    void testTransactionalRollbackOnChildFailure() {
        CtContractItem item = buildItem("CI-GRN2-001", "测试清单项-钢筋",
                new BigDecimal("50.00"), new BigDecimal("3800.00"));

        // termName 超过 VARCHAR(200) — 必定触发 SQL 错误
        CtContractPaymentTerm badTerm = new CtContractPaymentTerm();
        badTerm.setTermName("X".repeat(250));
        badTerm.setPaymentRatio(new BigDecimal("100.00"));
        badTerm.setPaymentAmount(new BigDecimal("640000.00"));
        badTerm.setSortOrder(1);

        ContractSaveRequest request = buildRequest("原子保存测试-GREEN2-回滚",
                List.of(item), List.of(badTerm), false);

        // compositeSave 应抛出异常（terms 保存失败）
        assertThrows(Exception.class, () -> contractService.compositeSave(request),
                "复合保存中任一子操作失败应抛异常并回滚");

        // 验证：没有任何孤儿记录残留（通过名称搜索）
        List<CtContract> orphans = contractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getContractName, "原子保存测试-GREEN2-回滚"));
        assertTrue(orphans.isEmpty(),
                "事务回滚后不应残留合同头（孤儿记录）。"
                        + "当前残留 " + orphans.size() + " 条记录，可能未回滚。");
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-3: 复合保存数据完整性 — 所有字段正确持久化
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GREEN-3: composite save with full data — all fields persisted correctly")
    void testCompositeSaveFullDataIntegrity() {
        CtContractItem item = buildItem("CI-GRN3-001", "测试清单项-模板",
                new BigDecimal("200.00"), new BigDecimal("350.00"));
        CtContractPaymentTerm term1 = buildTerm("首付款", new BigDecimal("40.00"), 1);
        CtContractPaymentTerm term2 = buildTerm("验收款", new BigDecimal("55.00"), 2);
        CtContractPaymentTerm term3 = buildTerm("质保金", new BigDecimal("5.00"), 3);

        ContractSaveRequest request = buildRequest("原子保存测试-GREEN3-完整性",
                List.of(item), List.of(term1, term2, term3), false);

        Long contractId = contractService.compositeSave(request);
        assertNotNull(contractId);

        // 验证 header 字段
        CtContract saved = contractMapper.selectById(contractId);
        assertNotNull(saved);
        assertEquals("SUB", saved.getContractType());
        assertEquals(PROJECT_ID, saved.getProjectId());
        assertEquals(PARTY_A_ID, saved.getPartyAId());
        assertEquals(PARTY_B_ID, saved.getPartyBId());
        assertEquals(0, new BigDecimal("640000.00").compareTo(saved.getContractAmount()));

        // 验证 1 条 item
        List<CtContractItem> items = contractItemMapper.selectList(
                new LambdaQueryWrapper<CtContractItem>()
                        .eq(CtContractItem::getContractId, contractId));
        assertEquals(1, items.size());

        // 验证 3 条 terms
        List<CtContractPaymentTerm> terms = paymentTermMapper.selectList(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getContractId, contractId)
                        .orderByAsc(CtContractPaymentTerm::getSortOrder));
        assertEquals(3, terms.size());
        assertEquals("首付款", terms.get(0).getTermName());
        assertEquals("验收款", terms.get(1).getTermName());
        assertEquals("质保金", terms.get(2).getTermName());
    }

    @Test
    @DisplayName("ISSUE-004-007: 合同金额、清单合计与付款条件金额/日期/状态保持一致")
    void testContractItemsAndPaymentTermsRemainConsistent() {
        CtContract contract = buildContract(null, "ISSUE-004-007-金额日期状态一致性");
        contract.setContractAmount(new BigDecimal("640000.00"));
        contract.setCurrentAmount(new BigDecimal("640000.00"));
        contract.setSignedDate(LocalDate.of(2026, 3, 1));
        contract.setStartDate(LocalDate.of(2026, 3, 15));
        contract.setEndDate(LocalDate.of(2026, 12, 31));

        CtContractItem concrete = buildItem("CI-ISSUE-004-007-001", "混凝土工程",
                new BigDecimal("400.00"), new BigDecimal("700.00"));
        concrete.setSortOrder(1);
        CtContractItem steel = buildItem("CI-ISSUE-004-007-002", "钢筋工程",
                new BigDecimal("120.00"), new BigDecimal("3000.00"));
        steel.setSortOrder(2);

        CtContractPaymentTerm advance = buildTerm("预付款", new BigDecimal("25.00"), 1);
        advance.setPaymentAmount(new BigDecimal("160000.00"));
        advance.setPaymentCondition("合同签订且履约保证提交后支付");
        advance.setPlannedDate(LocalDate.of(2026, 3, 20));
        advance.setTermStatus("PENDING");

        CtContractPaymentTerm progress = buildTerm("进度款", new BigDecimal("50.00"), 2);
        progress.setPaymentAmount(new BigDecimal("320000.00"));
        progress.setPaymentCondition("主体工程完成 50% 后支付");
        progress.setPlannedDate(LocalDate.of(2026, 7, 31));
        progress.setTermStatus("PENDING");

        CtContractPaymentTerm finalPayment = buildTerm("结算款", new BigDecimal("25.00"), 3);
        finalPayment.setPaymentAmount(new BigDecimal("160000.00"));
        finalPayment.setPaymentCondition("竣工结算确认后支付");
        finalPayment.setPlannedDate(LocalDate.of(2026, 12, 31));
        finalPayment.setTermStatus("WAITING_SETTLEMENT");

        ContractSaveRequest request = new ContractSaveRequest();
        request.setContract(contract);
        request.setItems(List.of(concrete, steel));
        request.setPaymentTerms(List.of(advance, progress, finalPayment));
        request.setSubmitForApproval(false);

        Long contractId = contractService.compositeSave(request);

        CtContract saved = contractMapper.selectById(contractId);
        assertNotNull(saved);
        assertMoneyEquals("640000.00", saved.getContractAmount());
        assertMoneyEquals("640000.00", saved.getCurrentAmount());
        assertEquals(LocalDate.of(2026, 3, 1), saved.getSignedDate());
        assertEquals(LocalDate.of(2026, 3, 15), saved.getStartDate());
        assertEquals(LocalDate.of(2026, 12, 31), saved.getEndDate());
        assertEquals("DRAFT", saved.getContractStatus());
        assertEquals(ContractStatusConstants.APPROVAL_DRAFT, saved.getApprovalStatus());

        List<CtContractItem> savedItems = contractItemMapper.selectList(
                new LambdaQueryWrapper<CtContractItem>()
                        .eq(CtContractItem::getContractId, contractId)
                        .orderByAsc(CtContractItem::getSortOrder));
        assertEquals(2, savedItems.size());
        BigDecimal itemTotal = savedItems.stream()
                .map(CtContractItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertMoneyEquals("640000.00", itemTotal);
        assertEquals("CI-ISSUE-004-007-001", savedItems.get(0).getItemCode());
        assertEquals("CI-ISSUE-004-007-002", savedItems.get(1).getItemCode());

        List<CtContractPaymentTerm> savedTerms = paymentTermMapper.selectList(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getContractId, contractId)
                        .orderByAsc(CtContractPaymentTerm::getSortOrder));
        assertEquals(3, savedTerms.size());
        BigDecimal termAmountTotal = savedTerms.stream()
                .map(CtContractPaymentTerm::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal termRatioTotal = savedTerms.stream()
                .map(CtContractPaymentTerm::getPaymentRatio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertMoneyEquals("640000.00", termAmountTotal);
        assertMoneyEquals("100.00", termRatioTotal);
        assertEquals(LocalDate.of(2026, 3, 20), savedTerms.get(0).getPlannedDate());
        assertEquals(LocalDate.of(2026, 7, 31), savedTerms.get(1).getPlannedDate());
        assertEquals(LocalDate.of(2026, 12, 31), savedTerms.get(2).getPlannedDate());
        assertEquals("PENDING", savedTerms.get(0).getTermStatus());
        assertEquals("PENDING", savedTerms.get(1).getTermStatus());
        assertEquals("WAITING_SETTLEMENT", savedTerms.get(2).getTermStatus());
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-4: 复合保存不触发审批 — submitForApproval 标志被忽略
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GREEN-4: compositeSave never triggers approval — submitForApproval flag ignored")
    void testCompositeSaveNeverTriggersApproval() {
        CtContractItem item = buildItem("CI-GRN4-001", "测试清单项-装饰",
                new BigDecimal("80.00"), new BigDecimal("200.00"));
        CtContractPaymentTerm term = buildTerm("进度款", new BigDecimal("50.00"), 1);

        // 即使 submitForApproval=true，也不会触发审批
        ContractSaveRequest request = buildRequest("原子保存测试-GREEN4-无审批",
                List.of(item), List.of(term), true);

        Long contractId = contractService.compositeSave(request);
        assertNotNull(contractId);

        // 验证合同状态仍为 DRAFT
        CtContract saved = contractMapper.selectById(contractId);
        assertNotNull(saved);
        assertEquals(ContractStatusConstants.APPROVAL_DRAFT, saved.getApprovalStatus(),
                "compositeSave 不应触发审批，状态应为 DRAFT");
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-5: APPROVED 合同拒绝编辑 — compositeSave + item update
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GREEN-5: editing approved contract via compositeSave throws CONTRACT_NOT_EDITABLE")
    void testApprovedContractRejectsCompositeEdit() {
        // 创建并插入一个 APPROVED 状态的合同
        CtContract approvedContract = buildContract(null, "已审批合同-禁止编辑测试");
        approvedContract.setContractCode("CT-TEST-APPROVED-NOEDIT");
        approvedContract.setApprovalStatus(ContractStatusConstants.APPROVAL_APPROVED);
        approvedContract.setTenantId(TestUserContext.TENANT_0);
        contractMapper.insert(approvedContract);
        Long approvedId = approvedContract.getId();

        // 尝试通过 compositeSave 编辑
        CtContract editContract = buildContract(null, "尝试修改已审批合同");
        editContract.setId(approvedId);

        ContractSaveRequest request = new ContractSaveRequest();
        request.setContract(editContract);
        request.setSubmitForApproval(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            contractService.compositeSave(request);
        }, "已审批合同编辑应抛出异常");
        assertEquals("CONTRACT_NOT_EDITABLE", ex.getCode(), "错误码应为 CONTRACT_NOT_EDITABLE");
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-6: APPROVED 合同清单项更新拒绝
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GREEN-6: approved contract item update returns CONTRACT_NOT_EDITABLE")
    void testApprovedContractItemUpdateRejected() {
        // 创建并插入一个 APPROVED 状态的合同
        CtContract approvedContract = buildContract(null, "已审批合同-清单项禁止编辑");
        approvedContract.setContractCode("CT-TEST-APPROVED-ITEM-NOEDIT");
        approvedContract.setApprovalStatus(ContractStatusConstants.APPROVAL_APPROVED);
        approvedContract.setTenantId(TestUserContext.TENANT_0);
        contractMapper.insert(approvedContract);
        Long approvedId = approvedContract.getId();

        // 插入一个清单项
        CtContractItem item = buildItem("CI-TEST-LOCKED", "锁定清单项",
                new BigDecimal("10.00"), new BigDecimal("100.00"));
        item.setContractId(approvedId);
        item.setTenantId(TestUserContext.TENANT_0);
        contractItemMapper.insert(item);

        // 尝试更新该清单项
        item.setItemName("尝试修改锁定合同的清单项");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            contractItemService.update(item);
        }, "已审批合同的清单项更新应抛出异常");
        assertEquals("CONTRACT_NOT_EDITABLE", ex.getCode(), "错误码应为 CONTRACT_NOT_EDITABLE");
    }
}
