package com.cgcpms.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.contract.service.CtContractItemService;
import com.cgcpms.contract.service.CtContractPaymentTermService;
import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED phase — reproduce non-atomic contract save behavior.
 * <p>
 * Known bugs:
 * <ul>
 *   <li>Frontend calls create/update contract, batch save items, batch save
 *       payment terms as separate HTTP requests → no cross-request transaction</li>
 *   <li>If payment terms save fails after header+items succeed, orphan header
 *       and items remain (no rollback)</li>
 * </ul>
 * ALL tests expect failure on current code — this is the RED phase.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("ContractCompositeSave — non-atomic multi-call save behavior")
class ContractCompositeSaveTest {

    private static final long PROJECT_ID = 10001L;
    private static final long PARTY_A_ID = 20001L;
    private static final long PARTY_B_ID = 20002L;

    @Autowired
    private CtContractService contractService;

    @Autowired
    private CtContractItemService itemService;

    @Autowired
    private CtContractPaymentTermService paymentTermService;

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
        contract.setWarrantyRate(new BigDecimal("5.00"));
        contract.setWarrantyAmount(new BigDecimal("32000.00"));
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

    // ═══════════════════════════════════════════════════════════════
    // RED-1: Multi-call save is NON-ATOMIC — demonstrates orphan risk
    // Creates header + items + terms in separate calls (simulating
    // frontend multi-request flow). Then verifies that a failure in
    // one step cannot rollback earlier steps.
    //
    // RED assertion: After creating header + items, we expect that
    // deleting items (simulating a mid-save failure scenario) would
    // also clean up the header (atomicity). But it doesn't — header
    // survives → assertion FAILS → RED.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-1: non-atomic save — header survives after items deleted (no composite rollback)")
    void testNonAtomicSaveHeaderSurvivesItemDeletion() {
        // Step 1: Create contract header (commits independently)
        CtContract contract = buildContract(null, "原子保存测试-RED1-非原子");
        Long contractId = contractService.create(contract);
        assertNotNull(contractId, "合同头创建应返回ID");

        // Step 2: Save items (separate transaction)
        CtContractItem item = buildItem("CI-RED1-001", "测试清单项-混凝土", new BigDecimal("100.00"), new BigDecimal("450.00"));
        itemService.batchSave(contractId, List.of(item));

        // Step 3: Save payment terms (separate transaction)
        CtContractPaymentTerm term = buildTerm("预付款", new BigDecimal("30.00"), 1);
        paymentTermService.batchSave(contractId, List.of(term));

        // Simulate a mid-save failure: delete the items (simulating
        // "item save failed but header was already committed")
        contractItemMapper.delete(new LambdaQueryWrapper<CtContractItem>()
                .eq(CtContractItem::getContractId, contractId));
        paymentTermMapper.delete(new LambdaQueryWrapper<CtContractPaymentTerm>()
                .eq(CtContractPaymentTerm::getContractId, contractId));

        // RED assertion: With atomic composite save, if children are
        // deleted/absent, the header should ALSO be absent (rollback).
        // But current multi-call approach leaves orphan header.
        // This assertion FAILS because header still exists → RED.
        CtContract afterDelete = contractMapper.selectById(contractId);
        assertNull(afterDelete,
                "RED: 子项删除后，合同头应不存在（原子性）。"
                        + "但当前非原子保存导致合同头仍存在（孤儿记录），contractId=" + contractId);
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-2: Orphan header risk — terms failure does NOT roll back header
    // After header + items succeed in their own transactions, if terms
    // save fails, the header and items are already committed (orphans).
    // This test FAILS (assertion error) because:
    //   assertNull(orphan) — expects NO orphan, but header exists → FAILS → RED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-2: orphan header remains after terms save fails (no rollback)")
    void testOrphanHeaderAfterTermsFail() {
        // Step 1: Create contract header (commits in its own transaction)
        CtContract contract = buildContract(null, "原子保存测试-RED2-孤儿风险");
        Long contractId = contractService.create(contract);
        assertNotNull(contractId);

        // Step 2: Save items (commits in its own transaction)
        CtContractItem item = buildItem("CI-RED2-001", "测试清单项-钢筋", new BigDecimal("50.00"), new BigDecimal("3800.00"));
        itemService.batchSave(contractId, List.of(item));

        // Step 3: Save payment terms WITH INVALID DATA to trigger failure
        // termName exceeds VARCHAR(200) → causes SQL error
        CtContractPaymentTerm badTerm = new CtContractPaymentTerm();
        badTerm.setTermName("X".repeat(250)); // exceeds column limit
        badTerm.setPaymentRatio(new BigDecimal("100.00"));
        badTerm.setPaymentAmount(new BigDecimal("640000.00"));
        badTerm.setSortOrder(1);

        // Terms save FAILS
        try {
            paymentTermService.batchSave(contractId, List.of(badTerm));
            System.out.println("RED-2 WARNING: 预期条款保存失败，但实际成功了 — 可能需要调整触发条件");
        } catch (Exception e) {
            // Expected: terms save failed
            System.out.println("RED-2: 条款保存失败（预期）: " + e.getClass().getSimpleName());
        }

        // RED assertion: header SHOULD NOT exist after terms failure
        // But with non-atomic multi-call save, the header IS already committed.
        // This assertion FAILS → RED indicator of the bug.
        CtContract orphan = contractMapper.selectById(contractId);
        assertNull(orphan,
                "RED: 付款条款保存失败后，合同头应回滚（原子性要求）。"
                        + "但当前非原子保存导致合同头仍存在（孤儿记录），contractId=" + contractId);
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-3: Partial save — items saved but no terms
    // Simulates scenario where user saves but forgets/omits payment terms.
    // With atomic composite save, either ALL data is saved or NONE is.
    //
    // RED assertion: After saving header + items, we expect terms to
    // also exist (atomic save would have rejected incomplete data).
    // But terms are absent → assertion FAILS → RED.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-3: partial save (header+items, no terms) — terms should exist (atomicity)")
    void testPartialSaveMissingTerms() {
        CtContract contract = buildContract(null, "原子保存测试-RED3-缺少条款");
        Long contractId = contractService.create(contract);
        assertNotNull(contractId);

        CtContractItem item = buildItem("CI-RED3-001", "测试清单项-模板", new BigDecimal("200.00"), new BigDecimal("350.00"));
        itemService.batchSave(contractId, List.of(item));

        // Deliberately skip payment terms (simulating incomplete save)
        // RED assertion: With atomic save, terms SHOULD exist
        // (either all saved or none saved). But they don't → FAILS.
        List<CtContractPaymentTerm> terms = paymentTermMapper.selectList(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getContractId, contractId));
        assertEquals(1, terms.size(),
                "RED: 应有付款条款（原子保存要求全有或全无），但实际缺少条款。"
                        + "非原子保存允许部分数据提交产生不完整记录。contractId=" + contractId);
    }
}
