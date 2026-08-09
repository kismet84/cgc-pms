package com.cgcpms.bid;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.bid.service.BidAwardProjectCreator.BidAwardProjectCommand;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.service.BidAwardProjectService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidAwardProjectServiceTest {

    @Mock PmProjectMapper projectMapper;

    private BidAwardProjectService service;

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(PmProject.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("BidAwardProjectServiceTest");
            TableInfoHelper.initTableInfo(assistant, PmProject.class);
        }
        service = new BidAwardProjectService(projectMapper, new CodeGenerationService());
    }

    @Test
    void rejectsZeroContractAmountInsteadOfDefaultingProjectValue() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.createOrGet(command(
                BigDecimal.ZERO, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31))));

        assertEquals("BID_AWARD_PROJECT_INVALID", error.getCode());
        verify(projectMapper, never()).insert(any(PmProject.class));
    }

    @Test
    void mapsRequiredAwardFieldsWithoutDefaults() {
        when(projectMapper.selectLastCodeByPrefix(any(), any())).thenReturn(null);
        doAnswer(invocation -> {
            PmProject project = invocation.getArgument(0);
            project.setId(99L);
            return 1;
        }).when(projectMapper).insert(any(PmProject.class));

        assertEquals(99L, service.createOrGet(command(new BigDecimal("123.45"),
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31))));

        ArgumentCaptor<PmProject> project = ArgumentCaptor.forClass(PmProject.class);
        verify(projectMapper).insert(project.capture());
        assertEquals("招标人", project.getValue().getOwnerUnit());
        assertEquals("建设地点", project.getValue().getProjectAddress());
        assertEquals(0, BigDecimal.ZERO.compareTo(project.getValue().getContractAmount()));
        assertEquals("BID_AWARD", project.getValue().getInitiationBasis());
        assertEquals(LocalDate.of(2026, 8, 1), project.getValue().getPlannedStartDate());
        assertEquals(LocalDate.of(2027, 7, 31), project.getValue().getPlannedEndDate());
    }

    private BidAwardProjectCommand command(BigDecimal amount, LocalDate start, LocalDate end) {
        return new BidAwardProjectCommand(0L, 1L, "BID-1", "工程", "招标人", "建设地点",
                amount, start, end);
    }
}
