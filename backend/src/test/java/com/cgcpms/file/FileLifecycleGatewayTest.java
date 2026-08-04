package com.cgcpms.file;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.service.FileLifecycleGateway;
import com.cgcpms.file.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileLifecycleGatewayTest {

    @Mock private ObjectProvider<FileService> provider;
    @Mock private FileService fileService;
    @Mock private SysFileMapper mapper;

    private FileLifecycleGateway gateway;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        gateway = new FileLifecycleGateway(provider, mapper);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void delegatesWhenFileLifecycleIsAvailable() {
        when(provider.getIfAvailable()).thenReturn(fileService);

        gateway.deleteAllForBusinessCascade("CONTRACT", 1L);

        verify(fileService).deleteAllForBusinessCascade("CONTRACT", 1L);
    }

    @Test
    void allowsParentWithoutFilesWhenStorageIsDisabled() {
        when(provider.getIfAvailable()).thenReturn(null);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertDoesNotThrow(() -> gateway.deleteAllForBusinessCascade("CONTRACT", 1L));
    }

    @Test
    void protectsParentWithFilesWhenStorageIsDisabled() {
        when(provider.getIfAvailable()).thenReturn(null);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> gateway.deleteAllForBusinessCascade("CONTRACT", 1L));

        assertEquals("FILE_STORAGE_UNAVAILABLE", error.getCode());
    }
}
