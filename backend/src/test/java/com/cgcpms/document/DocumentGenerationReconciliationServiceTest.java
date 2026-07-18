package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.document.mapper.DocumentGenerationMapper;
import com.cgcpms.document.service.DocumentGenerationPersistenceService;
import com.cgcpms.document.service.DocumentGenerationReconciliationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationReconciliationServiceTest {
    @Mock private DocumentGenerationMapper mapper;
    @Mock private DocumentGenerationPersistenceService persistence;
    @InjectMocks private DocumentGenerationReconciliationService service;

    @BeforeEach
    void setUp() { TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN); }

    @AfterEach
    void tearDown() { TestUserContext.clear(); }

    @Test
    void marksStaleWorkFailedAndReportsReferencesWithoutDeletingFiles() {
        when(mapper.selectStaleIds(eq(TestUserContext.TENANT_0), any(LocalDateTime.class))).thenReturn(List.of(10L));
        when(mapper.selectBrokenSuccessfulIds(TestUserContext.TENANT_0)).thenReturn(List.of(20L));
        when(mapper.selectOrphanGeneratedFileIds(TestUserContext.TENANT_0)).thenReturn(List.of(30L));

        var result = service.reconcileCurrentTenant(30);

        verify(persistence).fail(10L, TestUserContext.TENANT_0, "DOCUMENT_GENERATION_STALE");
        assertEquals(List.of(20L), result.brokenSuccessfulIds());
        assertEquals(List.of(30L), result.orphanGeneratedFileIds());
    }
}
