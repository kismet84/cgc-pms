package com.cgcpms.invoice;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.invoice.vo.InvoiceVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoiceServiceTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ADMIN = 1L;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PayInvoiceMapper payInvoiceMapper;

    @BeforeEach
    void setUp() {
        Claims claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .build();
        UserContext.set(claims);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ── RED 1: create invoice then retrieve ──

    @Test
    @Order(1)
    @DisplayName("CREATE: insert invoice and verify fields")
    void shouldCreateInvoice() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-TEST-001");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("13.00"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 12));
        invoice.setPayRecordId(1L);

        Long id = invoiceService.create(invoice);
        assertNotNull(id);

        InvoiceVO vo = invoiceService.getById(id);
        assertEquals("INV-TEST-001", vo.getInvoiceNo());
        assertEquals("VAT_SPECIAL", vo.getInvoiceType());
        assertEquals("10000.00", vo.getInvoiceAmount());
        assertEquals("13.00", vo.getTaxRate());
        assertEquals("1300.00", vo.getTaxAmount());
        assertEquals("2026-06-12", vo.getInvoiceDate());
        assertEquals("PENDING", vo.getVerifyStatus());
    }

    // ── RED 2: duplicate invoice_no per tenant → BusinessException ──

    @Test
    @Order(2)
    @DisplayName("DUPLICATE: same invoice_no within same tenant throws BusinessException")
    void shouldRejectDuplicateInvoiceNo() {
        PayInvoice invoice1 = new PayInvoice();
        invoice1.setInvoiceNo("INV-DUP-002");
        invoice1.setInvoiceType("VAT_SPECIAL");
        invoice1.setInvoiceAmount(new BigDecimal("5000.00"));
        Long id1 = invoiceService.create(invoice1);
        assertNotNull(id1);

        PayInvoice invoice2 = new PayInvoice();
        invoice2.setInvoiceNo("INV-DUP-002");
        invoice2.setInvoiceType("VAT_NORMAL");
        invoice2.setInvoiceAmount(new BigDecimal("3000.00"));

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            invoiceService.create(invoice2);
        });
        assertEquals("INVOICE_NO_DUPLICATE", ex.getCode());
    }

    // ── RED 3: verify status transition PENDING → VERIFIED ──

    @Test
    @Order(3)
    @DisplayName("VERIFY: PENDING → VERIFIED succeeds")
    void shouldVerifyInvoiceToVerified() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-VFY-003");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("8000.00"));
        Long id = invoiceService.create(invoice);

        invoiceService.verify(id, "VERIFIED");

        InvoiceVO vo = invoiceService.getById(id);
        assertEquals("VERIFIED", vo.getVerifyStatus());
    }

    // ── RED 4: verify status transition PENDING → ABNORMAL ──

    @Test
    @Order(4)
    @DisplayName("VERIFY: PENDING → ABNORMAL succeeds")
    void shouldVerifyInvoiceToAbnormal() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-ABN-004");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("6000.00"));
        Long id = invoiceService.create(invoice);

        invoiceService.verify(id, "ABNORMAL");

        InvoiceVO vo = invoiceService.getById(id);
        assertEquals("ABNORMAL", vo.getVerifyStatus());
    }

    // ── RED 5: verify non-PENDING → rejected ──

    @Test
    @Order(5)
    @DisplayName("VERIFY: already VERIFIED invoice cannot be verified again")
    void shouldRejectVerifyOnNonPending() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-NPV-005");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("7000.00"));
        Long id = invoiceService.create(invoice);
        invoiceService.verify(id, "VERIFIED");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            invoiceService.verify(id, "VERIFIED");
        });
        assertEquals("VERIFY_STATUS_CONFLICT", ex.getCode());
    }

    // ── RED 6: verify with invalid status → rejected ──

    @Test
    @Order(6)
    @DisplayName("VERIFY: invalid target status rejected")
    void shouldRejectInvalidVerifyStatus() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-INV-006");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("9000.00"));
        Long id = invoiceService.create(invoice);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            invoiceService.verify(id, "APPROVED");
        });
        assertEquals("INVALID_VERIFY_STATUS", ex.getCode());
    }

    // ── RED 7: getById with wrong tenant → rejected ──

    @Test
    @Order(7)
    @DisplayName("TENANT: getById with mismatched tenantId rejected")
    void shouldRejectGetByIdWrongTenant() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-TNT-007");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("4000.00"));
        Long id = invoiceService.create(invoice);

        // Switch to different tenant
        Claims otherClaims = Jwts.claims()
                .subject("other")
                .add("userId", 2L)
                .add("username", "other")
                .add("tenantId", 999L)
                .build();
        UserContext.set(otherClaims);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            invoiceService.getById(id);
        });
        assertEquals("INVOICE_NOT_FOUND", ex.getCode());
    }

    // ── RED 8: update ──

    @Test
    @Order(8)
    @DisplayName("UPDATE: update invoice fields")
    void shouldUpdateInvoice() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-UPD-008");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("2000.00"));
        Long id = invoiceService.create(invoice);

        PayInvoice update = new PayInvoice();
        update.setId(id);
        update.setInvoiceAmount(new BigDecimal("2500.00"));
        update.setRemark("更新测试");
        invoiceService.update(update);

        InvoiceVO vo = invoiceService.getById(id);
        assertEquals("2500.00", vo.getInvoiceAmount());
        assertEquals("更新测试", vo.getRemark());
    }

    // ── RED 9: delete ──

    @Test
    @Order(9)
    @DisplayName("DELETE: delete invoice")
    void shouldDeleteInvoice() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-DEL-009");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("1000.00"));
        Long id = invoiceService.create(invoice);

        invoiceService.delete(id);

        // After logical delete, getById should return null (or tenant check fails)
        assertThrows(BusinessException.class, () -> {
            invoiceService.getById(id);
        });
    }

    // ── RED 10: register with pay_record_id ──

    @Test
    @Order(10)
    @DisplayName("REGISTER: register invoice with payRecordId linkage")
    void shouldRegisterInvoiceWithPayRecord() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-REG-010");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("15000.00"));
        invoice.setPayRecordId(1L);

        Long id = invoiceService.register(invoice);
        assertNotNull(id);

        InvoiceVO vo = invoiceService.getById(id);
        assertEquals("1", vo.getPayRecordId());
    }

    // ── RED 11: register without pay_record_id → rejected ──

    @Test
    @Order(11)
    @DisplayName("REGISTER: missing payRecordId rejected")
    void shouldRejectRegisterWithoutPayRecord() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-NOR-011");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("5000.00"));
        // payRecordId not set

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            invoiceService.register(invoice);
        });
        assertEquals("MISSING_PAY_RECORD_ID", ex.getCode());
    }
}
