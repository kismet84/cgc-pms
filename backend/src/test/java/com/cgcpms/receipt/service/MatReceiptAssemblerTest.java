package com.cgcpms.receipt.service;

import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.vo.MatReceiptItemVO;
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
class MatReceiptAssemblerTest {

    @Mock private PmProjectMapper projectMapper;
    @Mock private MatPurchaseOrderMapper orderMapper;
    @Mock private MdPartnerMapper partnerMapper;
    @Mock private CtContractMapper contractMapper;
    @Mock private MdMaterialMapper materialMapper;
    @Mock private MatPurchaseOrderItemMapper orderItemMapper;

    private MatReceiptAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new MatReceiptAssembler(projectMapper, orderMapper, partnerMapper, contractMapper,
                materialMapper, orderItemMapper);
    }

    @Test
    void assemblesMaterialAndOrderFactsWithExplicitIds() {
        MdMaterial material = new MdMaterial();
        material.setId(11L);
        material.setMaterialName("钢筋");
        material.setSpecification("HRB400");
        material.setUnit("吨");
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material));

        MatPurchaseOrderItem orderItem = new MatPurchaseOrderItem();
        orderItem.setId(21L);
        orderItem.setQuantity(new BigDecimal("10.00"));
        orderItem.setReceivedQuantity(new BigDecimal("4.00"));
        when(orderItemMapper.selectByIds(anyCollection())).thenReturn(List.of(orderItem));

        MatReceiptItem item = new MatReceiptItem();
        item.setMaterialId(11L);
        item.setOrderItemId(21L);

        MatReceiptItemVO result = assembler.assembleItems(List.of(item)).getFirst();

        assertEquals("钢筋", result.getMaterialName());
        assertEquals("HRB400", result.getSpecification());
        assertEquals("吨", result.getUnit());
        assertEquals("10.00", result.getOrderedQuantity());
        assertEquals("4.00", result.getReceivedQuantity());
        assertEquals("6.00", result.getRemainingQuantity());
    }

    @Test
    void propagatesExplicitOrderItemIdFailure() {
        MatPurchaseOrderItem broken = org.mockito.Mockito.mock(MatPurchaseOrderItem.class);
        when(broken.getId()).thenThrow(new IllegalStateException("broken id"));
        when(orderItemMapper.selectByIds(anyCollection())).thenReturn(List.of(broken));
        MatReceiptItem item = new MatReceiptItem();
        item.setOrderItemId(21L);

        assertThrows(IllegalStateException.class, () -> assembler.assembleItems(List.of(item)));
    }

    @Test
    void emptyItemsDoNotQueryMappers() {
        assertEquals(List.of(), assembler.assembleItems(List.of()));
        verify(materialMapper, never()).selectByIds(anyCollection());
        verify(orderItemMapper, never()).selectByIds(anyCollection());
    }

    @Test
    void emptyAndMissingRelationsRemainBlankWithoutExtraQueries() {
        MatReceiptItem withoutRelations = new MatReceiptItem();
        MatReceiptItemVO withoutRelationsResult = assembler.assembleItems(List.of(withoutRelations)).getFirst();
        assertNull(withoutRelationsResult.getMaterialName());
        assertNull(withoutRelationsResult.getOrderedQuantity());
        verify(materialMapper, never()).selectByIds(anyCollection());
        verify(orderItemMapper, never()).selectByIds(anyCollection());

        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of());
        when(orderItemMapper.selectByIds(anyCollection())).thenReturn(List.of());
        MatReceiptItem missingRelations = new MatReceiptItem();
        missingRelations.setMaterialId(11L);
        missingRelations.setOrderItemId(21L);
        MatReceiptItemVO missingRelationsResult = assembler.assembleItems(List.of(missingRelations)).getFirst();
        assertNull(missingRelationsResult.getMaterialName());
        assertNull(missingRelationsResult.getSpecification());
        assertNull(missingRelationsResult.getUnit());
        assertNull(missingRelationsResult.getOrderedQuantity());
        assertNull(missingRelationsResult.getReceivedQuantity());
        assertNull(missingRelationsResult.getRemainingQuantity());
    }
}
