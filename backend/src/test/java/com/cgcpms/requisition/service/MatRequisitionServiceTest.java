package com.cgcpms.requisition.service;

import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class MatRequisitionServiceTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TestUserContext.clear();
    }

    @Test
    void employeeCannotForgeRequisitionPriceBeforeAnyPersistence() {
        authenticateEmployee();
        MatRequisitionItem item = new MatRequisitionItem();
        item.setUnitPrice(new BigDecimal("12.50"));
        MatRequisitionService service = mock(MatRequisitionService.class, CALLS_REAL_METHODS);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveItemsBatch(1L, List.of(item)));

        assertEquals("AMOUNT_FIELD_FORBIDDEN", exception.getCode());
    }

    @Test
    void employeeCannotForgeRequisitionReadOnlyAmountBeforeAnyPersistence() {
        authenticateEmployee();
        MatRequisitionItem item = new MatRequisitionItem();
        item.setAmount(new BigDecimal("12.50"));
        MatRequisitionService service = mock(MatRequisitionService.class, CALLS_REAL_METHODS);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveItemsBatch(1L, List.of(item)));

        assertEquals("AMOUNT_FIELD_FORBIDDEN", exception.getCode());
    }

    @Test
    void itemValidationRequiresMaterialId() {
        MatRequisitionItem item = new MatRequisitionItem();
        item.setRequisitionId(970000000000005704L);
        item.setQuantity(new BigDecimal("104.0000"));

        assertTrue(
                validator.validate(item).stream()
                        .anyMatch(v -> "materialId".equals(v.getPropertyPath().toString())),
                "领料明细保存必须要求真实物料ID，避免名称/单位无法回填并阻断后续出库审批");
    }

    @Test
    void assemblerReturnsWarehouseBusinessIdentity() {
        MatWarehouseMapper warehouseMapper = mock(MatWarehouseMapper.class);
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setId(9515L);
        warehouse.setWarehouseCode("WH-MAIN");
        warehouse.setWarehouseName("主材料仓");
        when(warehouseMapper.selectByIds(anyCollection())).thenReturn(List.of(warehouse));

        MatRequisitionAssembler assembler = new MatRequisitionAssembler(
                mock(PmProjectMapper.class),
                mock(CtContractMapper.class),
                mock(MdPartnerMapper.class),
                mock(MdMaterialMapper.class),
                warehouseMapper);
        MatRequisition requisition = new MatRequisition();
        requisition.setWarehouseId(9515L);

        var result = assembler.assemble(requisition);

        assertEquals("9515", result.getWarehouseId());
        assertEquals("WH-MAIN", result.getWarehouseCode());
        assertEquals("主材料仓", result.getWarehouseName());
    }

    private void authenticateEmployee() {
        TestUserContext.setUser(0L, 1L, "employee", List.of("EMPLOYEE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "employee", "n/a", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"),
                new SimpleGrantedAuthority("requisition:self"))));
    }
}
