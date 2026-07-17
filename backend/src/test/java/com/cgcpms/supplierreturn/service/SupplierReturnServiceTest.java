package com.cgcpms.supplierreturn.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.procurement.service.ProcurementIntegrityService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.supplierreturn.dto.SupplierReturnRequest;
import com.cgcpms.supplierreturn.entity.SupplierReturn;
import com.cgcpms.supplierreturn.entity.SupplierReturnItem;
import com.cgcpms.supplierreturn.mapper.SupplierReturnItemMapper;
import com.cgcpms.supplierreturn.mapper.SupplierReturnMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierReturnServiceTest {

    private static final long TENANT_ID = 9L;

    @Mock SupplierReturnMapper returnMapper;
    @Mock SupplierReturnItemMapper returnItemMapper;
    @Mock MatReceiptMapper receiptMapper;
    @Mock MatReceiptItemMapper receiptItemMapper;
    @Mock MatPurchaseOrderMapper orderMapper;
    @Mock MatPurchaseOrderItemMapper orderItemMapper;
    @Mock MatStockTxnMapper stockTxnMapper;
    @Mock CostItemMapper costItemMapper;
    @Mock MatStockService stockService;
    @Mock BudgetLedgerService budgetLedgerService;
    @Mock ProcurementIntegrityService integrityService;
    @Mock ProjectAccessChecker projectAccessChecker;
    @InjectMocks SupplierReturnService service;

    @BeforeAll
    static void initTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SupplierReturn.class);
        TableInfoHelper.initTableInfo(assistant, MatReceiptItem.class);
        TableInfoHelper.initTableInfo(assistant, com.cgcpms.purchase.entity.MatPurchaseOrder.class);
    }

    @BeforeEach
    void setUpContext() {
        UserContext.set(Jwts.claims()
                .add("userId", 7L)
                .add("username", "warehouse")
                .add("tenantId", TENANT_ID)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void rejectsUnqualifiedReturnBeyondReceiptQuantity() {
        MatReceiptItem receiptItem = receiptItem("RETURN", "PENDING");
        when(returnMapper.selectOne(any())).thenReturn(null);
        when(receiptItemMapper.selectById(101L)).thenReturn(receiptItem);
        when(receiptMapper.selectById(201L)).thenReturn(receipt());
        when(returnItemMapper.sumConfirmedQuantity(TENANT_ID, 101L, "UNQUALIFIED"))
                .thenReturn(new BigDecimal("2"));

        SupplierReturnRequest request = new SupplierReturnRequest(101L, "UNQUALIFIED",
                new BigDecimal("4"), LocalDate.of(2026, 7, 16), "检验不合格", "idem-1");

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("SUPPLIER_RETURN_EXCEEDS_SOURCE", error.getCode());
        verify(returnMapper, never()).insert(any(SupplierReturn.class));
    }

    @Test
    void confirmsUnqualifiedReturnAndReversesBudget() {
        SupplierReturn header = returnHeader("UNQUALIFIED");
        SupplierReturnItem returnItem = returnItem();
        returnItem.setQuantity(new BigDecimal("5"));
        returnItem.setAmount(new BigDecimal("50"));
        MatReceiptItem receiptItem = receiptItem("RETURN", "PENDING");
        MatPurchaseOrderItem orderItem = orderItem();
        when(returnMapper.selectById(301L)).thenReturn(header);
        when(returnItemMapper.selectOne(any())).thenReturn(returnItem);
        when(receiptItemMapper.selectById(101L)).thenReturn(receiptItem);
        when(receiptMapper.selectById(201L)).thenReturn(receipt());
        when(returnItemMapper.sumConfirmedQuantity(TENANT_ID, 101L, "UNQUALIFIED"))
                .thenReturn(BigDecimal.ZERO);
        when(orderItemMapper.selectById(401L)).thenReturn(orderItem);
        when(returnMapper.update(eq(null), any())).thenReturn(1);

        service.confirm(301L);

        verify(integrityService).requireActiveProject(501L, "确认供应商退货");
        verify(integrityService).requireCleanAttachment("SUPPLIER_RETURN", 301L);
        verify(receiptItemMapper).update(eq(null), any());
        verify(budgetLedgerService).reverse(eq(601L), eq("SUPPLIER_RETURN"), eq(301L),
                eq(new BigDecimal("50.00")), any());
    }

    @Test
    void acceptedInventoryReturnUsesOriginalReceiptStockAndDecrementsOrderReceipt() {
        SupplierReturn header = returnHeader("ACCEPTED");
        SupplierReturnItem returnItem = returnItem();
        MatReceiptItem receiptItem = receiptItem(null, null);
        MatPurchaseOrderItem orderItem = orderItem();
        orderItem.setReceivedQuantity(new BigDecimal("8"));
        MatStockTxn originalTxn = new MatStockTxn();
        originalTxn.setId(701L);
        originalTxn.setUnitCost(new BigDecimal("9.50"));
        when(returnMapper.selectById(301L)).thenReturn(header);
        when(returnItemMapper.selectOne(any())).thenReturn(returnItem);
        when(receiptItemMapper.selectById(101L)).thenReturn(receiptItem);
        when(receiptMapper.selectById(201L)).thenReturn(receipt());
        when(returnItemMapper.sumConfirmedQuantity(TENANT_ID, 101L, "ACCEPTED"))
                .thenReturn(BigDecimal.ZERO);
        when(orderItemMapper.selectById(401L)).thenReturn(orderItem);
        when(stockTxnMapper.selectOne(any())).thenReturn(originalTxn);
        when(orderItemMapper.updateById(any(MatPurchaseOrderItem.class))).thenReturn(1);
        when(returnMapper.update(eq(null), any())).thenReturn(1);

        service.confirm(301L);

        verify(stockService).stockOutValued(801L, 901L, new BigDecimal("3"),
                "SUPPLIER_RETURN", 301L, 302L);
        ArgumentCaptor<MatPurchaseOrderItem> captor = ArgumentCaptor.forClass(MatPurchaseOrderItem.class);
        verify(orderItemMapper).updateById(captor.capture());
        assertEquals(0, new BigDecimal("5").compareTo(captor.getValue().getReceivedQuantity()));
    }

    private MatReceipt receipt() {
        MatReceipt receipt = new MatReceipt();
        receipt.setId(201L);
        receipt.setTenantId(TENANT_ID);
        receipt.setProjectId(501L);
        receipt.setContractId(1001L);
        receipt.setPartnerId(1002L);
        receipt.setWarehouseId(801L);
        receipt.setOrderId(1003L);
        receipt.setReceiptMode("INVENTORY");
        receipt.setApprovalStatus("APPROVED");
        return receipt;
    }

    private MatReceiptItem receiptItem(String dispositionType, String dispositionStatus) {
        MatReceiptItem item = new MatReceiptItem();
        item.setId(101L);
        item.setTenantId(TENANT_ID);
        item.setReceiptId(201L);
        item.setOrderItemId(401L);
        item.setMaterialId(901L);
        item.setQualifiedQuantity(new BigDecimal("8"));
        item.setUnqualifiedQuantity(new BigDecimal("5"));
        item.setUnitPrice(new BigDecimal("10"));
        item.setDispositionType(dispositionType);
        item.setDispositionStatus(dispositionStatus);
        return item;
    }

    private SupplierReturn returnHeader(String kind) {
        SupplierReturn header = new SupplierReturn();
        header.setId(301L);
        header.setTenantId(TENANT_ID);
        header.setProjectId(501L);
        header.setContractId(1001L);
        header.setPartnerId(1002L);
        header.setWarehouseId(801L);
        header.setReceiptId(201L);
        header.setReturnKind(kind);
        header.setStatus("DRAFT");
        header.setReturnDate(LocalDate.of(2026, 7, 16));
        return header;
    }

    private SupplierReturnItem returnItem() {
        SupplierReturnItem item = new SupplierReturnItem();
        item.setId(302L);
        item.setTenantId(TENANT_ID);
        item.setReturnId(301L);
        item.setReceiptItemId(101L);
        item.setOrderItemId(401L);
        item.setMaterialId(901L);
        item.setQuantity(new BigDecimal("3"));
        item.setAmount(new BigDecimal("30"));
        return item;
    }

    private MatPurchaseOrderItem orderItem() {
        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setId(401L);
        item.setTenantId(TENANT_ID);
        item.setBudgetLineId(601L);
        item.setUnitPrice(new BigDecimal("10"));
        item.setReceivedQuantity(new BigDecimal("8"));
        item.setVersion(0);
        return item;
    }
}
