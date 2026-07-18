package com.cgcpms.cost.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostSubjectReferenceProtectionTest {

    private static final long SUBJECT_ID = 101L;
    private static final long TENANT_ID = 0L;

    @Mock private CostSubjectMapper costSubjectMapper;
    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private CostSubjectService service;

    @BeforeEach
    void setUp() {
        UserContext.restore(new UserContext.Snapshot(1L, "tester", TENANT_ID, List.of("ADMIN")));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void disablingReferencedSubjectMustBeRejected() {
        CostSubject subject = subject("ENABLE");
        when(costSubjectMapper.selectById(SUBJECT_ID)).thenReturn(subject);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("pay_application") ? 2L : 0L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.toggleStatus(SUBJECT_ID));

        assertEquals("COST_SUBJECT_REFERENCED", exception.getCode());
        verify(costSubjectMapper, never()).updateById(subject);
    }

    @Test
    void deletingReferencedSubjectMustBeRejected() {
        CostSubject subject = subject("ENABLE");
        when(costSubjectMapper.selectById(SUBJECT_ID)).thenReturn(subject);
        when(costSubjectMapper.selectCount(any())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("stl_settlement_item") ? 1L : 0L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.delete(SUBJECT_ID));

        assertEquals("COST_SUBJECT_REFERENCED", exception.getCode());
        verify(costSubjectMapper, never()).deleteById(SUBJECT_ID);
    }

    private CostSubject subject(String status) {
        CostSubject subject = new CostSubject();
        subject.setId(SUBJECT_ID);
        subject.setTenantId(TENANT_ID);
        subject.setStatus(status);
        return subject;
    }
}
