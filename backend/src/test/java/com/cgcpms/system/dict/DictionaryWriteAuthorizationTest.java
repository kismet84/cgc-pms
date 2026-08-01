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
import com.cgcpms.system.dict.service.SysDictGroupService;
import com.cgcpms.system.dict.service.SysDictTypeService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DictionaryWriteAuthorizationTest {

    private final SysDictGroupMapper groupMapper = mock(SysDictGroupMapper.class);
    private final SysDictTypeMapper typeMapper = mock(SysDictTypeMapper.class);
    private final SysDictDataMapper dataMapper = mock(SysDictDataMapper.class);
    private final SysDictDataService dataService = new SysDictDataService(dataMapper, typeMapper, groupMapper);
    private final SysDictGroupService groupService = new SysDictGroupService(groupMapper, typeMapper);
    private final SysDictTypeService typeService = new SysDictTypeService(typeMapper, dataMapper, groupMapper, dataService);

    @BeforeEach
    void ordinaryUser() {
        UserContext.set(Jwts.claims()
                .add(JwtUtils.CLAIM_USER_ID, 88L)
                .add(JwtUtils.CLAIM_USERNAME, "ordinary")
                .add(JwtUtils.CLAIM_TENANT_ID, 0L)
                .add(JwtUtils.CLAIM_ROLES, List.of("PROJECT_MANAGER"))
                .build());
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void directServiceWritesRequireAdminRole() {
        BusinessException groupError = assertThrows(BusinessException.class,
                () -> groupService.create(new SysDictGroup()));
        BusinessException typeError = assertThrows(BusinessException.class,
                () -> typeService.create(new SysDictType()));
        BusinessException dataError = assertThrows(BusinessException.class,
                () -> dataService.create(new SysDictData()));

        assertEquals("DICT_WRITE_FORBIDDEN", groupError.getCode());
        assertEquals("DICT_WRITE_FORBIDDEN", typeError.getCode());
        assertEquals("DICT_WRITE_FORBIDDEN", dataError.getCode());
    }
}
