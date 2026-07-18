package com.cgcpms.cost.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostSubjectResolverTest {

    @Mock private CostSubjectMapper costSubjectMapper;
    @InjectMocks private CostSubjectResolver resolver;

    @Test
    void missingExactTypeMustRemainUnclassifiedWithoutFallback() {
        when(costSubjectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertNull(resolver.resolveDefaultSubjectId(0L, "不存在类型"));
        assertNull(resolver.resolveForChange(0L));

        verify(costSubjectMapper, never()).selectCount(any());
    }

    @Test
    void exactEnabledTypeMustReturnMatchedSubject() {
        CostSubject subject = new CostSubject();
        subject.setId(900001L);
        when(costSubjectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(subject));
        when(costSubjectMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertEquals(900001L, resolver.resolveDefaultSubjectId(0L, "材料"));
    }

    @Test
    void parentSubjectMustNotBeSelectedForPosting() {
        CostSubject parent = new CostSubject();
        parent.setId(900002L);
        when(costSubjectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(parent));
        when(costSubjectMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertNull(resolver.resolveDefaultSubjectId(0L, "材料"));
    }
}
