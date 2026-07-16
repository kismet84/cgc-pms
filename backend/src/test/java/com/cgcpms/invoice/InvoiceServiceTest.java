package com.cgcpms.invoice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.invoice.entity.InvoicePaymentAllocation;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.invoice.vo.InvoiceRecognizeResultVO;
import com.cgcpms.invoice.vo.InvoiceVO;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoiceServiceTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ADMIN = 1L;
    private static final long SEED_PAY_RECORD_ID = 91001L;
    private static final long SEED_PROJECT_ID = 91001L;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PayInvoiceMapper payInvoiceMapper;

    @Autowired
    private PayRecordMapper payRecordMapper;

    @Autowired
    private PayApplicationMapper payApplicationMapper;

    @Autowired
    private SysFileMapper sysFileMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        Claims claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build();
        UserContext.set(claims);

        // 物理清理本测试关心的数据，防止逻辑删除和并行测试类复用固定主键触发 PK 冲突。
        jdbcTemplate.update("DELETE FROM invoice_payment_allocation WHERE pay_record_id = ?", SEED_PAY_RECORD_ID);
        jdbcTemplate.update("DELETE FROM pay_invoice WHERE pay_record_id = ?", SEED_PAY_RECORD_ID);
        jdbcTemplate.update("DELETE FROM pay_invoice WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM pay_record WHERE id = ?", SEED_PAY_RECORD_ID);
        jdbcTemplate.update("DELETE FROM pay_application WHERE id = ?", SEED_PAY_RECORD_ID);
        jdbcTemplate.update("DELETE FROM pm_project WHERE id = ?", SEED_PROJECT_ID);

        PmProject project = new PmProject();
        project.setId(SEED_PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("PRJ-INVOICE-91001");
        project.setProjectName("发票测试项目");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("1000000.00"));
        project.setTargetCost(new BigDecimal("800000.00"));
        project.setStatus("RUNNING");
        project.setApprovalStatus("APPROVED");
        projectMapper.insert(project);

        PayApplication application = new PayApplication();
        application.setId(SEED_PAY_RECORD_ID);
        application.setTenantId(TENANT_ID);
        application.setProjectId(SEED_PROJECT_ID);
        application.setApplyCode("PAY-INVOICE-91001");
        application.setPayType("PROGRESS");
        application.setApplyAmount(new BigDecimal("100000.00"));
        application.setApprovedAmount(new BigDecimal("100000.00"));
        application.setApprovalStatus("APPROVED");
        application.setPayStatus("PAID");
        payApplicationMapper.insert(application);

        // 插入种子付款记录，供发票创建时关联使用
        PayRecord seed = new PayRecord();
        seed.setId(SEED_PAY_RECORD_ID);
        seed.setTenantId(TENANT_ID);
        seed.setProjectId(SEED_PROJECT_ID);
        seed.setPayApplicationId(SEED_PAY_RECORD_ID);
        seed.setPayAmount(new BigDecimal("100000.00"));
        seed.setPayDate(LocalDate.of(2026, 6, 1));
        seed.setPayStatus("SUCCESS");
        payRecordMapper.insert(seed);
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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);

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

    @Test
    @Order(14)
    @DisplayName("CREATE: info log should not expose invoiceNo or payRecordId")
    void shouldNotLogSensitiveIdentifiersAtInfoOnCreate() {
        Logger logger = (Logger) LoggerFactory.getLogger(InvoiceService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            PayInvoice invoice = new PayInvoice();
            invoice.setInvoiceNo("INV-LOG-014");
            invoice.setInvoiceType("VAT_SPECIAL");
            invoice.setInvoiceAmount(new BigDecimal("1200.00"));
            invoice.setPayRecordId(SEED_PAY_RECORD_ID);

            invoiceService.create(invoice);

            List<String> infoMessages = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertTrue(infoMessages.stream().noneMatch(message -> message.contains("INV-LOG-014")),
                    "INFO 日志不应输出 invoiceNo");
            assertTrue(infoMessages.stream().noneMatch(message -> message.contains(String.valueOf(SEED_PAY_RECORD_ID))),
                    "INFO 日志不应输出 payRecordId");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    @Order(15)
    @DisplayName("RECOGNIZE: fake PDF magic bytes rejected before PDFBox parsing")
    void shouldRejectRecognizeForFakePdfMagic() {
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                "not-a-real-pdf".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.recognize(fakePdf));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
    }

    @Test
    @Order(16)
    @DisplayName("RECOGNIZE: oversized PDF rejected before PDFBox parsing")
    void shouldRejectRecognizeForOversizedPdf() {
        byte[] content = new byte[11 * 1024 * 1024];
        System.arraycopy("%PDF-".getBytes(), 0, content, 0, 5);
        MockMultipartFile oversizedPdf = new MockMultipartFile(
                "file",
                "oversized.pdf",
                "application/pdf",
                content);

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.recognize(oversizedPdf));
        assertEquals("FILE_TOO_LARGE", ex.getCode());
        assertTrue(ex.getMessage().contains("10MB"));
    }

    @Test
    @Order(17)
    @DisplayName("RECOGNIZE: PDF content with non-PDF extension is rejected")
    void shouldRejectRecognizeForPdfWithNonPdfExtension() {
        MockMultipartFile disguisedPdf = new MockMultipartFile(
                "file",
                "invoice.txt",
                "application/pdf",
                "%PDF-1.4 disguised".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.recognize(disguisedPdf));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
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
        invoice1.setInvoiceDate(LocalDate.of(2026, 6, 2));
        invoice1.setPayRecordId(SEED_PAY_RECORD_ID);
        Long id1 = invoiceService.create(invoice1);
        assertNotNull(id1);

        PayInvoice invoice2 = new PayInvoice();
        invoice2.setInvoiceNo("INV-DUP-002");
        invoice2.setInvoiceType("VAT_NORMAL");
        invoice2.setInvoiceAmount(new BigDecimal("3000.00"));
        invoice2.setInvoiceDate(LocalDate.of(2026, 7, 2));
        invoice2.setPayRecordId(SEED_PAY_RECORD_ID);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            invoiceService.create(invoice2);
        });
        assertEquals("INVOICE_NO_DUPLICATE", ex.getCode());

        Long activeCount = payInvoiceMapper.selectCount(new LambdaQueryWrapper<PayInvoice>()
                .eq(PayInvoice::getTenantId, TENANT_ID)
                .eq(PayInvoice::getInvoiceNo, "INV-DUP-002"));
        assertEquals(1L, activeCount);
        InvoiceVO vo = invoiceService.getById(id1);
        assertEquals("5000.00", vo.getInvoiceAmount());
        assertEquals("2026-06-02", vo.getInvoiceDate());
        assertEquals(String.valueOf(SEED_PAY_RECORD_ID), vo.getPayRecordId());
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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
        Long id = invoiceService.create(invoice);
        prepareVerification(id, "8000.00");

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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
        Long id = invoiceService.create(invoice);
        prepareVerification(id, "7000.00");
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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
        Long id = invoiceService.create(invoice);

        // Switch to different tenant
        Claims otherClaims = Jwts.claims()
                .subject("other")
                .add("userId", 2L)
                .add("username", "other")
                .add("tenantId", 999L)
                .add("roleCodes", java.util.List.of())
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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
        Long id = invoiceService.create(invoice);

        invoiceService.delete(id);

        // After physical delete, getById should throw BusinessException
        assertThrows(BusinessException.class, () -> {
            invoiceService.getById(id);
        });
    }

    @Test
    @Order(16)
    @DisplayName("M2: VERIFIED invoice cannot be updated or deleted")
    void shouldRejectUpdateAndDeleteAfterVerified() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-M2-016");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("1000.00"));
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
        Long id = invoiceService.create(invoice);
        prepareVerification(id, "1000.00");
        invoiceService.verify(id, "VERIFIED");

        PayInvoice update = new PayInvoice();
        update.setId(id);
        update.setRemark("blocked");

        BusinessException updateEx = assertThrows(BusinessException.class, () -> invoiceService.update(update));
        assertEquals("INVOICE_VERIFIED_LOCKED", updateEx.getCode());

        BusinessException deleteEx = assertThrows(BusinessException.class, () -> invoiceService.delete(id));
        assertEquals("INVOICE_VERIFIED_LOCKED", deleteEx.getCode());
    }

    @Test
    @Order(18)
    @DisplayName("UPDATE: invalid payRecordId is rejected and original invoice fields remain unchanged")
    void shouldRejectUpdateToInvalidPayRecordAndKeepOriginalFields() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-PAYLINK-018");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("2600.00"));
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 18));
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
        Long id = invoiceService.create(invoice);

        PayInvoice update = new PayInvoice();
        update.setId(id);
        update.setPayRecordId(99999999999L);
        update.setInvoiceAmount(new BigDecimal("8800.00"));
        update.setInvoiceDate(LocalDate.of(2026, 7, 18));

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.update(update));
        assertEquals("INVOICE_PAYMENT_LINK_IMMUTABLE", ex.getCode());

        InvoiceVO vo = invoiceService.getById(id);
        assertEquals(String.valueOf(SEED_PAY_RECORD_ID), vo.getPayRecordId());
        assertEquals("2600.00", vo.getInvoiceAmount());
        assertEquals("2026-06-18", vo.getInvoiceDate());
    }

    private void prepareVerification(Long invoiceId, String amount) {
        InvoicePaymentAllocation allocation = new InvoicePaymentAllocation();
        allocation.setPayRecordId(SEED_PAY_RECORD_ID);
        allocation.setAllocatedAmount(new BigDecimal(amount));
        invoiceService.saveAllocations(invoiceId, List.of(allocation));

        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("INVOICE");
        file.setBusinessId(invoiceId);
        file.setDocumentType("ELECTRONIC_INVOICE");
        file.setFileName("invoice-" + invoiceId + ".pdf");
        file.setOriginalName("invoice.pdf");
        file.setFileSize(100L);
        file.setContentType("application/pdf");
        file.setStoragePath("INVOICE/" + invoiceId + "/invoice.pdf");
        file.setBucketName("test");
        sysFileMapper.insert(file);
    }

    @Test
    @Order(19)
    @DisplayName("RECOGNIZE: parse failure returns reason and does not block invoice create")
    void shouldReturnRecognizeFailureReasonAndKeepInvoiceFlowAvailable() {
        MockMultipartFile brokenPdf = new MockMultipartFile(
                "file",
                "broken.pdf",
                "application/pdf",
                malformedPdfBytes());

        InvoiceRecognizeResultVO result = invoiceService.recognize(brokenPdf);

        assertFalse(result.getSuccess());
        assertTrue(result.getManualConfirmationRequired());
        assertEquals("PDF_RECOGNIZE_FAILED", result.getErrorCode());
        assertEquals("PDF识别失败，请人工确认发票信息", result.getErrorMessage());
        assertEquals(0L, invoiceCount());

        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-FALLBACK-019");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("1600.00"));
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 19));
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);

        Long id = invoiceService.create(invoice);

        InvoiceVO vo = invoiceService.getById(id);
        assertEquals("INV-FALLBACK-019", vo.getInvoiceNo());
        assertEquals("PENDING", vo.getVerifyStatus());
    }

    @Test
    @Order(20)
    @DisplayName("RECOGNIZE: extracted high risk fields are not persisted before manual confirmation")
    void shouldNotPersistRecognizedFieldsBeforeManualConfirmation() throws IOException {
        MockMultipartFile emptyPdf = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                emptyPdfBytes());

        InvoiceRecognizeResultVO result = invoiceService.recognize(emptyPdf);

        assertTrue(result.getSuccess());
        assertTrue(result.getManualConfirmationRequired());
        assertEquals(0L, invoiceCount());
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
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);

        Long id = invoiceService.register(invoice);
        assertNotNull(id);

        InvoiceVO vo = invoiceService.getById(id);
        assertEquals(String.valueOf(SEED_PAY_RECORD_ID), vo.getPayRecordId());
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

    // ── RED 12: create without pay_record_id → rejected ──

    @Test
    @Order(12)
    @DisplayName("CREATE: missing payRecordId rejected (mandatory linkage)")
    void shouldRejectCreateWithoutPayRecord() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-NOPR-012");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("5000.00"));
        // payRecordId not set

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            invoiceService.create(invoice);
        });
        assertEquals("MISSING_PAY_RECORD_ID", ex.getCode());
    }

    // ── RED 13: create with non-existent pay_record_id → rejected ──

    @Test
    @Order(13)
    @DisplayName("CREATE: non-existent payRecordId rejected")
    void shouldRejectCreateWithInvalidPayRecord() {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo("INV-INVPR-013");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("5000.00"));
        invoice.setPayRecordId(99999999999L);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            invoiceService.create(invoice);
        });
        assertEquals("PAY_RECORD_NOT_FOUND", ex.getCode());
    }

    private long invoiceCount() {
        Long count = payInvoiceMapper.selectCount(new LambdaQueryWrapper<PayInvoice>()
                .eq(PayInvoice::getTenantId, TENANT_ID));
        return count == null ? 0L : count;
    }

    private static byte[] malformedPdfBytes() {
        return """
                %PDF-1.4
                1 0 obj
                << /Type /Catalog /Pages 2 0 R >>
                endobj
                """.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] emptyPdfBytes() throws IOException {
        PDDocument doc = new PDDocument();
        try {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            doc.save(output);
            return output.toByteArray();
        } finally {
            doc.close();
        }
    }
}
