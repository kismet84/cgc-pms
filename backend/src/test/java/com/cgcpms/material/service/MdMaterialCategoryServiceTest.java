package com.cgcpms.material.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.material.entity.MdMaterialCategory;
import com.cgcpms.material.mapper.MdMaterialCategoryMapper;
import com.cgcpms.material.mapper.MdMaterialMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MdMaterialCategoryServiceTest {

    private static final long TENANT_ID = 20L;
    private static final long CATEGORY_ID = 7L;

    private final MdMaterialCategoryMapper categoryMapper = mock(MdMaterialCategoryMapper.class);
    private final MdMaterialMapper materialMapper = mock(MdMaterialMapper.class);
    private final MdMaterialCategoryService service =
            new MdMaterialCategoryService(categoryMapper, materialMapper);

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims()
                .add("userId", 1L)
                .add("username", "material-admin")
                .add("tenantId", TENANT_ID)
                .build());
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(category(TENANT_ID));
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void updateStatusNormalizesAllowedValue() {
        service.updateStatus(CATEGORY_ID, " disable ");

        MdMaterialCategory category = categoryMapper.selectById(CATEGORY_ID);
        assertEquals("DISABLE", category.getStatus());
        verify(categoryMapper).updateById(category);
    }

    @Test
    void updateStatusRejectsUnknownValueWithoutWriting() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatus(CATEGORY_ID, "ARCHIVED"));

        assertEquals("MATERIAL_CATEGORY_STATUS_INVALID", error.getCode());
        verify(categoryMapper, never()).updateById(any(MdMaterialCategory.class));
    }

    @Test
    void deleteRejectsCategoryWithChildren() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.delete(CATEGORY_ID));

        assertEquals("MATERIAL_CATEGORY_HAS_CHILDREN", error.getCode());
        verifyNoInteractions(materialMapper);
        verify(categoryMapper, never()).deleteById(CATEGORY_ID);
    }

    @Test
    void deleteRejectsCategoryReferencedByMaterials() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(materialMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.delete(CATEGORY_ID));

        assertEquals("MATERIAL_CATEGORY_IN_USE", error.getCode());
        verify(categoryMapper, never()).deleteById(CATEGORY_ID);
    }

    @Test
    void statusAndDeleteFailClosedAcrossTenants() {
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(category(TENANT_ID + 1));

        assertEquals("MATERIAL_CATEGORY_NOT_FOUND",
                assertThrows(BusinessException.class,
                        () -> service.updateStatus(CATEGORY_ID, "ENABLE")).getCode());
        assertEquals("MATERIAL_CATEGORY_NOT_FOUND",
                assertThrows(BusinessException.class,
                        () -> service.delete(CATEGORY_ID)).getCode());
        verify(categoryMapper, never()).updateById(any(MdMaterialCategory.class));
        verify(categoryMapper, never()).deleteById(CATEGORY_ID);
    }

    private MdMaterialCategory category(Long tenantId) {
        MdMaterialCategory category = new MdMaterialCategory();
        category.setId(CATEGORY_ID);
        category.setTenantId(tenantId);
        category.setStatus("ENABLE");
        return category;
    }
}
