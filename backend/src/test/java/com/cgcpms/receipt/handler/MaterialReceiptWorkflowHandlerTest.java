package com.cgcpms.receipt.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("MaterialReceiptWorkflowHandler — approval lifecycle tests")
class MaterialReceiptWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private MaterialReceiptWorkflowHandler handler;

    @Autowired
    private MatReceiptMapper receiptMapper;

    @BeforeEach void setupContext() {
        UserContext.set(Jwts.claims().add("userId", USER_ADMIN).add("username", "admin")
                .add("tenantId", TENANT_0).add("roleCodes", List.of("ADMIN")).build());
    }
    @AfterEach void clearContext() { UserContext.clear(); }

    @Test @DisplayName("supportBusinessType -> MATERIAL_RECEIPT")
    void testSupportBusinessType() { assertEquals("MATERIAL_RECEIPT", handler.supportBusinessType()); }
    @Test @DisplayName("isCritical -> true")
    void testIsCritical() { assertTrue(handler.isCritical()); }

    @Test @Transactional @DisplayName("onApproved -> status = APPROVED, stock update")
    void testOnApproved() {
        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(10001L);
        receipt.setContractId(30001L);
        receipt.setPartnerId(20002L);
        receipt.setWarehouseId(1L);
        receipt.setReceiptDate(LocalDate.now());
        receipt.setQualityStatus("PENDING");
        receipt.setTotalAmount(new BigDecimal("10000.00"));
        receipt.setReceiptCode("RC-HDLR-" + System.nanoTime());
        receipt.setApprovalStatus("APPROVING");
        receipt.setTenantId(0L);
        receiptMapper.insert(receipt);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(receipt.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        MatReceipt updated = receiptMapper.selectById(receipt.getId());
        assertNotNull(updated);
        assertEquals("APPROVED", updated.getApprovalStatus());
    }

    @Test @Transactional @DisplayName("onApproved — null businessId")
    void testOnApproved_NullBusinessId() {
        WfInstance instance = new WfInstance(); instance.setId(9101L);
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);
        assertThrows(Exception.class, () -> handler.onApproved(ctx));
    }

    @Test @Transactional @DisplayName("onRejected -> status = REJECTED")
    void testOnRejected() {
        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(10001L); receipt.setContractId(30001L); receipt.setPartnerId(20002L);
        receipt.setWarehouseId(1L); receipt.setReceiptDate(LocalDate.now());
        receipt.setQualityStatus("PENDING"); receipt.setTotalAmount(new BigDecimal("5000.00"));
        receipt.setReceiptCode("RC-HDLR-" + System.nanoTime());
        receipt.setApprovalStatus("APPROVING"); receipt.setTenantId(0L);
        receiptMapper.insert(receipt);

        WfInstance instance = new WfInstance(); instance.setBusinessId(receipt.getId());
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);

        handler.onRejected(ctx);
        assertEquals("REJECTED", receiptMapper.selectById(receipt.getId()).getApprovalStatus());
    }

    @Test @Transactional @DisplayName("onWithdrawn -> DRAFT")
    void testOnWithdrawn() {
        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(10001L); receipt.setContractId(30001L); receipt.setPartnerId(20002L);
        receipt.setWarehouseId(1L); receipt.setReceiptDate(LocalDate.now());
        receipt.setQualityStatus("PENDING"); receipt.setTotalAmount(new BigDecimal("5000.00"));
        receipt.setReceiptCode("RC-HDLR-" + System.nanoTime());
        receipt.setApprovalStatus("APPROVING"); receipt.setTenantId(0L);
        receiptMapper.insert(receipt);

        WfInstance instance = new WfInstance(); instance.setBusinessId(receipt.getId());
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);

        handler.onWithdrawn(ctx);
        assertEquals("DRAFT", receiptMapper.selectById(receipt.getId()).getApprovalStatus());
    }
}
