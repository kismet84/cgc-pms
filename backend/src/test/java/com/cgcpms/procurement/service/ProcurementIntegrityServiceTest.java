package com.cgcpms.procurement.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementIntegrityServiceTest {

    private static final long TENANT_ID = 3L;

    @Mock PmProjectMapper projectMapper;
    @Mock ProjectBudgetMapper budgetMapper;
    @Mock ProjectBudgetLineMapper budgetLineMapper;
    @Mock SubTaskMapper subTaskMapper;
    @Mock SysFileMapper fileMapper;
    @InjectMocks ProcurementIntegrityService service;

    @BeforeEach
    void setUpContext() {
        UserContext.set(Jwts.claims().add("userId", 1L).add("tenantId", TENANT_ID).build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void rejectsPausedProject() {
        PmProject project = new PmProject();
        project.setTenantId(TENANT_ID);
        project.setStatus("SUSPENDED");
        when(projectMapper.selectById(10L)).thenReturn(project);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireActiveProject(10L, "提交采购申请"));

        assertEquals("PROJECT_NOT_ACTIVE", error.getCode());
    }

    @Test
    void acceptsOnlyCurrentActiveBudgetLineForProject() {
        ProjectBudgetLine line = new ProjectBudgetLine();
        line.setId(20L);
        line.setTenantId(TENANT_ID);
        line.setProjectId(10L);
        line.setBudgetId(30L);
        ProjectBudget budget = new ProjectBudget();
        budget.setId(30L);
        budget.setTenantId(TENANT_ID);
        budget.setProjectId(10L);
        budget.setStatus("ACTIVE");
        budget.setActiveFlag(1);
        when(budgetLineMapper.selectById(20L)).thenReturn(line);
        when(budgetMapper.selectById(30L)).thenReturn(budget);

        assertSame(line, service.requireActiveBudgetLine(10L, 20L));
    }

    @Test
    void rejectsMissingCleanAttachment() {
        when(fileMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireCleanAttachment("PURCHASE_ORDER", 40L));

        assertEquals("PROCUREMENT_ATTACHMENT_REQUIRED", error.getCode());
    }
}
