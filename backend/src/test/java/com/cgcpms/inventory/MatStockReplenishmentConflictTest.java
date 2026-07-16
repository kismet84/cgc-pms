package com.cgcpms.inventory;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("补货设置乐观锁冲突 fail-close")
class MatStockReplenishmentConflictTest {

    private static final long STOCK_ID = 88001L;
    private static final long WAREHOUSE_ID = 88002L;
    private static final long PROJECT_ID = 88003L;
    private static final long TENANT_ID = 0L;

    @Mock
    private MatStockMapper stockMapper;
    @Mock
    private MatStockTxnMapper stockTxnMapper;
    @Mock
    private MatWarehouseMapper warehouseMapper;
    @Mock
    private MdMaterialMapper materialMapper;
    @Mock
    private ProjectAccessChecker projectAccessChecker;
    @InjectMocks
    private MatStockService stockService;

    @BeforeEach
    void setUpContext() {
        UserContext.set(Jwts.claims()
                .add("userId", 1L)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("updateById 返回 0 时拒绝并保持持久化替身不变")
    void rejectsOptimisticLockConflictWithoutPersistingSettings() {
        MatStock persisted = stockFixture();
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setTenantId(TENANT_ID);
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setStatus("ENABLE");
        when(stockMapper.selectById(STOCK_ID)).thenAnswer(invocation -> copyStock(persisted));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse);
        when(stockMapper.updateById(any(MatStock.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stockService.updateReplenishmentSettings(
                        STOCK_ID, new BigDecimal("20.0000"), new BigDecimal("40.0000"), 9));

        assertEquals("STOCK_CONCURRENT_CONFLICT", ex.getCode());
        assertEquals(0, new BigDecimal("10.0000").compareTo(persisted.getSafetyStockQty()));
        assertEquals(0, new BigDecimal("25.0000").compareTo(persisted.getReplenishmentTargetQty()));
        assertEquals(7, persisted.getReplenishmentLeadDays());
        verify(projectAccessChecker).checkAccess(PROJECT_ID, "维护补货设置");
        verify(stockMapper).selectById(STOCK_ID);
        verify(stockMapper).updateById(any(MatStock.class));
        verify(stockMapper, never()).insert(any(MatStock.class));
        verifyNoMoreInteractions(stockMapper);
        verify(warehouseMapper).selectById(WAREHOUSE_ID);
        verifyNoMoreInteractions(warehouseMapper);
        verifyNoInteractions(stockTxnMapper, materialMapper);
    }

    private MatStock stockFixture() {
        MatStock stock = new MatStock();
        stock.setId(STOCK_ID);
        stock.setTenantId(TENANT_ID);
        stock.setWarehouseId(WAREHOUSE_ID);
        stock.setMaterialId(1001L);
        stock.setAvailableQty(new BigDecimal("80.0000"));
        stock.setSafetyStockQty(new BigDecimal("10.0000"));
        stock.setReplenishmentTargetQty(new BigDecimal("25.0000"));
        stock.setReplenishmentLeadDays(7);
        stock.setVersion(0);
        return stock;
    }

    private MatStock copyStock(MatStock source) {
        MatStock copy = new MatStock();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setWarehouseId(source.getWarehouseId());
        copy.setMaterialId(source.getMaterialId());
        copy.setAvailableQty(source.getAvailableQty());
        copy.setSafetyStockQty(source.getSafetyStockQty());
        copy.setReplenishmentTargetQty(source.getReplenishmentTargetQty());
        copy.setReplenishmentLeadDays(source.getReplenishmentLeadDays());
        copy.setVersion(source.getVersion());
        return copy;
    }
}
