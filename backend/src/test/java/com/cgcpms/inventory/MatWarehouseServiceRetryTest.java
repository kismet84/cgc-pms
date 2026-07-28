package com.cgcpms.inventory;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.util.BusinessCodeGenerator;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.service.MatWarehouseService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatWarehouseServiceRetryTest {

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void retriesAfterDatabaseUniqueConflict() {
        TestUserContext.setAdmin(1L, 9L);
        MatWarehouseMapper warehouseMapper = mock(MatWarehouseMapper.class);
        BusinessCodeGenerator generator = mock(BusinessCodeGenerator.class);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        MatWarehouseService service = new MatWarehouseService(
                warehouseMapper,
                mock(MatStockMapper.class),
                mock(PmProjectMapper.class),
                accessChecker,
                generator);
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(88L);
        warehouse.setWarehouseName("并发仓库");
        warehouse.setStatus("ENABLE");

        when(generator.next(BusinessCodeGenerator.Rule.WAREHOUSE, 88L, 0))
                .thenReturn("WH-20260728-001");
        when(generator.next(BusinessCodeGenerator.Rule.WAREHOUSE, 88L, 1))
                .thenReturn("WH-20260728-002");
        doThrow(new DuplicateKeyException("duplicate"))
                .doAnswer(invocation -> {
                    warehouse.setId(99L);
                    return 1;
                })
                .when(warehouseMapper).insert(warehouse);

        assertEquals(99L, service.create(warehouse));
        assertEquals("WH-20260728-002", warehouse.getWarehouseCode());
        verify(accessChecker).checkAccess(88L, "创建仓库");
        verify(warehouseMapper, org.mockito.Mockito.times(2)).insert(warehouse);
    }
}
