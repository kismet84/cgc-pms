package com.cgcpms.accounting;

import com.cgcpms.accounting.strategy.AccountingSubjectResolver;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountingSubjectResolverTest {

    private static final long TENANT = 307L;
    private final CostSubjectMapper subjectMapper = mock(CostSubjectMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AccountingSubjectResolver resolver = new AccountingSubjectResolver(subjectMapper, jdbc);

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims()
                .subject("accounting-resolver")
                .add("userId", 1L)
                .add("tenantId", TENANT)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void fundAccountUsesExplicitAccountingSubjectCode() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(),
                eq(TENANT), eq(901L))).thenReturn(List.of("1002.03"));
        CostSubject projectBank = new CostSubject();
        projectBank.setId(100203L);
        projectBank.setSubjectCode("1002.03");
        when(subjectMapper.selectOne(any())).thenReturn(projectBank);

        assertEquals("1002.03", resolver.requireFundAccount(901L).getSubjectCode());
    }

    @Test
    void fundAccountWithoutClassificationFailsClosed() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(),
                anyLong(), anyLong())).thenReturn(Collections.singletonList(null));

        BusinessException error = assertThrows(BusinessException.class,
                () -> resolver.requireFundAccount(902L));

        assertEquals("FUND_ACCOUNT_ACCOUNTING_SUBJECT_REQUIRED", error.getCode());
    }

    @Test
    void unavailableFundAccountKeepsDedicatedError() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(),
                anyLong(), anyLong())).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> resolver.requireFundAccount(903L));

        assertEquals("FUND_ACCOUNT_UNAVAILABLE", error.getCode());
    }
}
