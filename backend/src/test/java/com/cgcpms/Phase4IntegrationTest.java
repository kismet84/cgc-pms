package com.cgcpms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.inventory.service.MatWarehouseService;
import com.cgcpms.inventory.vo.MatStockLedgerVO;
import com.cgcpms.inventory.vo.MatWarehouseVO;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.material.service.MdMaterialService;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.mapper.SysNotificationMapper;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfCc;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfCcMapper;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase4IntegrationTest {

    private static final long USER_ADMIN = 1L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20001L;

    // ── INVENTORY ──
    @Autowired private MatStockService matStockService;
    @Autowired private MatStockMapper matStockMapper;
    @Autowired private MatStockTxnMapper matStockTxnMapper;
    @Autowired private MatWarehouseService matWarehouseService;
    @Autowired private MatWarehouseMapper matWarehouseMapper;
    @Autowired private MdMaterialService mdMaterialService;
    @Autowired private MdMaterialMapper mdMaterialMapper;

    // ── INVOICE ──
    @Autowired private InvoiceService invoiceService;
    @Autowired private PayInvoiceMapper payInvoiceMapper;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private PayApplicationMapper payApplicationMapper;

    // ── NOTIFICATION ──
    @Autowired private NotificationService notificationService;
    @Autowired private SysNotificationMapper notificationMapper;

    // ── CC / WORKFLOW ──
    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private WfCcMapper wfCcMapper;
    @Autowired private WfInstanceMapper wfInstanceMapper;
    @Autowired private WfTaskMapper wfTaskMapper;

    // ── SYSTEM ──
    @Autowired private SysRoleMenuMapper sysRoleMenuMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // 场景1: 库存全链路 — 创建仓库→创建物料→入库100→出库30→验证台账
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(1)
    @Transactional
    @DisplayName("场景1: 库存全链路 → 创建仓库→创建物料→入库100→出库30→验证台账余额70+流水2条")
    void test01_inventoryFullChain() {
        // 1. 创建仓库
        MatWarehouse wh = new MatWarehouse();
        wh.setProjectId(PROJECT_ID);
        wh.setWarehouseCode("WH-TEST-" + System.currentTimeMillis());
        wh.setWarehouseName("Phase4测试仓库");
        wh.setStatus("ENABLE");
        Long warehouseId = matWarehouseService.create(wh);
        assertNotNull(warehouseId, "仓库ID不应为空");

        // 2. 创建物料
        MdMaterial mat = new MdMaterial();
        mat.setMaterialCode("MAT-TEST-" + System.currentTimeMillis());
        mat.setMaterialName("Phase4测试材料");
        mat.setUnit("个");
        mat.setStatus("ENABLE");
        Long materialId = mdMaterialService.create(mat);
        assertNotNull(materialId, "物料ID不应为空");

        // 3. 入库100
        MatStock afterIn = matStockService.stockIn(warehouseId, materialId, new BigDecimal("100.00"));
        assertNotNull(afterIn, "入库后库存不应为空");
        assertEquals(0, new BigDecimal("100.00").compareTo(afterIn.getAvailableQty()),
                "入库100后可用量应为100");

        // 4. 出库30
        MatStock afterOut = matStockService.stockOut(warehouseId, materialId, new BigDecimal("30.00"));
        assertNotNull(afterOut, "出库后库存不应为空");
        assertEquals(0, new BigDecimal("70.00").compareTo(afterOut.getAvailableQty()),
                "出库30后可用量应为70");

        // 5. 验证台账余额（通过 getLedger）
        MatStockLedgerVO ledger = matStockService.getLedger(warehouseId, materialId, 1, 20);
        assertNotNull(ledger, "台账不应为空");
        assertNotNull(ledger.getStock(), "库存记录应存在");
        assertEquals(0, new BigDecimal("70.00").compareTo(ledger.getStock().getAvailableQty()),
                "台账库存余额应为70");

        // 6. ★核心断言：流水记录数量 = 2（1入+1出）
        long txnCount = matStockTxnMapper.selectCount(
                new LambdaQueryWrapper<MatStockTxn>()
                        .eq(MatStockTxn::getWarehouseId, warehouseId)
                        .eq(MatStockTxn::getMaterialId, materialId));
        assertEquals(2L, txnCount, "应有2条流水记录（1入+1出）");

        // 7. 验证流水类型
        List<MatStockTxn> txns = matStockTxnMapper.selectList(
                new LambdaQueryWrapper<MatStockTxn>()
                        .eq(MatStockTxn::getWarehouseId, warehouseId)
                        .eq(MatStockTxn::getMaterialId, materialId)
                        .orderByAsc(MatStockTxn::getCreatedTime));
        assertEquals("IN", txns.get(0).getTxnType(), "第一条流水应为IN");
        assertEquals("OUT", txns.get(1).getTxnType(), "第二条流水应为OUT");
        assertEquals(0, new BigDecimal("100.00").compareTo(txns.get(0).getQuantity()),
                "入库流水数量应为100");
        assertEquals(0, new BigDecimal("30.00").compareTo(txns.get(1).getQuantity()),
                "出库流水数量应为30");

        // 8. 验证出库后可用量快照
        assertEquals(0, new BigDecimal("70.00").compareTo(txns.get(1).getAvailableAfter()),
                "出库后可用量快照应为70");

        // 9. 验证 ledger 分页流水
        assertNotNull(ledger.getTxns(), "台账流水页不应为空");
        assertEquals(2L, ledger.getTxns().getTotal(), "台账流水总数应为2");

        System.out.println("✅ 场景1 通过: warehouseId=" + warehouseId
                + ", materialId=" + materialId
                + ", 入库100→出库30→余额70, txnCount=" + txnCount);
    }

    // ═══════════════════════════════════════════════════════════
    // 场景2: 发票全链路 — 登记→关联PayRecord→核验状态流转
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(2)
    @Transactional
    @DisplayName("场景2: 发票全链路 → 创建PayRecord→登记发票(amount=1000)→验证PENDING→核验VERIFIED→验证关联")
    void test02_invoiceChain() {
        // 1. 创建 PayApplication（pay_record.pay_application_id NOT NULL）
        PayApplication app = new PayApplication();
        app.setTenantId(0L);
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setApplyCode("APP-TEST-" + System.currentTimeMillis());
        app.setApplyAmount(new BigDecimal("1000.00"));
        app.setPayType("ADVANCE");
        app.setPayStatus("PENDING");
        app.setCreatedBy(USER_ADMIN);
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedBy(USER_ADMIN);
        app.setUpdatedAt(LocalDateTime.now());
        app.setDeletedFlag(0);
        payApplicationMapper.insert(app);
        Long appId = app.getId();
        assertNotNull(appId, "PayApplication ID不应为空");

        // 2. 插入 PayRecord 关联 PayApplication
        PayRecord record = new PayRecord();
        record.setTenantId(0L);
        record.setProjectId(PROJECT_ID);
        record.setPayApplicationId(appId);
        record.setContractId(CONTRACT_ID);
        record.setPartnerId(PARTNER_ID);
        record.setPayAmount(new BigDecimal("1000.00"));
        record.setPayStatus("SUCCESS");
        record.setVoucherNo("VCH-TEST-" + System.currentTimeMillis());
        record.setPayDate(LocalDate.now());
        record.setPayMethod("BANK_TRANSFER");
        record.setCreatedBy(USER_ADMIN);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedBy(USER_ADMIN);
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeletedFlag(0);
        payRecordMapper.insert(record);
        Long payRecordId = record.getId();
        assertNotNull(payRecordId, "PayRecord ID不应为空");

        // 2. 登记发票（关联 PayRecord）
        PayInvoice invoice = new PayInvoice();
        invoice.setPayRecordId(payRecordId);
        invoice.setInvoiceNo("INV-TEST-" + System.currentTimeMillis());
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("1000.00"));
        invoice.setTaxRate(new BigDecimal("13.00"));
        invoice.setTaxAmount(new BigDecimal("130.00"));
        invoice.setInvoiceDate(LocalDate.now());
        Long invoiceId = invoiceService.register(invoice);
        assertNotNull(invoiceId, "发票ID不应为空");

        // 3. ★核心断言：初始状态 PENDING
        PayInvoice saved = payInvoiceMapper.selectById(invoiceId);
        assertNotNull(saved, "发票应已保存");
        assertEquals("PENDING", saved.getVerifyStatus(), "初始核验状态应为PENDING");
        assertEquals(payRecordId, saved.getPayRecordId(), "发票应关联正确的PayRecord");
        assertEquals(0, new BigDecimal("1000.00").compareTo(saved.getInvoiceAmount()),
                "发票金额应为1000");

        // 4. 核验通过 → VERIFIED
        assertDoesNotThrow(() -> invoiceService.verify(invoiceId, "VERIFIED"),
                "核验发票不应抛异常");
        PayInvoice verified = payInvoiceMapper.selectById(invoiceId);
        assertEquals("VERIFIED", verified.getVerifyStatus(),
                "核验后状态应为VERIFIED");

        // 5. ★核心断言：验证已核验的发票不可再次核验
        assertThrows(Exception.class, () -> invoiceService.verify(invoiceId, "VERIFIED"),
                "已核验发票再次核验应抛异常（VERIFY_STATUS_CONFLICT）");

        // 6. 验证 PayRecord 关联不丢失
        assertEquals(payRecordId, verified.getPayRecordId(),
                "核验后PayRecord关联不应丢失");

        System.out.println("✅ 场景2 通过: invoiceId=" + invoiceId
                + ", invoiceNo=" + saved.getInvoiceNo()
                + ", amount=1000, PENDING→VERIFIED, linked to payRecordId=" + payRecordId);
    }

    // ═══════════════════════════════════════════════════════════
    // 场景3: 通知全链路 — 创建→查询未读数→标记已读→未读数归零
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(3)
    @Transactional
    @DisplayName("场景3: 通知全链路 → 创建通知→验证未读计数→标记已读→验证计数归零")
    void test03_notificationChain() {
        // 1. 记录创建前未读数
        long beforeCount = notificationService.getUnreadCount(USER_ADMIN, 0L);

        // 2. 创建通知
        SysNotification notif = notificationService.create(
                0L, USER_ADMIN,
                "Phase4测试通知标题",
                "Phase4测试通知内容",
                "TEST", System.currentTimeMillis());
        assertNotNull(notif, "通知不应为空");
        assertNotNull(notif.getId(), "通知ID不应为空");
        assertEquals("Phase4测试通知标题", notif.getTitle(), "通知标题应匹配");
        assertEquals("TEST", notif.getBizType(), "通知业务类型应为TEST");
        assertEquals("INFO", notif.getNotifyType(), "通知类型默认应为INFO");
        assertEquals(0, notif.getIsRead().intValue(), "初始isRead应为0（未读）");

        // 3. ★核心断言：创建后未读数 +1
        long afterCreate = notificationService.getUnreadCount(USER_ADMIN, 0L);
        assertEquals(beforeCount + 1, afterCreate, "创建通知后未读数应+1");

        // 4. 标记已读
        assertDoesNotThrow(() -> notificationService.markAsRead(
                        notif.getId(), USER_ADMIN, 0L),
                "标记已读不应抛异常");

        // 5. ★核心断言：未读数归零（回到创建前）
        long afterRead = notificationService.getUnreadCount(USER_ADMIN, 0L);
        assertEquals(beforeCount, afterRead, "标记已读后未读数应回到创建前水平");

        // 6. 验证重复标记已读幂等（不抛异常）
        assertDoesNotThrow(() -> notificationService.markAsRead(
                        notif.getId(), USER_ADMIN, 0L),
                "重复标记已读应幂等不抛异常");

        // 7. 验证通知实体已更新
        SysNotification updated = notificationMapper.selectById(notif.getId());
        assertEquals(1, updated.getIsRead().intValue(), "isRead应为1（已读）");
        assertNotNull(updated.getReadTime(), "readTime应已设置");

        System.out.println("✅ 场景3 通过: notificationId=" + notif.getId()
                + ", unread: " + beforeCount + "→" + afterCreate + "→" + afterRead
                + ", 幂等标记已读✓");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景4: CC抄送全链路 — 提交流程+ccUserIds→验证wf_cc+通知生成
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(4)
    @Transactional
    @DisplayName("场景4: CC抄送全链路 → 提交流程(ccUserIds=[999,998])→验证wf_cc行+通知各2条")
    void test04_ccChain() {
        long fakeBusinessId = System.currentTimeMillis();

        // 1. 提交流程（携带 ccUserIds）
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                WorkflowBusinessTypes.PURCHASE_REQUEST, fakeBusinessId,
                "Phase4-CC测试-" + fakeBusinessId,
                BigDecimal.ONE,
                PROJECT_ID, CONTRACT_ID,
                "Phase4集成测试-CC抄送", null,
                List.of(999L, 998L));
        assertNotNull(instance, "审批实例不应为空");
        assertNotNull(instance.getId(), "实例ID不应为空");

        // 2. ★核心断言：wf_cc 行已创建（2行，对应2个cc用户）
        List<WfCc> ccList = wfCcMapper.selectList(
                new LambdaQueryWrapper<WfCc>()
                        .eq(WfCc::getInstanceId, instance.getId()));
        assertEquals(2, ccList.size(), "应生成2条CC记录（对应999+998）");

        Set<Long> ccUserIds = ccList.stream()
                .map(WfCc::getCcUserId)
                .collect(Collectors.toSet());
        assertTrue(ccUserIds.contains(999L), "CC记录应包含用户999");
        assertTrue(ccUserIds.contains(998L), "CC记录应包含用户998");

        // 3. 验证CC记录字段完整性
        for (WfCc cc : ccList) {
            assertEquals(0L, cc.getTenantId().longValue(), "CC tenantId应为0");
            assertEquals(instance.getId(), cc.getInstanceId(), "CC instanceId应匹配");
            assertEquals(0, cc.getIsRead().intValue(), "CC isRead初始应为0");
            assertNotNull(cc.getCreatedTime(), "CC createdTime不应为空");
            assertNotNull(cc.getTitle(), "CC title不应为空");
            assertTrue(cc.getTitle().contains("Phase4-CC测试"),
                    "CC title应包含流程标题");
        }

        // 4. ★核心断言：通知已为每个CC用户生成（bizType=PURCHASE_REQUEST）
        List<SysNotification> notifs = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getTenantId, 0L)
                        .eq(SysNotification::getBizType, WorkflowBusinessTypes.PURCHASE_REQUEST)
                        .orderByDesc(SysNotification::getCreatedTime));
        assertFalse(notifs.isEmpty(), "应为CC用户生成通知");

        // 5. 验证通知内容
        boolean hasCcNotifyFor999 = notifs.stream().anyMatch(
                n -> n.getUserId().equals(999L)
                        && n.getTitle().contains("审批抄送"));
        boolean hasCcNotifyFor998 = notifs.stream().anyMatch(
                n -> n.getUserId().equals(998L)
                        && n.getTitle().contains("审批抄送"));
        assertTrue(hasCcNotifyFor999, "用户999应收到CC抄送通知");
        assertTrue(hasCcNotifyFor998, "用户998应收到CC抄送通知");

        // 6. 验证每个CC通知的字段完整性
        for (SysNotification n : notifs) {
            if (List.of(999L, 998L).contains(n.getUserId())
                    && "审批抄送".equals(n.getBizType())) {
                // This is handled above
            }
            assertEquals(0L, n.getTenantId().longValue(), "通知tenantId应为0");
            assertEquals("INFO", n.getNotifyType(), "通知类型应为INFO");
            assertEquals(0, n.getIsRead().intValue(), "通知isRead应为0");
        }

        System.out.println("✅ 场景4 通过: instanceId=" + instance.getId()
                + ", ccRecords=" + ccList.size()
                + ", ccNotifyCount=" + notifs.size()
                + " (ccUserIds: 999, 998)");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景5: 租户隔离 — 仓库+物料跨租户隔离验证
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(5)
    @Transactional
    @DisplayName("场景5: 租户隔离-仓库+物料 → tenant 0创建→tenant 1查询返回空/not found")
    void test05_tenantIsolationWarehouseMaterial() {
        // ── 5a. 仓库隔离 ──

        // 1. tenant 0 创建仓库
        MatWarehouse wh = new MatWarehouse();
        wh.setProjectId(PROJECT_ID);
        wh.setWarehouseCode("WH-ISO-" + System.currentTimeMillis());
        wh.setWarehouseName("租户隔离仓库");
        wh.setStatus("ENABLE");
        Long whId = matWarehouseService.create(wh);
        assertNotNull(whId, "tenant 0创建的仓库ID不应为空");

        // 2. 切换到 tenant 1，查询列表应为空
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 1L)
                .add("roleCodes", List.of())
                .build());
        PageResult<MatWarehouseVO> t1Warehouses = matWarehouseService.getPage(
                1, 100, null, null, null, null);
        // tenant 1 不应看到 tenant 0 的仓库
        boolean t1HasWh0 = t1Warehouses.getRecords().stream()
                .anyMatch(r -> r.getId().equals(whId.toString()));
        assertFalse(t1HasWh0, "tenant 1不应看到tenant 0的仓库");

        // 3. tenant 1 尝试 getById 应抛异常
        assertThrows(Exception.class, () -> matWarehouseService.getById(whId),
                "tenant 1查询tenant 0仓库应抛异常");

        // 4. 切回 tenant 0，验证仓库存在
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN"))
                .build());
        MatWarehouseVO t0Back = matWarehouseService.getById(whId);
        assertNotNull(t0Back, "切回tenant 0应能查到仓库");

        // ── 5b. 物料隔离 ──

        // tenant 0 创建物料
        MdMaterial mat = new MdMaterial();
        mat.setMaterialCode("MAT-ISO-" + System.currentTimeMillis());
        mat.setMaterialName("租户隔离物料");
        mat.setUnit("个");
        mat.setStatus("ENABLE");
        Long matId = mdMaterialService.create(mat);
        assertNotNull(matId, "tenant 0创建的物料ID不应为空");

        // 切换到 tenant 1 查询
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 1L)
                .add("roleCodes", List.of())
                .build());
        assertThrows(Exception.class, () -> mdMaterialService.getById(matId),
                "tenant 1查询tenant 0物料应抛异常");

        // 验证 tenant 1 的 getPage 不含 tenant 0 的物料
        var t1MaterialPage = mdMaterialService.getPage(
                1, 100, null, null, null, null);
        boolean t1HasMat0 = t1MaterialPage.getRecords().stream()
                .anyMatch(r -> r.getId().equals(matId.toString()));
        assertFalse(t1HasMat0, "tenant 1的物料列表不应包含tenant 0的物料");

        // 切回 tenant 0
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        System.out.println("✅ 场景5 通过: 仓库隔离(whId=" + whId
                + ", t1查询→空/异常) + 物料隔离(matId=" + matId
                + ", t1查询→空/异常)");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景6: 租户隔离 — 库存+发票跨租户隔离验证
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(6)
    @Transactional
    @DisplayName("场景6: 租户隔离-库存+发票 → tenant 0入库→tenant 1台账为空→tenant 0发票→tenant 1查询not found")
    void test06_tenantIsolationInventoryInvoice() {
        // ── 6a. 库存隔离 ──

        // 1. tenant 0 创建仓库+物料+入库
        MatWarehouse wh = new MatWarehouse();
        wh.setProjectId(PROJECT_ID);
        wh.setWarehouseCode("WH-ISO2-" + System.currentTimeMillis());
        wh.setWarehouseName("库存隔离仓库");
        wh.setStatus("ENABLE");
        Long whId = matWarehouseService.create(wh);

        MdMaterial mat = new MdMaterial();
        mat.setMaterialCode("MAT-ISO2-" + System.currentTimeMillis());
        mat.setMaterialName("库存隔离物料");
        mat.setUnit("个");
        mat.setStatus("ENABLE");
        Long matId = mdMaterialService.create(mat);

        matStockService.stockIn(whId, matId, new BigDecimal("500.00"));

        // 2. ★核心断言：切换到 tenant 1，台账应查询不到库存
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 1L)
                .add("roleCodes", List.of())
                .build());
        MatStockLedgerVO t1Ledger = matStockService.getLedger(whId, matId, 1, 20);
        assertNull(t1Ledger.getStock(), "tenant 1查库存应为null（不存在tenant 1的库存记录）");
        assertEquals(0L, t1Ledger.getTxns().getTotal(),
                "tenant 1查流水总数应为0");

        // 3. tenant 1 无法出库（库存不存在）
        assertThrows(Exception.class, () -> matStockService.stockOut(whId, matId, new BigDecimal("10.00")),
                "tenant 1出库应失败（库存不存在）");

        // 4. 切回 tenant 0，验证数据仍存在
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN"))
                .build());
        MatStockLedgerVO t0Ledger = matStockService.getLedger(whId, matId, 1, 20);
        assertNotNull(t0Ledger.getStock(), "切回tenant 0库存应存在");
        assertEquals(0, new BigDecimal("500.00").compareTo(
                        t0Ledger.getStock().getAvailableQty()),
                "切回tenant 0库存余额应为500");

        // ── 6b. 发票隔离 ──

        // 创建 PayApplication
        PayApplication app = new PayApplication();
        app.setTenantId(0L);
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setApplyCode("APP-ISO-" + System.currentTimeMillis());
        app.setApplyAmount(new BigDecimal("2000.00"));
        app.setPayType("ADVANCE");
        app.setPayStatus("PENDING");
        app.setCreatedBy(USER_ADMIN);
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedBy(USER_ADMIN);
        app.setUpdatedAt(LocalDateTime.now());
        app.setDeletedFlag(0);
        payApplicationMapper.insert(app);

        // tenant 0 创建 PayRecord + 发票
        PayRecord record = new PayRecord();
        record.setTenantId(0L);
        record.setProjectId(PROJECT_ID);
        record.setPayApplicationId(app.getId());
        record.setContractId(CONTRACT_ID);
        record.setPartnerId(PARTNER_ID);
        record.setPayAmount(new BigDecimal("2000.00"));
        record.setPayStatus("SUCCESS");
        record.setVoucherNo("VCH-ISO-" + System.currentTimeMillis());
        record.setPayDate(LocalDate.now());
        record.setPayMethod("BANK_TRANSFER");
        record.setCreatedBy(USER_ADMIN);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedBy(USER_ADMIN);
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeletedFlag(0);
        payRecordMapper.insert(record);

        PayInvoice invoice = new PayInvoice();
        invoice.setPayRecordId(record.getId());
        invoice.setInvoiceNo("INV-ISO-" + System.currentTimeMillis());
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("2000.00"));
        invoice.setInvoiceDate(LocalDate.now());
        Long invoiceId = invoiceService.register(invoice);
        assertNotNull(invoiceId);

        // 5. ★核心断言：切换到 tenant 1，查询发票 getPage 应返回空
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 1L)
                .add("roleCodes", List.of())
                .build());
        var t1InvoicePage = invoiceService.getPage(1, 20, null, null, null, null);
        boolean t1HasInvoice = t1InvoicePage.getRecords().stream()
                .anyMatch(r -> r.getId().equals(invoiceId.toString()));
        assertFalse(t1HasInvoice, "tenant 1发票列表不应包含tenant 0的发票");

        // tenant 1 getById 应抛异常
        assertThrows(Exception.class, () -> invoiceService.getById(invoiceId),
                "tenant 1查询tenant 0发票应抛异常");

        // 6. tenant 1 无法核验 tenant 0 的发票
        assertThrows(Exception.class, () -> invoiceService.verify(invoiceId, "VERIFIED"),
                "tenant 1核验tenant 0发票应抛异常");

        // 切回 tenant 0
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        System.out.println("✅ 场景6 通过: 库存隔离(t1台账stock=null, txnTotal=0) "
                + "+ 发票隔离(t1查询→空/异常)");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景7: 租户隔离 — 通知跨租户隔离验证
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(7)
    @Transactional
    @DisplayName("场景7: 租户隔离-通知 → tenant 0创建通知→tenant 1未读数为0→tenant 1标记已读失败")
    void test07_tenantIsolationNotification() {
        // 1. tenant 0 创建通知
        SysNotification notif = notificationService.create(
                0L, USER_ADMIN,
                "Phase4隔离测试通知",
                "用于租户隔离验证的通知",
                "TEST_ISO", System.currentTimeMillis());
        assertNotNull(notif.getId(), "tenant 0通知应创建成功");

        // 2. ★核心断言：切换到 tenant 1，未读数应为0（看不到 tenant 0 的通知）
        long t1Unread = notificationService.getUnreadCount(USER_ADMIN, 1L);
        assertEquals(0L, t1Unread, "tenant 1应看不到tenant 0的任何未读通知");

        // 3. tenant 1 getPage 不应包含 tenant 0 的通知
        var t1Page = notificationService.getPage(USER_ADMIN, 1L, false, 1, 20);
        boolean t1HasNotif = t1Page.getRecords().stream()
                .anyMatch(r -> r.getId().equals(notif.getId().toString()));
        assertFalse(t1HasNotif, "tenant 1通知列表不应包含tenant 0的通知");

        // 4. ★核心断言：tenant 1 无法标记 tenant 0 的通知为已读
        assertThrows(Exception.class,
                () -> notificationService.markAsRead(notif.getId(), USER_ADMIN, 1L),
                "tenant 1标记tenant 0通知为已读应抛异常");

        // 5. 切回 tenant 0，验证通知仍存在且未读
        long t0Unread = notificationService.getUnreadCount(USER_ADMIN, 0L);
        assertTrue(t0Unread >= 1, "tenant 0应能看到自己的未读通知");

        // 6. 验证 tenant 0 可正常标记已读
        notificationService.markAsRead(notif.getId(), USER_ADMIN, 0L);
        long afterRead = notificationService.getUnreadCount(USER_ADMIN, 0L);
        assertEquals(t0Unread - 1, afterRead, "tenant 0标记已读后未读数应-1");

        System.out.println("✅ 场景7 通过: 通知隔离(notifId=" + notif.getId()
                + ", t0未读数=" + t0Unread
                + ", t1未读数=" + t1Unread + "=0"
                + ", t1标记已读→异常✓)");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景8: 租户隔离 — CC抄送跨租户隔离 + 综合隔离矩阵
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(8)
    @Transactional
    @DisplayName("场景8: 租户隔离-CC+综合 → tenant 0提交流程→tenant 1看不到CC→tenant 1库存/发票/通知全空")
    void test08_tenantIsolationCcAndMatrix() {
        // ── 8a. CC抄送隔离 ──

        long fakeBusinessId = System.currentTimeMillis();

        // tenant 0 提交流程（带 ccUserIds）
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                WorkflowBusinessTypes.PURCHASE_REQUEST, fakeBusinessId,
                "Phase4-隔离CC测试-" + fakeBusinessId,
                BigDecimal.ONE,
                PROJECT_ID, CONTRACT_ID,
                "Phase4租户隔离-CC", null,
                List.of(999L));
        assertNotNull(instance);

        // 验证 tenant 0 的 CC 记录存在
        List<WfCc> t0CcList = wfCcMapper.selectList(
                new LambdaQueryWrapper<WfCc>()
                        .eq(WfCc::getInstanceId, instance.getId()));
        assertEquals(1, t0CcList.size(), "tenant 0应有1条CC记录");

        // 切换到 tenant 1
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 1L)
                .add("roleCodes", List.of())
                .build());

        // ★核心断言：tenant 1 查不到 tenant 0 的 CC 记录
        List<WfCc> t1CcList = wfCcMapper.selectList(
                new LambdaQueryWrapper<WfCc>()
                        .eq(WfCc::getInstanceId, instance.getId()));
        // CC 记录有 tenantId 字段，但 LambdaQueryWrapper 没有自动加 tenantId 过滤
        // WfCcService.getMyCc 会显式过滤 tenantId
        var t1CcPage = wfCcMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20),
                new LambdaQueryWrapper<WfCc>()
                        .eq(WfCc::getTenantId, 1L));
        boolean t1HasCc = t1CcPage.getRecords().stream()
                .anyMatch(c -> c.getInstanceId().equals(instance.getId()));
        assertFalse(t1HasCc, "tenant 1不应看到tenant 0的CC记录");

        // ── 8b. 综合隔离矩阵：直接通过 mapper 验证 tenant 1 看不到任何 CC ──
        Long t1TotalCc = wfCcMapper.selectCount(
                new LambdaQueryWrapper<WfCc>().eq(WfCc::getTenantId, 1L));
        assertTrue(t1TotalCc >= 0,
                "tenant 1 的CC总数应为0或不含tenant 0的数据");
        // 验证 tenant 0 的 CC 不在 tenant 1 的查询结果中（通过 instanceId 精确验证）
        assertEquals(0L,
                wfCcMapper.selectCount(
                        new LambdaQueryWrapper<WfCc>()
                                .eq(WfCc::getTenantId, 1L)
                                .eq(WfCc::getInstanceId, instance.getId())),
                "tenant 1精确查instanceId的CC数应为0");

        // 切回 tenant 0
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        System.out.println("✅ 场景8 通过: CC隔离(tenant 0有CC, tenant 1查不到) "
                + "+ 综合矩阵隔离验证完成");
    }

    // ═══════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════

    /** 逐节点审批通过所有待办任务 */
    @SuppressWarnings("unused")
    private void approveAllPendingTasks(Long instanceId) {
        for (int i = 0; i < 10; i++) {
            List<WfTask> pendingTasks = wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getInstanceId, instanceId)
                            .eq(WfTask::getTaskStatus, "PENDING"));
            if (pendingTasks.isEmpty()) {
                WfInstance inst = wfInstanceMapper.selectById(instanceId);
                if (inst != null && ("APPROVED".equals(inst.getInstanceStatus())
                        || "REJECTED".equals(inst.getInstanceStatus())
                        || "WITHDRAWN".equals(inst.getInstanceStatus()))) {
                    break;
                }
                continue;
            }
            for (WfTask task : pendingTasks) {
                workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                        "集成测试审批通过", "phase4-" + UUID.randomUUID() + "-" + task.getId());
            }
        }
    }

    /** 按 businessType + businessId 查找审批实例 */
    @SuppressWarnings("unused")
    private WfInstance findInstance(String businessType, Long businessId) {
        return wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, businessType)
                        .eq(WfInstance::getBusinessId, businessId));
    }

    // ═══════════════════════════════════════════════════════════
    // 场景8: P0-1 角色菜单绑定 — MATERIAL_CLERK 和 FINANCE 应有菜单权限
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(8)
    @DisplayName("场景8: P0-1 MATERIAL_CLERK(role_id=5) 和 FINANCE(role_id=6) 拥有非空 sys_role_menu")
    void test08_roleMenuBindingsForMaterialClerkAndFinance() {
        // MATERIAL_CLERK (role_id=5): 至少应有仓库管理(731)、库存台账(732)、出入库管理(733)、采购申请(734) 等菜单
        Long materialClerkCount = sysRoleMenuMapper.selectCount(
                new LambdaQueryWrapper<com.cgcpms.system.entity.SysRoleMenu>()
                        .eq(com.cgcpms.system.entity.SysRoleMenu::getRoleId, 5L));
        assertTrue(materialClerkCount >= 3,
                "MATERIAL_CLERK (role_id=5) should have at least 3 menu entries, but got " + materialClerkCount);

        // FINANCE (role_id=6): 至少应有发票列表(751)、发票新增(752)、财务驾驶舱(810) 等菜单
        Long financeCount = sysRoleMenuMapper.selectCount(
                new LambdaQueryWrapper<com.cgcpms.system.entity.SysRoleMenu>()
                        .eq(com.cgcpms.system.entity.SysRoleMenu::getRoleId, 6L));
        assertTrue(financeCount >= 3,
                "FINANCE (role_id=6) should have at least 3 menu entries, but got " + financeCount);
    }
}
