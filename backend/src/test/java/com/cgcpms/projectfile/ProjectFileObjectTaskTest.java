package com.cgcpms.projectfile;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.service.FileObjectTaskService;
import com.cgcpms.file.service.FileService;
import com.cgcpms.file.service.ProjectFileProjection;
import com.cgcpms.project.auth.ProjectAccessChecker;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ProjectFileObjectTaskTest {

    @Test
    void previewTaskIsPersistentIdempotentAndFailedTaskCanRetry() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:project_file_task;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE sys_file_object_task(
                    id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,operation VARCHAR(20) NOT NULL,
                    source_bucket VARCHAR(100) NOT NULL,source_path VARCHAR(500) NOT NULL,reference_id BIGINT,
                    idempotency_key VARCHAR(700) NOT NULL,status VARCHAR(20) NOT NULL,attempt_count INT NOT NULL,
                    next_retry_at TIMESTAMP,last_error_code VARCHAR(100),completed_at TIMESTAMP,
                    created_at TIMESTAMP,updated_at TIMESTAMP,
                    CONSTRAINT uk_project_file_task UNIQUE(tenant_id,idempotency_key))
                """);
        @SuppressWarnings("unchecked")
        ObjectProvider<FileObjectTaskService> self = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProjectFileProjection> projectFiles = mock(ObjectProvider.class);
        ProjectFileProjection projection = mock(ProjectFileProjection.class);
        when(projectFiles.getObject()).thenReturn(projection);
        FileObjectTaskService service = new FileObjectTaskService(
                jdbc, mock(MinioClient.class), self, projectFiles);

        long first = service.enqueuePreviewConvert(7, 101, 201, "source.docx");
        long duplicate = service.enqueuePreviewConvert(7, 101, 201, "ignored.docx");

        assertEquals(first, duplicate);
        assertEquals("PREVIEW_CONVERT", jdbc.queryForObject(
                "SELECT operation FROM sys_file_object_task WHERE id=?", String.class, first));
        assertEquals(201L, jdbc.queryForObject(
                "SELECT reference_id FROM sys_file_object_task WHERE id=?", Long.class, first));

        jdbc.update("UPDATE sys_file_object_task SET status='FAILED',attempt_count=10 WHERE id=?", first);
        service.enqueuePreviewConvert(7, 101, 202, "source.docx");
        assertEquals("RETRY", jdbc.queryForObject(
                "SELECT status FROM sys_file_object_task WHERE id=?", String.class, first));
        assertEquals(202L, jdbc.queryForObject(
                "SELECT reference_id FROM sys_file_object_task WHERE id=?", Long.class, first));

        service.processNow(first);
        verify(projection).processConversionTask(202L);
        assertEquals("SUCCEEDED", jdbc.queryForObject(
                "SELECT status FROM sys_file_object_task WHERE id=?", String.class, first));
    }

    @Test
    void previewClassificationDoesNotSendLegacyOfficeToConverter() {
        assertEquals("DIRECT", ProjectFileService.previewKind("drawing.pdf", "application/pdf"));
        assertEquals("DIRECT", ProjectFileService.previewKind("photo.bin", "image/png"));
        assertEquals("OOXML", ProjectFileService.previewKind("plan.DOCX", "application/octet-stream"));
        assertEquals("UNSUPPORTED", ProjectFileService.previewKind("legacy.doc", "application/msword"));
    }

    @Test
    void invalidationDeletesDerivedPreviewOnlyAfterCommit() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project_file_invalidation;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE project_file_catalog(
                    id BIGINT PRIMARY KEY,tenant_id BIGINT,source_kind VARCHAR(20),
                    deleted_flag INT,updated_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE project_file_version_link(
                    id BIGINT PRIMARY KEY,tenant_id BIGINT,catalog_id BIGINT,sys_file_id BIGINT,
                    preview_storage_path VARCHAR(500),deleted_flag INT,updated_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO project_file_catalog VALUES(20,7,'BUSINESS',0,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO project_file_version_link VALUES(30,7,20,10,'derived/preview.pdf',0,CURRENT_TIMESTAMP)");
        FileService files = mock(FileService.class);
        ProjectFileService service = new ProjectFileService(jdbc, mock(ProjectAccessChecker.class),
                mock(BusinessObjectAuthorizer.class), files, mock(FileObjectTaskService.class),
                mock(OfficePreviewClient.class));
        var source = new com.cgcpms.file.entity.SysFile();
        source.setId(10L);
        source.setTenantId(7L);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transaction.executeWithoutResult(status -> {
            service.invalidateBusinessFile(source);
            verify(files, never()).deleteDerivedPreviewLater(7L, "derived/preview.pdf");
        });

        verify(files).deleteDerivedPreviewLater(7L, "derived/preview.pdf");
        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted_flag FROM project_file_version_link WHERE id=30", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted_flag FROM project_file_catalog WHERE id=20", Integer.class));
    }

    @Test
    void historicalCategoryMappingIsFrozenAndDocumentTypeWins() {
        assertEquals("CONTRACT", ProjectFileService.categoryFor("CONTRACT", "OTHER"));
        assertEquals("FINANCE", ProjectFileService.categoryFor("PROJECT", "BANK_RECEIPT"));
        assertEquals("QUALITY_SAFETY", ProjectFileService.categoryFor("QS_ISSUE", null));
        assertEquals("OTHER", ProjectFileService.categoryFor("PROJECT", "OTHER"));
    }

    @Test
    void previewReturnsDirectUnsupportedReadyFailedPendingAndScanStates() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project_file_preview;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_code VARCHAR(50),deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE project_file_catalog(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,
                source_kind VARCHAR(20),source_business_type VARCHAR(50),source_business_id BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE project_file_version_link(id BIGINT PRIMARY KEY,tenant_id BIGINT,catalog_id BIGINT,
                sys_file_id BIGINT,preview_status VARCHAR(20),preview_storage_path VARCHAR(500),
                preview_error_code VARCHAR(100),preview_updated_at TIMESTAMP,updated_at TIMESTAMP,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE sys_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,business_type VARCHAR(50),business_id BIGINT,
                original_name VARCHAR(200),content_type VARCHAR(120),virus_scan_status VARCHAR(20),deleted_flag INT)
                """);
        jdbc.update("INSERT INTO pm_project VALUES(1,7,'P1',0)");
        jdbc.update("INSERT INTO project_file_catalog VALUES(20,7,1,'MANAGED',NULL,NULL,0)");
        jdbc.update("INSERT INTO project_file_version_link VALUES(30,7,20,10,'PENDING',NULL,NULL,NULL,CURRENT_TIMESTAMP,0)");
        jdbc.update("INSERT INTO sys_file VALUES(10,7,'PROJECT_FILE',20,'file.pdf','application/pdf','CLEAN',0)");
        FileService files = mock(FileService.class);
        FileObjectTaskService tasks = mock(FileObjectTaskService.class);
        OfficePreviewClient converter = mock(OfficePreviewClient.class);
        when(files.getPresignedFileUrl(10L)).thenReturn(
                new FileService.PresignedFileUrl("direct-url", "PROJECT_FILE", 20L, 10L));
        when(files.getDerivedPreviewPresignedUrl(10L, "preview.pdf")).thenReturn("derived-url");
        ProjectFileService service = new ProjectFileService(jdbc, mock(ProjectAccessChecker.class),
                mock(BusinessObjectAuthorizer.class), files, tasks, converter);
        TestUserContext.setAdmin(7L, 1L);
        try {
            assertEquals("direct-url", service.preview(30L).url());

            jdbc.update("UPDATE sys_file SET original_name='file.txt',content_type='text/plain'");
            assertEquals("UNSUPPORTED", service.preview(30L).status());

            jdbc.update("UPDATE sys_file SET original_name='file.docx',content_type='application/octet-stream'");
            jdbc.update("UPDATE project_file_version_link SET preview_status='READY',preview_storage_path='preview.pdf'");
            assertEquals("derived-url", service.preview(30L).url());

            jdbc.update("UPDATE project_file_version_link SET preview_status='FAILED',preview_storage_path=NULL,preview_error_code=NULL");
            assertEquals("OFFICE_PREVIEW_CONVERSION_FAILED", service.preview(30L).errorCode());
            jdbc.update("UPDATE project_file_version_link SET preview_error_code='CUSTOM_PREVIEW_ERROR'");
            assertEquals("CUSTOM_PREVIEW_ERROR", service.preview(30L).errorCode());

            jdbc.update("UPDATE project_file_version_link SET preview_status='READY',preview_storage_path=NULL");
            assertEquals("PROCESSING", service.preview(30L).status());

            jdbc.update("UPDATE project_file_version_link SET preview_status='PENDING'");
            assertEquals("PROCESSING", service.preview(30L).status());

            jdbc.update("UPDATE sys_file SET virus_scan_status='PENDING'");
            assertEquals("FILE_VIRUS_SCAN_REQUIRED", assertThrows(BusinessException.class,
                    () -> service.preview(30L)).getCode());

            jdbc.update("UPDATE sys_file SET virus_scan_status='CLEAN',original_name='file.pdf',content_type='application/pdf'");
            service.processConversionTask(30L);
            assertEquals("UNSUPPORTED", jdbc.queryForObject(
                    "SELECT preview_status FROM project_file_version_link WHERE id=30", String.class));

            jdbc.update("UPDATE sys_file SET original_name='file.docx',content_type='application/octet-stream'");
            byte[] sourceBytes = new byte[]{1};
            byte[] pdfBytes = "%PDF-ok".getBytes();
            when(files.readCleanObjectForInternalConversion(7L, 10L)).thenReturn(
                    new FileService.InternalFileContent(sourceBytes, "file.docx", "source-sha"));
            doReturn(pdfBytes).when(converter).convert(sourceBytes, "file.docx");
            when(files.storeDerivedPreview(7L, 10L, "source-sha", pdfBytes))
                    .thenReturn("stored.pdf");
            service.processConversionTask(30L);
            assertEquals("READY", jdbc.queryForObject(
                    "SELECT preview_status FROM project_file_version_link WHERE id=30", String.class));

            when(converter.convert(sourceBytes, "file.docx")).thenThrow(new IllegalStateException("converter down"));
            assertThrows(IllegalStateException.class, () -> service.processConversionTask(30L));
            assertEquals("OFFICE_PREVIEW_CONVERSION_FAILED", jdbc.queryForObject(
                    "SELECT preview_error_code FROM project_file_version_link WHERE id=30", String.class));

            doThrow(new BusinessException("CUSTOM_CONVERSION_ERROR", "failed"))
                    .when(converter).convert(sourceBytes, "file.docx");
            assertThrows(BusinessException.class, () -> service.processConversionTask(30L));
            assertEquals("CUSTOM_CONVERSION_ERROR", jdbc.queryForObject(
                    "SELECT preview_error_code FROM project_file_version_link WHERE id=30", String.class));

            jdbc.update("UPDATE project_file_version_link SET preview_status='PENDING',deleted_flag=0 WHERE id=30");
            doReturn(pdfBytes).when(converter).convert(sourceBytes, "file.docx");
            when(files.storeDerivedPreview(7L, 10L, "source-sha", pdfBytes)).thenAnswer(ignored -> {
                jdbc.update("UPDATE project_file_version_link SET deleted_flag=1 WHERE id=30");
                return "orphan.pdf";
            });
            service.processConversionTask(30L);
            verify(files).deleteDerivedPreviewLater(7L, "orphan.pdf");

            jdbc.update("""
                    INSERT INTO sys_file VALUES
                    (11,7,'PROJECT',1,'project.txt','text/plain','CLEAN',0),
                    (12,7,'PARTNER',2,'partner.txt','text/plain','CLEAN',0)
                    """);
            ProjectFileModels.ImportPreview importPreview = service.previewDirectProjectImport();
            assertEquals(2, importPreview.eligibleCount());
            assertEquals(1, importPreview.resolvableCount());
            assertEquals(1, importPreview.exceptionCount());
        } finally {
            TestUserContext.clear();
        }
    }

    @Test
    void historicalBatchesUseOneCreatedAtIdOrderAcrossOrdinaryAndBidFiles() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project_file_order;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_code VARCHAR(50),deleted_flag INT)");
        jdbc.execute("CREATE TABLE project_file_code_scope(tenant_id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE sys_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,business_type VARCHAR(50),
                document_type VARCHAR(50),business_id BIGINT,original_name VARCHAR(200),content_type VARCHAR(100),
                created_by BIGINT,created_at TIMESTAMP,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE project_file_catalog(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,
                file_code VARCHAR(100) UNIQUE,display_name VARCHAR(200),category_code VARCHAR(50),source_kind VARCHAR(20),
                source_business_type VARCHAR(50),source_business_id BIGINT,maintain_mode VARCHAR(20),created_by BIGINT,
                updated_by BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE project_file_version_link(id BIGINT PRIMARY KEY,tenant_id BIGINT,catalog_id BIGINT,
                version_no INT,sys_file_id BIGINT,source_version_type VARCHAR(50),source_version_id BIGINT,
                preview_status VARCHAR(20),created_by BIGINT,updated_by BIGINT,created_at TIMESTAMP,
                updated_at TIMESTAMP,deleted_flag INT)
                """);
        jdbc.execute("CREATE TABLE bid_cost(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE bid_document_version(id BIGINT PRIMARY KEY,tenant_id BIGINT,bid_cost_id BIGINT,
                logical_name VARCHAR(100),version_no INT,sys_file_id BIGINT,deleted_flag INT)
                """);
        jdbc.update("INSERT INTO pm_project VALUES(1,7,'P1',0)");
        jdbc.update("INSERT INTO bid_cost VALUES(10,7,1,0)");
        jdbc.update("""
                INSERT INTO sys_file VALUES
                (200,7,'PROJECT','OTHER',1,'early.txt','text/plain',1,TIMESTAMP '2026-08-05 08:00:00',0),
                (100,7,'PROJECT','OTHER',1,'later.txt','text/plain',1,TIMESTAMP '2026-08-05 09:00:00',0),
                (300,7,'BID_COST','TENDER_DOCUMENT',10,'bid.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',1,TIMESTAMP '2026-08-05 10:00:00',0)
                """);
        jdbc.update("INSERT INTO bid_document_version VALUES(400,7,10,'投标文件',1,300,0)");
        TestUserContext.setAdmin(7L, 1L);
        try {
            ProjectFileService service = new ProjectFileService(jdbc, mock(ProjectAccessChecker.class),
                    mock(BusinessObjectAuthorizer.class), mock(FileService.class),
                    mock(FileObjectTaskService.class), mock(OfficePreviewClient.class));

            long cursor = service.importDirectProjectFiles(0, 1).lastFileId();
            cursor = service.importDirectProjectFiles(cursor, 1).lastFileId();
            service.importDirectProjectFiles(cursor, 1);

            assertEquals(java.util.List.of("early.txt", "later.txt", "投标文件"), jdbc.queryForList(
                    "SELECT display_name FROM project_file_catalog ORDER BY file_code", String.class));
            assertEquals(java.util.List.of("FILE-20260805-001", "FILE-20260805-002", "FILE-20260805-003"),
                    jdbc.queryForList("SELECT file_code FROM project_file_catalog ORDER BY file_code", String.class));
        } finally {
            TestUserContext.clear();
        }
    }

    @Test
    void historicalImportCompletesPartiallyProjectedBidChainIdempotently() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project_file_partial_bid;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_code VARCHAR(50),deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE sys_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,business_type VARCHAR(50),
                document_type VARCHAR(50),business_id BIGINT,original_name VARCHAR(200),content_type VARCHAR(100),
                created_by BIGINT,created_at TIMESTAMP,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE project_file_catalog(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,
                file_code VARCHAR(100) UNIQUE,display_name VARCHAR(200),category_code VARCHAR(50),source_kind VARCHAR(20),
                source_business_type VARCHAR(50),source_business_id BIGINT,maintain_mode VARCHAR(20),created_by BIGINT,
                updated_by BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE project_file_version_link(id BIGINT PRIMARY KEY,tenant_id BIGINT,catalog_id BIGINT,
                version_no INT,sys_file_id BIGINT UNIQUE,source_version_type VARCHAR(50),source_version_id BIGINT,
                preview_status VARCHAR(20),created_by BIGINT,updated_by BIGINT,created_at TIMESTAMP,
                updated_at TIMESTAMP,deleted_flag INT, UNIQUE(catalog_id,version_no))
                """);
        jdbc.execute("CREATE TABLE bid_cost(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE bid_document_version(id BIGINT PRIMARY KEY,tenant_id BIGINT,bid_cost_id BIGINT,
                logical_name VARCHAR(100),version_no INT,sys_file_id BIGINT,deleted_flag INT)
                """);
        jdbc.update("INSERT INTO pm_project VALUES(1,7,'P1',0)");
        jdbc.update("INSERT INTO bid_cost VALUES(10,7,1,0)");
        jdbc.update("""
                INSERT INTO sys_file VALUES
                (101,7,'BID_COST','TENDER_DOCUMENT',10,'bid-v1.docx','application/octet-stream',1,TIMESTAMP '2026-08-05 08:00:00',0),
                (102,7,'BID_COST','TENDER_DOCUMENT',10,'bid-v2.docx','application/octet-stream',1,TIMESTAMP '2026-08-05 09:00:00',0),
                (103,7,'BID_COST','TENDER_DOCUMENT',10,'bid-v3.docx','application/octet-stream',1,TIMESTAMP '2026-08-05 10:00:00',0)
                """);
        jdbc.update("""
                INSERT INTO bid_document_version VALUES
                (201,7,10,'投标文件',1,101,0),(202,7,10,'投标文件',2,102,0),(203,7,10,'投标文件',3,103,0)
                """);
        jdbc.update("""
                INSERT INTO project_file_catalog VALUES
                (301,7,1,'FILE-P1-20260805-001','投标文件','BID','BUSINESS','BID_COST',10,'READ_ONLY',1,1,
                 CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """);
        jdbc.update("""
                INSERT INTO project_file_version_link VALUES
                (401,7,301,3,103,'BID_DOCUMENT_VERSION',203,'PENDING',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """);
        TestUserContext.setAdmin(7L, 1L);
        try {
            ProjectFileService service = new ProjectFileService(jdbc, mock(ProjectAccessChecker.class),
                    mock(BusinessObjectAuthorizer.class), mock(FileService.class),
                    mock(FileObjectTaskService.class), mock(OfficePreviewClient.class));

            assertEquals(1, service.importDirectProjectFiles(0, 10).importedCount());
            assertEquals(3L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_file_version_link WHERE catalog_id=301 AND deleted_flag=0", Long.class));
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_file_catalog WHERE tenant_id=7 AND source_business_id=10", Long.class));
            assertEquals(0, service.importDirectProjectFiles(0, 10).importedCount());
        } finally {
            TestUserContext.clear();
        }
    }

    @Test
    void reconciliationFindsUnindexedFilesVersionGapsAndWrongProjects() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:project_file_reconcile;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_code VARCHAR(50),deleted_flag INT)");
        jdbc.execute("CREATE TABLE sys_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,business_type VARCHAR(50),business_id BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE project_file_catalog(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,source_kind VARCHAR(16),source_business_type VARCHAR(50),source_business_id BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE project_file_version_link(id BIGINT PRIMARY KEY,tenant_id BIGINT,catalog_id BIGINT,version_no INT,sys_file_id BIGINT,preview_status VARCHAR(16),preview_storage_path VARCHAR(500),deleted_flag INT)");
        jdbc.update("INSERT INTO pm_project VALUES(1,7,'P1',0),(2,7,'P2',0)");
        jdbc.update("INSERT INTO sys_file VALUES(10,7,'PROJECT',1,0),(11,7,'PROJECT',1,0),(12,7,'PROJECT',1,0)");
        jdbc.update("INSERT INTO project_file_catalog VALUES(20,7,2,'BUSINESS','PROJECT',1,0)");
        jdbc.update("INSERT INTO project_file_version_link VALUES(30,7,20,1,10,'UNSUPPORTED',NULL,0),(31,7,20,3,11,'UNSUPPORTED',NULL,0)");
        TestUserContext.setAdmin(7L, 1L);
        try {
            ProjectFileService service = new ProjectFileService(jdbc, mock(ProjectAccessChecker.class),
                    mock(BusinessObjectAuthorizer.class), mock(FileService.class),
                    mock(FileObjectTaskService.class), mock(OfficePreviewClient.class));
            ProjectFileModels.Reconciliation report = service.reconcile();
            assertEquals(1, report.unindexedResolvableCount());
            assertEquals(1, report.versionGapCatalogCount());
            assertEquals(1, report.wrongProjectCount());
        } finally {
            TestUserContext.clear();
        }
    }

    @Test
    void historicalProjectDateOver999StopsBeforeAnyCatalogWrite() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project_file_capacity;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_code VARCHAR(50),deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE sys_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,business_type VARCHAR(50),
                document_type VARCHAR(50),business_id BIGINT,original_name VARCHAR(200),content_type VARCHAR(100),
                created_by BIGINT,created_at TIMESTAMP,deleted_flag INT)
                """);
        jdbc.execute("CREATE TABLE project_file_catalog(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,file_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE project_file_version_link(tenant_id BIGINT,sys_file_id BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE bid_cost(id BIGINT,tenant_id BIGINT,project_id BIGINT,deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE bid_document_version(tenant_id BIGINT,bid_cost_id BIGINT,logical_name VARCHAR(100),
                sys_file_id BIGINT,deleted_flag INT)
                """);
        jdbc.update("INSERT INTO pm_project VALUES(1,7,'P1',0)");
        for (int index = 1; index <= 1000; index++) {
            jdbc.update("""
                    INSERT INTO sys_file VALUES(?,7,'PROJECT','OTHER',1,?,'text/plain',1,
                    TIMESTAMP '2026-08-05 08:00:00',0)
                    """, index, "file-" + index);
        }
        TestUserContext.setAdmin(7L, 1L);
        try {
            ProjectFileService service = new ProjectFileService(jdbc, mock(ProjectAccessChecker.class),
                    mock(BusinessObjectAuthorizer.class), mock(FileService.class),
                    mock(FileObjectTaskService.class), mock(OfficePreviewClient.class));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.importDirectProjectFiles(0, 100));

            assertEquals("PROJECT_FILE_CODE_EXHAUSTED", error.getCode());
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM project_file_catalog", Long.class));
        } finally {
            TestUserContext.clear();
        }
    }
}
