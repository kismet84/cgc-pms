package com.cgcpms.project.handler;

import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("ProjectApprovalWorkflowHandler")
class ProjectApprovalWorkflowHandlerTest {

    @Autowired
    private ProjectApprovalWorkflowHandler handler;

    @Autowired
    private PmProjectMapper projectMapper;

    @Test
    @Transactional
    @DisplayName("重新提交将驳回项目恢复为审批中并可完成审批")
    void resubmittedProjectCanBeApproved() {
        PmProject project = project("REJECTED");
        projectMapper.insert(project);
        WorkflowContext context = context(project.getId(), 2);

        handler.beforeSubmit(context);
        assertEquals("APPROVING", projectMapper.selectById(project.getId()).getApprovalStatus());

        context.getInstance().setInstanceStatus("APPROVED");
        handler.onApproved(context);
        assertEquals("APPROVED", projectMapper.selectById(project.getId()).getApprovalStatus());
        assertEquals("PREPARING", projectMapper.selectById(project.getId()).getStatus());
        assertEquals("DIRECT_APPROVAL", projectMapper.selectById(project.getId()).getInitiationBasis());
    }

    @Test
    @Transactional
    @DisplayName("修复上线前已运行的重提实例可从驳回态完成审批")
    void inFlightResubmissionCanRecover() {
        PmProject project = project("REJECTED");
        projectMapper.insert(project);
        WorkflowContext context = context(project.getId(), 2);
        context.getInstance().setInstanceStatus("APPROVED");

        handler.onApproved(context);

        assertEquals("APPROVED", projectMapper.selectById(project.getId()).getApprovalStatus());
        assertEquals("PREPARING", projectMapper.selectById(project.getId()).getStatus());
    }

    private PmProject project(String approvalStatus) {
        PmProject project = new PmProject();
        project.setTenantId(0L);
        project.setProjectCode("PROJECT-RESUBMIT-" + System.nanoTime());
        project.setProjectName("项目审批重提测试");
        project.setProjectType("CONSTRUCTION");
        project.setStatus("DRAFT");
        project.setApprovalStatus(approvalStatus);
        project.setContractAmount(new BigDecimal("1000000.00"));
        project.setTargetCost(new BigDecimal("800000.00"));
        return project;
    }

    private WorkflowContext context(Long projectId, int round) {
        WfInstance instance = new WfInstance();
        instance.setBusinessId(projectId);
        instance.setTenantId(0L);
        instance.setCurrentRound(round);
        instance.setInstanceStatus("RUNNING");
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);
        return context;
    }
}
