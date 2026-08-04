package com.cgcpms.file;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.config.MinioConfig;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.scan.VirusScanner;
import com.cgcpms.file.service.FileMaintenanceService;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileMaintenanceServiceTest {

    @Mock private SysFileMapper fileMapper;
    @Mock private MinioClient minioClient;
    @Mock private VirusScanner virusScanner;
    @Mock private JdbcTemplate jdbcTemplate;

    private FileMaintenanceService service;
    private SysFile file;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(7L, 1L);
        MinioConfig config = new MinioConfig();
        config.setBucket("files");
        service = new FileMaintenanceService(fileMapper, minioClient, config, virusScanner, jdbcTemplate);
        file = new SysFile();
        file.setId(11L);
        file.setTenantId(7L);
        file.setBucketName("files");
        file.setStoragePath("tenants/7/CONTRACT/3/files/11/a.pdf");
        when(fileMapper.selectList(any())).thenReturn(List.of(file));
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void reconciliationReportsTenantObjectOrphanWithoutRepairingIt() throws Exception {
        @SuppressWarnings("unchecked") Result<Item> active = mock(Result.class);
        @SuppressWarnings("unchecked") Result<Item> orphan = mock(Result.class);
        Item activeItem = mock(Item.class);
        Item orphanItem = mock(Item.class);
        when(active.get()).thenReturn(activeItem);
        when(orphan.get()).thenReturn(orphanItem);
        when(activeItem.objectName()).thenReturn(file.getStoragePath());
        when(orphanItem.objectName()).thenReturn("tenants/7/CONTRACT/3/files/12/orphan.pdf");
        when(minioClient.listObjects(any())).thenReturn(List.of(active, orphan));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        var report = service.reconcile();

        assertEquals(1, report.activeMetadata());
        assertEquals(2, report.tenantObjectCount());
        assertEquals(1, report.orphanObjectCount());
        assertEquals(1, report.orphanObjectPathSample().size());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rescanPersistsRealScannerOutcomeAndReturnsCursor() throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        byte[] content = "%PDF-1.4 infected".getBytes(StandardCharsets.US_ASCII);
        when(minioClient.getObject(any())).thenReturn(response);
        when(response.readNBytes(anyInt())).thenReturn(content);
        when(virusScanner.scan(content)).thenReturn(VirusScanner.ScanResult.infected("Eicar-Test-Signature"));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        var report = service.rescan(0, 100);

        assertEquals(11L, report.nextAfterId());
        assertEquals(1, report.processed());
        assertEquals(1, report.infected());
        assertTrue(!report.hasMore());
        verify(virusScanner).scan(content);
    }
}
