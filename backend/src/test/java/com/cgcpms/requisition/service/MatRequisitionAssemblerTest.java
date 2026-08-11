package com.cgcpms.requisition.service;

import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.requisition.vo.MatRequisitionItemVO;
import com.cgcpms.requisition.vo.MatRequisitionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatRequisitionAssemblerTest {

    @Mock private PmProjectMapper projectMapper;
    @Mock private CtContractMapper contractMapper;
    @Mock private MdPartnerMapper partnerMapper;
    @Mock private MdMaterialMapper materialMapper;
    @Mock private MatWarehouseMapper warehouseMapper;

    private MatRequisitionAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new MatRequisitionAssembler(projectMapper, contractMapper, partnerMapper,
                materialMapper, warehouseMapper);
    }

    @Test
    void assemblesMaterialAndWarehouseFactsWithExplicitIds() {
        MdMaterial material = new MdMaterial();
        material.setId(31L);
        material.setMaterialName("水泥");
        material.setSpecification("P.O 42.5");
        material.setUnit("吨");
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material));

        MatRequisitionItem item = new MatRequisitionItem();
        item.setMaterialId(31L);
        item.setQuantity(new BigDecimal("2.00"));
        MatRequisitionItemVO itemResult = assembler.assembleItems(List.of(item)).getFirst();
        assertEquals("水泥", itemResult.getMaterialName());
        assertEquals("P.O 42.5", itemResult.getSpecification());
        assertEquals("吨", itemResult.getUnit());

        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setId(41L);
        warehouse.setWarehouseCode("WH-41");
        warehouse.setWarehouseName("主仓");
        when(warehouseMapper.selectByIds(anyCollection())).thenReturn(List.of(warehouse));
        MatRequisition requisition = new MatRequisition();
        requisition.setWarehouseId(41L);

        MatRequisitionVO result = assembler.assemble(requisition);
        assertEquals("WH-41", result.getWarehouseCode());
        assertEquals("主仓", result.getWarehouseName());
    }

    @Test
    void propagatesExplicitWarehouseIdFailure() {
        MatWarehouse broken = org.mockito.Mockito.mock(MatWarehouse.class);
        when(broken.getId()).thenThrow(new IllegalStateException("broken id"));
        when(warehouseMapper.selectByIds(anyCollection())).thenReturn(List.of(broken));
        MatRequisition requisition = new MatRequisition();
        requisition.setWarehouseId(41L);

        assertThrows(IllegalStateException.class, () -> assembler.assemble(requisition));
    }

    @Test
    void emptyItemsDoNotQueryMaterialMapper() {
        assertEquals(List.of(), assembler.assembleItems(List.of()));
        verify(materialMapper, never()).selectByIds(anyCollection());
    }

    @Test
    void emptyAndMissingRelationsRemainBlankWithoutExtraQueries() {
        MatRequisitionItem withoutMaterial = new MatRequisitionItem();
        MatRequisitionItemVO withoutMaterialResult = assembler.assembleItems(List.of(withoutMaterial)).getFirst();
        assertNull(withoutMaterialResult.getMaterialName());
        verify(materialMapper, never()).selectByIds(anyCollection());

        MatRequisition withoutWarehouse = new MatRequisition();
        MatRequisitionVO withoutWarehouseResult = assembler.assemble(withoutWarehouse);
        assertNull(withoutWarehouseResult.getWarehouseCode());
        assertNull(withoutWarehouseResult.getWarehouseName());
        verify(warehouseMapper, never()).selectByIds(anyCollection());

        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of());
        MatRequisitionItem missingMaterial = new MatRequisitionItem();
        missingMaterial.setMaterialId(31L);
        MatRequisitionItemVO missingMaterialResult = assembler.assembleItems(List.of(missingMaterial)).getFirst();
        assertNull(missingMaterialResult.getMaterialName());
        assertNull(missingMaterialResult.getSpecification());
        assertNull(missingMaterialResult.getUnit());

        when(warehouseMapper.selectByIds(anyCollection())).thenReturn(List.of());
        MatRequisition missingWarehouse = new MatRequisition();
        missingWarehouse.setWarehouseId(41L);
        MatRequisitionVO missingWarehouseResult = assembler.assemble(missingWarehouse);
        assertNull(missingWarehouseResult.getWarehouseCode());
        assertNull(missingWarehouseResult.getWarehouseName());
    }
}
