package com.cgcpms.project;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.dto.ProjectCommencementSaveRequest;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.ProjectCommencement;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.ProjectCommencementMapper;
import com.cgcpms.project.service.ProjectCommencementService;
import com.cgcpms.project.service.ProjectLifecycleService;
import com.cgcpms.project.vo.ProjectActivationReadinessVO;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCommencementServiceUnitTest {
    @Mock ProjectCommencementMapper commencementMapper;
    @Mock PmProjectMapper projectMapper;
    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock ProjectLifecycleService lifecycleService;
    @Mock WorkflowEngine workflowEngine;
    @InjectMocks ProjectCommencementService service;

    @BeforeEach
    void setUp() {
        initTableInfo(PmProject.class);
        initTableInfo(ProjectCommencement.class);
        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", 7L).add("roleCodes", List.of("ADMIN")).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void saveUpdatesDraftAndNormalizesRemark() {
        PmProject project = readyProject();
        ProjectCommencement row = commencement("DRAFT", 2);
        when(projectMapper.selectOne(any())).thenReturn(project);
        when(commencementMapper.selectOne(any())).thenReturn(row);
        when(commencementMapper.update(isNull(), any())).thenReturn(1);
        when(commencementMapper.selectById(9L)).thenReturn(row);

        assertEquals(row, service.save(3L,
                new ProjectCommencementSaveRequest(2, LocalDate.now(), " OWNER_NOTICE ", " note ")));
    }

    @Test
    void saveCreatesDraftAndConvertsBlankRemarkToNull() {
        PmProject project = readyProject();
        ProjectCommencement saved = commencement("DRAFT", 0);
        when(projectMapper.selectOne(any())).thenReturn(project);
        when(commencementMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ProjectCommencement created = invocation.getArgument(0);
            created.setId(9L);
            return 1;
        }).when(commencementMapper).insert(any(ProjectCommencement.class));
        when(commencementMapper.selectById(9L)).thenReturn(saved);

        assertEquals(saved, service.save(3L,
                new ProjectCommencementSaveRequest(null, LocalDate.now(), "OWNER_NOTICE", "  ")));
    }

    @Test
    void saveRejectsInvalidDateAndDuplicateInsert() {
        PmProject project = readyProject();
        project.setPlannedEndDate(LocalDate.now());
        when(projectMapper.selectOne(any())).thenReturn(project);

        assertCode("PROJECT_COMMENCEMENT_DATE_INVALID", () -> service.save(3L,
                new ProjectCommencementSaveRequest(0, LocalDate.now().plusDays(1), "OWNER_NOTICE", null)));

        project.setPlannedEndDate(null);
        when(commencementMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate")).when(commencementMapper).insert(any(ProjectCommencement.class));
        assertCode("PROJECT_COMMENCEMENT_ALREADY_EXISTS", () -> service.save(3L,
                new ProjectCommencementSaveRequest(0, LocalDate.now(), "OWNER_NOTICE", null)));
    }

    @Test
    void saveRejectsStaleAndNonEditableRows() {
        when(projectMapper.selectOne(any())).thenReturn(readyProject());
        when(commencementMapper.selectOne(any()))
                .thenReturn(null, commencement("APPROVING", 1), commencement("DRAFT", 2), commencement("DRAFT", 2));

        assertCode("PROJECT_COMMENCEMENT_VERSION_CONFLICT", () -> service.save(3L,
                new ProjectCommencementSaveRequest(1, LocalDate.now(), "OWNER_NOTICE", null)));
        assertCode("PROJECT_COMMENCEMENT_NOT_EDITABLE", () -> service.save(3L,
                new ProjectCommencementSaveRequest(1, LocalDate.now(), "OWNER_NOTICE", null)));
        assertCode("PROJECT_COMMENCEMENT_VERSION_REQUIRED", () -> service.save(3L,
                new ProjectCommencementSaveRequest(null, LocalDate.now(), "OWNER_NOTICE", null)));
        when(commencementMapper.update(isNull(), any())).thenReturn(0);
        assertCode("PROJECT_COMMENCEMENT_VERSION_CONFLICT", () -> service.save(3L,
                new ProjectCommencementSaveRequest(2, LocalDate.now(), "OWNER_NOTICE", null)));
    }

    @Test
    void submitUsesNewWorkflowAndRejectsRemainingBlockers() {
        PmProject project = readyProject();
        ProjectCommencement row = commencement("DRAFT", 2);
        WfInstance instance = new WfInstance();
        instance.setId(20L);
        when(projectMapper.selectOne(any())).thenReturn(project);
        when(commencementMapper.selectOne(any())).thenReturn(row);
        when(lifecycleService.getActivationReadiness(3L))
                .thenReturn(readiness(false, List.of("PROJECT_COMMENCEMENT_NOT_APPROVED")));
        when(workflowEngine.submit(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(instance);
        when(commencementMapper.update(isNull(), any())).thenReturn(1);
        when(commencementMapper.selectById(9L)).thenReturn(row);

        assertEquals(row, service.submit(3L, 2));
        verify(workflowEngine).submit(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), isNull(), isNull(), isNull());

        when(lifecycleService.getActivationReadiness(3L))
                .thenReturn(readiness(false, List.of("OWNER_CONTRACT_REQUIRED")));
        assertCode("PROJECT_COMMENCEMENT_GATE_REQUIRED", () -> service.submit(3L, 2));
    }

    @Test
    void submitResubmitsRejectedWorkflowAndDetectsConcurrentUpdate() {
        ProjectCommencement row = commencement("REJECTED", 2);
        row.setApprovalInstanceId(19L);
        WfInstance instance = new WfInstance();
        instance.setId(20L);
        when(projectMapper.selectOne(any())).thenReturn(readyProject());
        when(commencementMapper.selectOne(any())).thenReturn(row);
        when(lifecycleService.getActivationReadiness(3L))
                .thenReturn(readiness(true, List.of()));
        when(workflowEngine.resubmit(19L, 1L, "admin")).thenReturn(instance);
        when(commencementMapper.update(isNull(), any())).thenReturn(0);

        assertCode("PROJECT_COMMENCEMENT_VERSION_CONFLICT", () -> service.submit(3L, 2));
        verify(workflowEngine).resubmit(19L, 1L, "admin");
    }

    @Test
    void submitRequiresEditableExistingRowAndReadyProject() {
        when(projectMapper.selectOne(any())).thenReturn(readyProject());
        when(commencementMapper.selectOne(any())).thenReturn(null, commencement("APPROVED", 1));
        assertCode("PROJECT_COMMENCEMENT_REQUIRED", () -> service.submit(3L, 0));
        assertCode("PROJECT_COMMENCEMENT_ALREADY_SUBMITTED", () -> service.submit(3L, 1));

        PmProject project = readyProject();
        project.setStatus("ACTIVE");
        when(projectMapper.selectOne(any())).thenReturn(project);
        assertCode("PROJECT_COMMENCEMENT_PROJECT_NOT_READY", () -> service.submit(3L, 0));
    }

    private PmProject readyProject() {
        PmProject project = new PmProject();
        project.setId(3L);
        project.setTenantId(7L);
        project.setProjectName("项目");
        project.setStatus("PREPARING");
        project.setApprovalStatus("APPROVED");
        project.setInitiationBasis("DIRECT_APPROVAL");
        return project;
    }

    private ProjectCommencement commencement(String status, int version) {
        ProjectCommencement row = new ProjectCommencement();
        row.setId(9L);
        row.setTenantId(7L);
        row.setProjectId(3L);
        row.setApprovalStatus(status);
        row.setVersion(version);
        return row;
    }

    private ProjectActivationReadinessVO readiness(boolean ready, List<String> blockers) {
        return new ProjectActivationReadinessVO("3", "DIRECT_APPROVAL", "4", "MAIN", null,
                "5", "6", "7", "9", "DRAFT", ready, blockers);
    }

    private void assertCode(String code, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(code, assertThrows(BusinessException.class, executable).getCode());
    }

    private static void initTableInfo(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) != null) return;
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(type.getName());
        TableInfoHelper.initTableInfo(assistant, type);
    }
}
