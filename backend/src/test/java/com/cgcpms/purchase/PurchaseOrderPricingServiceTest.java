package com.cgcpms.purchase;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.service.PurchaseOrderPricingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderPricingServiceTest {
    @Mock CtContractMapper contractMapper;
    @Mock CtContractItemMapper contractItemMapper;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock ProjectAccessChecker projectAccessChecker;
    PurchaseOrderPricingService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(0L, 1L);
        service = new PurchaseOrderPricingService(contractMapper, contractItemMapper, jdbcTemplate, projectAccessChecker);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void fixedPriceComesOnlyFromUniqueContractItem() {
        when(contractMapper.selectById(10L)).thenReturn(contract("FIXED"));
        when(contractItemMapper.selectList(any())).thenReturn(List.of(item("3560.0000")));

        var result = service.suggest(10L, 20L);

        assertEquals("3560.0000", result.unitPrice());
        assertEquals("CONTRACT_ITEM", result.priceSource());
        assertFalse(result.editable());
    }

    @Test
    void actualPriceUsesLatestApprovedReceiptAndRemainsEditable() {
        when(contractMapper.selectById(10L)).thenReturn(contract("ACTUAL"));
        when(contractItemMapper.selectList(any())).thenReturn(List.of(item("0")));
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyLong(), anyLong())).thenReturn(List.of(Map.of(
                "id", 99L,
                "unit_price", new BigDecimal("3500.0000"),
                "receipt_date", Date.valueOf(LocalDate.of(2026, 7, 28)))));

        var result = service.suggest(10L, 20L);

        assertEquals("3500.0000", result.unitPrice());
        assertEquals("99", result.sourceReceiptItemId());
        assertEquals("2026-07-28", result.sourceReceiptDate());
        assertTrue(result.editable());
    }

    @Test
    void ambiguousContractMaterialFailsClosed() {
        when(contractMapper.selectById(10L)).thenReturn(contract("FIXED"));
        when(contractItemMapper.selectList(any())).thenReturn(List.of(item("10"), item("11")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.suggest(10L, 20L));
        assertEquals("PURCHASE_CONTRACT_MATERIAL_NOT_UNIQUE", exception.getCode());
    }

    @Test
    void missingApprovedRequestDocumentFailsClosed() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(0L), eq(88L))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requirePurchaseRequestDocument(88L, 0L));

        assertEquals("PURCHASE_REQUEST_DOCUMENT_REQUIRED", exception.getCode());
    }

    private CtContract contract(String pricingMode) {
        CtContract contract = new CtContract();
        contract.setId(10L);
        contract.setTenantId(0L);
        contract.setProjectId(10001L);
        contract.setPartyBId(20002L);
        contract.setContractType("PURCHASE");
        contract.setContractStatus("PERFORMING");
        contract.setPricingMode(pricingMode);
        return contract;
    }

    private CtContractItem item(String price) {
        CtContractItem item = new CtContractItem();
        item.setId(Math.abs(price.hashCode()) + 1L);
        item.setTenantId(0L);
        item.setContractId(10L);
        item.setMaterialId(20L);
        item.setUnitPrice(new BigDecimal(price));
        return item;
    }
}
