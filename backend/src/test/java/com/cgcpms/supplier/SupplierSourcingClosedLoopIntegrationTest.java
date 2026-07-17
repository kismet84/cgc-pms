package com.cgcpms.supplier;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.supplier.dto.SupplierSourcingModels.*;
import com.cgcpms.supplier.entity.*;
import com.cgcpms.supplier.service.SupplierSourcingService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class SupplierSourcingClosedLoopIntegrationTest {
    private static final long PROJECT = 99189001L;
    private static final long REQUEST = 99189002L;
    private static final long SUPPLIER_A = 99189011L;
    private static final long SUPPLIER_B = 99189012L;
    private static final long SUPPLIER_C = 99189013L;
    private static final long CONTRACT = 99189020L;
    private static final long ORDER = 99189030L;
    private static final long ORDER_ITEM = 99189031L;
    private static final long RECEIPT = 99189040L;
    private static final long RECEIPT_ITEM = 99189041L;
    private static final long SETTLEMENT = 99189050L;
    private static final AtomicLong FILE_ID = new AtomicLong(99189100L);

    @Autowired SupplierSourcingService service;
    @Autowired BusinessObjectAuthorizer fileAuthorizer;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        asUser(1L);
        cleanup();
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,contract_amount,target_cost,project_manager_id,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'SP-IT','供应商闭环测试项目',100000,80000,1,'ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        insertSupplier(SUPPLIER_A, "SP-A", "供应商A");
        insertSupplier(SUPPLIER_B, "SP-B", "供应商B");
        insertSupplier(SUPPLIER_C, "SP-C", "供应商C");
        jdbc.update("INSERT INTO mat_purchase_request(id,tenant_id,project_id,request_code,approval_status,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'PR-SP-IT','APPROVED','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", REQUEST, PROJECT);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'CT-SP-IT','供应商测试采购合同','PURCHASE',?,?,10000,10000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CONTRACT, PROJECT, SUPPLIER_A, SUPPLIER_A);
        jdbc.update("INSERT INTO mat_purchase_order(id,tenant_id,project_id,request_id,contract_id,partner_id,order_code,order_type,order_date,delivery_date,total_amount,approval_status,order_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,?, 'PO-SP-IT','PURCHASE',?,?,10000,'APPROVED','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", ORDER, PROJECT, REQUEST, CONTRACT, SUPPLIER_A, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        jdbc.update("INSERT INTO mat_purchase_order_item(id,tenant_id,order_id,project_id,material_id,unit,quantity,unit_price,amount,received_quantity,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,1,'吨',10,1000,10000,10,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", ORDER_ITEM, ORDER, PROJECT);
        jdbc.update("INSERT INTO mat_receipt(id,tenant_id,project_id,order_id,contract_id,partner_id,receipt_code,receipt_date,quality_status,total_amount,approval_status,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,?, 'RC-SP-IT',?,'UNQUALIFIED',10000,'APPROVED',1,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", RECEIPT, PROJECT, ORDER, CONTRACT, SUPPLIER_A, LocalDate.now());
        jdbc.update("INSERT INTO mat_receipt_item(id,tenant_id,receipt_id,order_item_id,material_id,actual_quantity,qualified_quantity,unit_price,amount,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,1,10,0,1000,10000,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", RECEIPT_ITEM, RECEIPT, ORDER_ITEM);
        jdbc.update("INSERT INTO stl_settlement(id,tenant_id,project_id,contract_id,partner_id,settlement_code,settlement_type,contract_amount,change_amount,measured_amount,deduction_amount,paid_amount,final_amount,approval_status,settlement_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,'ST-SP-IT','PURCHASE',10000,0,10000,100,0,9900,'APPROVED','FINALIZED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", SETTLEMENT, PROJECT, CONTRACT, SUPPLIER_A);
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void closesRequirementSourcingQuoteEvaluationAwardContractPerformanceAndBlacklist() {
        SourcingEvent event = service.createEvent(new EventCommand(PROJECT, REQUEST, "SP-SRC-001", "钢材采购询价",
                "INQUIRY", LocalDateTime.now().plusDays(1), "CNY", null));
        service.addSuppliers(event.getId(), new InvitationCommand(List.of(SUPPLIER_A, SUPPLIER_B, SUPPLIER_C)));
        evidence("SUPPLIER_SOURCING", event.getId(), "SOURCING_REQUIREMENT");
        assertEquals("PUBLISHED", service.publish(event.getId()).getStatus());

        SupplierQuote quoteA = quote(event.getId(), SUPPLIER_A, "SP-Q-A", "10000");
        SupplierQuote quoteB = quote(event.getId(), SUPPLIER_B, "SP-Q-B", "9800");
        evidence("SUPPLIER_QUOTE", quoteA.getId(), "QUOTE_ATTACHMENT");
        evidence("SUPPLIER_QUOTE", quoteB.getId(), "QUOTE_ATTACHMENT");
        service.submitQuote(quoteA.getId());
        service.submitQuote(quoteB.getId());
        service.decline(event.getId(), SUPPLIER_C, new DeclineCommand("产能不足，放弃本次报价"));
        assertEquals("EVALUATING", service.startEvaluation(event.getId()).getStatus());

        service.evaluate(new EvaluationCommand(quoteA.getId(), score(90), score(95), score(90), score(85), "综合能力与交付保障较优"));
        service.evaluate(new EvaluationCommand(quoteB.getId(), score(95), score(75), score(70), score(75), "价格较优但交付保障一般"));
        assertEquals("AWARDED", service.award(event.getId(), new AwardCommand(quoteA.getId(), "综合评分最高，技术、交付和质量保障满足项目要求")).getStatus());
        assertEquals("CONTRACTED", service.linkContract(event.getId(), new LinkContractCommand(CONTRACT)).getStatus());

        SupplierReturn supplierReturn = service.createSupplierReturn(new SupplierReturnCommand(
                RECEIPT, "SRT-SP-001", LocalDate.now(), new BigDecimal("2"), new BigDecimal("2000"),
                "到货质量不合格，退回供应商"));
        supplierReturn = service.confirmSupplierReturn(supplierReturn.getId());
        assertEquals("CONFIRMED", supplierReturn.getStatus());

        SupplierPerformanceEvaluation performance = service.createPerformance(new PerformanceCommand(
                ORDER, BigDecimal.ZERO, "延期且本批验收不合格，售后响应未达到项目要求"));
        assertEquals("E", performance.getGrade());
        assertEquals(1, performance.getReturnCount());
        assertEquals(1, performance.getRecommendBlacklist());
        performance = service.confirmPerformance(performance.getId());
        assertEquals("CONFIRMED", performance.getStatus());

        SupplierBlacklistRecord blacklist = service.createBlacklist(new BlacklistCommand(performance.getId(),
                "综合履约评价低于准入红线，申请纳入供应商黑名单"));
        blacklist = service.submitBlacklist(blacklist.getId());
        assertEquals("SUBMITTED", blacklist.getStatus());
        asUser(2L);
        blacklist = service.reviewBlacklist(blacklist.getId(), new ReviewCommand("APPROVE", "事实来源完整，同意纳入黑名单"));
        assertEquals("APPROVED", blacklist.getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT blacklist_flag FROM md_partner WHERE id=?", Integer.class, SUPPLIER_A));

        SourcingTrace trace = service.trace(event.getId());
        assertEquals(REQUEST, trace.purchaseRequest().getId());
        assertEquals(3, trace.invitedSuppliers().size());
        assertEquals(2, trace.quotes().size());
        assertEquals(2, trace.bidEvaluations().size());
        assertEquals(CONTRACT, trace.contract().getId());
        assertEquals(1, trace.purchaseOrders().size());
        assertEquals(1, trace.receipts().size());
        assertEquals(1, trace.supplierReturns().size());
        assertEquals(1, trace.settlements().size());
        assertEquals(1, trace.performanceEvaluations().size());
        assertEquals(1, trace.blacklistRecords().size());
    }

    @Test
    void rejectsInsufficientSuppliersMissingAttachmentsAndSelfReview() {
        SourcingEvent event = service.createEvent(new EventCommand(PROJECT, REQUEST, "SP-SRC-EDGE", "边界询价",
                "INQUIRY", LocalDateTime.now().plusDays(1), "CNY", null));
        service.addSuppliers(event.getId(), new InvitationCommand(List.of(SUPPLIER_A, SUPPLIER_B)));
        BusinessException insufficient = assertThrows(BusinessException.class, () -> service.publish(event.getId()));
        assertEquals("SP_SUPPLIERS_INSUFFICIENT", insufficient.getCode());
        service.addSuppliers(event.getId(), new InvitationCommand(List.of(SUPPLIER_C)));
        assertEquals("SP_ATTACHMENT_REQUIRED", assertThrows(BusinessException.class,
                () -> service.publish(event.getId())).getCode());

        authenticate("supplier:sourcing:maintain");
        assertDoesNotThrow(() -> fileAuthorizer.checkUploadAccess("SUPPLIER_SOURCING", event.getId()));
        assertDoesNotThrow(() -> fileAuthorizer.checkVariationDocumentStage(
                "SUPPLIER_SOURCING", event.getId(), "SOURCING_REQUIREMENT"));
        assertEquals("SP_DOCUMENT_STAGE_INVALID", assertThrows(BusinessException.class,
                () -> fileAuthorizer.checkVariationDocumentStage(
                        "SUPPLIER_SOURCING", event.getId(), "QUOTE_ATTACHMENT")).getCode());
    }

    @Test
    void rejectsBlacklistedSupplierAtInvitation() {
        jdbc.update("UPDATE md_partner SET blacklist_flag=1 WHERE id=?", SUPPLIER_C);
        SourcingEvent event = service.createEvent(new EventCommand(PROJECT, REQUEST, "SP-SRC-BL", "黑名单校验",
                "TENDER", LocalDateTime.now().plusDays(1), "CNY", null));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addSuppliers(event.getId(), new InvitationCommand(List.of(SUPPLIER_C))));
        assertEquals("SP_SUPPLIER_BLACKLISTED", exception.getCode());
    }

    @Test
    void rejectsSupplierReturnBeforeReceiptDateAndDuplicateConfirmation() {
        BusinessException invalidDate = assertThrows(BusinessException.class, () -> service.createSupplierReturn(
                new SupplierReturnCommand(RECEIPT, "SRT-SP-EDGE", LocalDate.now().minusDays(1),
                        BigDecimal.ONE, BigDecimal.ZERO, "退货日期异常")));
        assertEquals("SP_RETURN_DATE_INVALID", invalidDate.getCode());

        SupplierReturn row = service.createSupplierReturn(new SupplierReturnCommand(RECEIPT, "SRT-SP-EDGE",
                LocalDate.now(), BigDecimal.ONE, BigDecimal.ZERO, "不合格退货"));
        service.confirmSupplierReturn(row.getId());
        assertEquals("SP_RETURN_IMMUTABLE", assertThrows(BusinessException.class,
                () -> service.confirmSupplierReturn(row.getId())).getCode());
    }

    private SupplierQuote quote(Long eventId, Long partnerId, String code, String amount) {
        return service.createQuote(new QuoteCommand(eventId, partnerId, code, new BigDecimal(amount),
                new BigDecimal("13"), 7, LocalDate.now().plusDays(30), "含税到场，验收合格后结算", null));
    }

    private BigDecimal score(int value) { return BigDecimal.valueOf(value); }

    private void insertSupplier(long id, String code, String name) {
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,blacklist_flag,risk_level,status,created_at,updated_at,deleted_flag) VALUES(?,0,?,?,'SUPPLIER',0,'LOW','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", id, code, name);
    }

    private void evidence(String businessType, Long businessId, String documentType) {
        long id = FILE_ID.incrementAndGet();
        jdbc.update("INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,'evidence.pdf','evidence.pdf',100,'application/pdf',?,'test','CLEAN',CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",
                id, businessType, documentType, businessId, businessType + "/" + businessId + "/" + id + ".pdf");
    }

    private void asUser(long userId) {
        UserContext.set(Jwts.claims().subject("admin-" + userId).add("userId", userId).add("username", "admin-" + userId)
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private void cleanup() {
        jdbc.update("UPDATE sp_sourcing_event SET awarded_quote_id=NULL,contract_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM sp_blacklist_record WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM sp_performance_evaluation WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM sp_supplier_return WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM sp_bid_evaluation WHERE sourcing_event_id IN(SELECT id FROM sp_sourcing_event WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM sys_file WHERE business_type IN('SUPPLIER_SOURCING','SUPPLIER_QUOTE') AND (business_id IN(SELECT id FROM sp_sourcing_event WHERE project_id=?) OR business_id IN(SELECT id FROM sp_supplier_quote WHERE sourcing_event_id IN(SELECT id FROM sp_sourcing_event WHERE project_id=?)))", PROJECT, PROJECT);
        jdbc.update("DELETE FROM sp_supplier_quote WHERE sourcing_event_id IN(SELECT id FROM sp_sourcing_event WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM sp_sourcing_supplier WHERE sourcing_event_id IN(SELECT id FROM sp_sourcing_event WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM sp_sourcing_event WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM stl_settlement WHERE id=?", SETTLEMENT);
        jdbc.update("DELETE FROM mat_receipt_item WHERE id=?", RECEIPT_ITEM);
        jdbc.update("DELETE FROM mat_receipt WHERE id=?", RECEIPT);
        jdbc.update("DELETE FROM mat_purchase_order_item WHERE id=?", ORDER_ITEM);
        jdbc.update("DELETE FROM mat_purchase_order WHERE id=?", ORDER);
        jdbc.update("DELETE FROM ct_contract WHERE id=?", CONTRACT);
        jdbc.update("DELETE FROM mat_purchase_request WHERE id=?", REQUEST);
        jdbc.update("DELETE FROM md_partner WHERE id IN(?,?,?)", SUPPLIER_A, SUPPLIER_B, SUPPLIER_C);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }
}
