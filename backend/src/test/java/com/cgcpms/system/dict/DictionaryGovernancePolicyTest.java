package com.cgcpms.system.dict;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dict.entity.SysDictData;
import com.cgcpms.system.dict.entity.SysDictGroup;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.mapper.SysDictDataMapper;
import com.cgcpms.system.dict.mapper.SysDictGroupMapper;
import com.cgcpms.system.dict.mapper.SysDictTypeMapper;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.system.dict.service.SysDictTypeService;
import io.jsonwebtoken.Jwts;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DictionaryGovernancePolicyTest {

    private final SysDictGroupMapper groupMapper = mock(SysDictGroupMapper.class);
    private final SysDictTypeMapper typeMapper = mock(SysDictTypeMapper.class);
    private final SysDictDataMapper dataMapper = mock(SysDictDataMapper.class);
    private final SysDictDataService dataService = new SysDictDataService(dataMapper, typeMapper, groupMapper);
    private final SysDictTypeService typeService = new SysDictTypeService(typeMapper, dataMapper, groupMapper, dataService);

    @BeforeEach
    void admin() {
        UserContext.set(Jwts.claims()
                .add(JwtUtils.CLAIM_USER_ID, 1L)
                .add(JwtUtils.CLAIM_USERNAME, "admin")
                .add(JwtUtils.CLAIM_TENANT_ID, 0L)
                .add(JwtUtils.CLAIM_ROLES, List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void managementCanOnlyCreateBusinessType() {
        SysDictType request = type(99L, "new_state", "SYSTEM", "ENABLE");
        BusinessException error = assertThrows(BusinessException.class, () -> typeService.create(request));
        assertEquals("DICT_CLASS_CREATE_FORBIDDEN", error.getCode());
    }

    @Test
    void stateMachineTypeCannotMoveRenameOrDisable() {
        SysDictType existing = type(10L, "project_status", "STATE_MACHINE", "ENABLE");
        existing.setId(1001L);
        when(typeMapper.selectById(1001L)).thenReturn(existing);
        when(groupMapper.selectById(10L)).thenReturn(group(10L, "ENABLE"));
        when(groupMapper.selectById(11L)).thenReturn(group(11L, "ENABLE"));

        SysDictType disabled = type(10L, "project_status", "STATE_MACHINE", "DISABLE");
        disabled.setId(1001L);
        assertEquals("DICT_TYPE_DISABLE_PROTECTED",
                assertThrows(BusinessException.class, () -> typeService.update(disabled)).getCode());

        SysDictType moved = type(11L, "project_status", "STATE_MACHINE", "ENABLE");
        moved.setId(1001L);
        assertEquals("DICT_GROUP_IMMUTABLE",
                assertThrows(BusinessException.class, () -> typeService.update(moved)).getCode());

        SysDictType renamed = type(10L, "project_stage", "STATE_MACHINE", "ENABLE");
        renamed.setId(1001L);
        assertEquals("DICT_CODE_IMMUTABLE",
                assertThrows(BusinessException.class, () -> typeService.update(renamed)).getCode());
    }

    @Test
    void protectedItemAllowsPresentationEditButRejectsDisable() {
        SysDictType systemType = type(10L, "pay_type", "SYSTEM", "ENABLE");
        systemType.setId(20L);
        SysDictData existing = data(30L, 20L, "PROGRESS", "进度款", "ENABLE");
        when(dataMapper.selectById(30L)).thenReturn(existing);
        when(typeMapper.selectById(20L)).thenReturn(systemType);
        when(groupMapper.selectById(10L)).thenReturn(group(10L, "ENABLE"));

        SysDictData labelEdit = data(30L, 20L, "PROGRESS", "工程进度款", "ENABLE");
        labelEdit.setListClass("primary");
        labelEdit.setOrderNum(2);
        assertDoesNotThrow(() -> dataService.update(labelEdit));

        SysDictData disabled = data(30L, 20L, "PROGRESS", "工程进度款", "DISABLE");
        assertEquals("DICT_VALUE_DISABLE_PROTECTED",
                assertThrows(BusinessException.class, () -> dataService.update(disabled)).getCode());
    }

    @Test
    void protectedTypeRejectsNewAllowedValue() {
        SysDictType systemType = type(10L, "pay_type", "SYSTEM", "ENABLE");
        systemType.setId(20L);
        when(typeMapper.selectById(20L)).thenReturn(systemType);
        when(groupMapper.selectById(10L)).thenReturn(group(10L, "ENABLE"));

        SysDictData request = data(31L, 20L, "UNREVIEWED", "未评审值", "ENABLE");
        assertEquals("DICT_VALUE_CREATE_PROTECTED",
                assertThrows(BusinessException.class, () -> dataService.create(request)).getCode());
    }

    @Test
    void invoiceDefaultValueIsReservedButCustomValueRemainsMaintainable() {
        SysDictType invoiceType = type(70L, "invoice_type", "BUSINESS", "ENABLE");
        invoiceType.setId(71L);
        when(typeMapper.selectById(71L)).thenReturn(invoiceType);
        when(groupMapper.selectById(70L)).thenReturn(group(70L, "ENABLE"));

        SysDictData reserved = data(72L, 71L, "VAT_SPECIAL", "专票", "ENABLE");
        when(dataMapper.selectById(72L)).thenReturn(reserved);
        SysDictData disableReserved = data(72L, 71L, "VAT_SPECIAL", "专票", "DISABLE");
        assertEquals("DICT_VALUE_DISABLE_PROTECTED",
                assertThrows(BusinessException.class, () -> dataService.update(disableReserved)).getCode());

        SysDictData custom = data(73L, 71L, "E_INVOICE", "电子票", "ENABLE");
        when(dataMapper.selectById(73L)).thenReturn(custom);
        SysDictData changed = data(73L, 71L, "DIGITAL_INVOICE", "数电票", "ENABLE");
        assertDoesNotThrow(() -> dataService.update(changed));
    }

    @Test
    void byCodeMapperRequiresEnabledGroupAndType() throws Exception {
        Method method = SysDictTypeMapper.class.getMethod(
                "selectEnabledByCodeAndTenant", String.class, Long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());
        assertTrue(sql.contains("t.status = 'ENABLE'"));
        assertTrue(sql.contains("g.status = 'ENABLE'"));
    }

    @Test
    void disabledGroupRejectsNewItemWrite() {
        SysDictType businessType = type(80L, "invoice_type", "BUSINESS", "ENABLE");
        businessType.setId(81L);
        when(typeMapper.selectById(81L)).thenReturn(businessType);
        when(groupMapper.selectById(80L)).thenReturn(group(80L, "DISABLE"));

        SysDictData request = data(82L, 81L, "CUSTOM", "自定义票据", "ENABLE");
        assertEquals("DICT_GROUP_DISABLED",
                assertThrows(BusinessException.class, () -> dataService.create(request)).getCode());
    }

    private SysDictType type(long groupId, String code, String dictClass, String status) {
        SysDictType type = new SysDictType();
        type.setTenantId(0L);
        type.setGroupId(groupId);
        type.setDictCode(code);
        type.setDictName(code);
        type.setDictClass(dictClass);
        type.setStatus(status);
        return type;
    }

    private SysDictGroup group(long id, String status) {
        SysDictGroup group = new SysDictGroup();
        group.setId(id);
        group.setTenantId(0L);
        group.setGroupCode("GROUP_" + id);
        group.setGroupName("分组" + id);
        group.setStatus(status);
        return group;
    }

    private SysDictData data(long id, long typeId, String value, String label, String status) {
        SysDictData data = new SysDictData();
        data.setId(id);
        data.setTenantId(0L);
        data.setDictTypeId(typeId);
        data.setDictValue(value);
        data.setDictLabel(label);
        data.setStatus(status);
        return data;
    }
}
