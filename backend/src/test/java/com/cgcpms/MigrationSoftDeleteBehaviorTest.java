package com.cgcpms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.mapper.MatStockMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("迁移软删与会计分录结构回归测试")
class MigrationSoftDeleteBehaviorTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long SEED_PAY_RECORD_ID = 90001L;

    @Autowired
    private MatStockMapper matStockMapper;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PayInvoiceMapper payInvoiceMapper;

    @Autowired
    private AccountingEntryLineMapper accountingEntryLineMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PayRecordMapper payRecordMapper;

    @BeforeEach
    void setUp() {
        Claims claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build();
        UserContext.set(claims);

        // Insert seed pay record so invoice creation (now mandatory payRecordId) succeeds
        // Use JDBC physical delete to avoid logical-delete PK conflicts
        jdbcTemplate.update("DELETE FROM pay_record WHERE tenant_id = ?", TENANT_ID);
        PayRecord seed = new PayRecord();
        seed.setId(SEED_PAY_RECORD_ID);
        seed.setTenantId(TENANT_ID);
        seed.setPayApplicationId(SEED_PAY_RECORD_ID);
        seed.setPayAmount(new BigDecimal("100000.00"));
        seed.setPayDate(LocalDate.of(2026, 6, 1));
        seed.setPayStatus("PAID");
        payRecordMapper.insert(seed);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("mat_stock 逻辑删除后允许重建同一仓库物料库存")
    void matStockAllowsRecreateAfterLogicalDelete() {
        Long warehouseId = 82000001L;
        Long materialId = 82000002L;

        MatStock deletedStock = new MatStock();
        deletedStock.setId(82000003L);
        deletedStock.setTenantId(TENANT_ID);
        deletedStock.setWarehouseId(warehouseId);
        deletedStock.setMaterialId(materialId);
        deletedStock.setAvailableQty(new BigDecimal("12.3400"));
        deletedStock.setVersion(0);
        matStockMapper.insert(deletedStock);
        matStockMapper.deleteById(deletedStock.getId());

        MatStock activeStock = new MatStock();
        activeStock.setId(82000004L);
        activeStock.setTenantId(TENANT_ID);
        activeStock.setWarehouseId(warehouseId);
        activeStock.setMaterialId(materialId);
        activeStock.setAvailableQty(new BigDecimal("1.0000"));
        activeStock.setVersion(0);

        assertDoesNotThrow(() -> matStockMapper.insert(activeStock));
        Long activeCount = matStockMapper.selectCount(new LambdaQueryWrapper<MatStock>()
                .eq(MatStock::getWarehouseId, warehouseId)
                .eq(MatStock::getMaterialId, materialId));
        assertEquals(1L, activeCount);
    }

    @Test
    @DisplayName("pay_invoice 删除走逻辑删除并允许同一发票号重建")
    void payInvoiceDeleteIsLogicalAndAllowsRecreate() {
        String invoiceNo = "INV-SOFT-DEL-082";
        Long firstId = invoiceService.create(invoice(invoiceNo));

        invoiceService.delete(firstId);

        Integer deletedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pay_invoice WHERE id = ? AND deleted_flag = 1",
                Integer.class,
                firstId);
        assertEquals(1, deletedRows);

        Long recreatedId = assertDoesNotThrow(() -> invoiceService.create(invoice(invoiceNo)));
        assertNotNull(recreatedId);

        Long activeCount = payInvoiceMapper.selectCount(new LambdaQueryWrapper<PayInvoice>()
                .eq(PayInvoice::getTenantId, TENANT_ID)
                .eq(PayInvoice::getInvoiceNo, invoiceNo));
        assertEquals(1L, activeCount);
    }

    @Test
    @DisplayName("accounting_entry_line 包含 BaseEntity 审计列，插入分录行不报缺列")
    void accountingEntryLineInsertHasAuditColumns() {
        AccountingEntryLine line = new AccountingEntryLine();
        line.setId(82000005L);
        line.setTenantId(TENANT_ID);
        line.setEntryId(82000006L);
        line.setLineNo(1);
        line.setDirection("DEBIT");
        line.setCostSubjectId(82000007L);
        line.setAmount(new BigDecimal("88.88"));
        line.setSummary("迁移列回归测试");

        assertDoesNotThrow(() -> accountingEntryLineMapper.insert(line));
    }

    private PayInvoice invoice(String invoiceNo) {
        PayInvoice invoice = new PayInvoice();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("100.00"));
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 21));
        invoice.setPayRecordId(SEED_PAY_RECORD_ID);
        return invoice;
    }
}
