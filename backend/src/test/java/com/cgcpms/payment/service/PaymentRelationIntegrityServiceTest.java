package com.cgcpms.payment.service;

import com.cgcpms.common.TestUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRelationIntegrityServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PaymentRelationIntegrityService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(7L, 1L);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void crossTenantInvoiceAllocationIsReportedAsBlocker() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(7L)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .contains("x.tenant_id<>i.tenant_id") ? 1L : 0L);

        var issues = service.scan();
        var blocker = issues.stream()
                .filter(issue -> issue.getIssueCode().equals("INVOICE_ALLOCATION_CROSS_TENANT_RELATION"))
                .findFirst()
                .orElseThrow();

        assertEquals(1L, blocker.getAffectedRows());
        assertEquals("BLOCKER", blocker.getSeverity());
        assertTrue(blocker.getRemediation().contains("禁止静默删除"));
    }
}
