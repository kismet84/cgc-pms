package com.cgcpms.requisition.service;

import com.cgcpms.contract.mapper.CtContractMapper;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatRequisitionServiceTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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
}
