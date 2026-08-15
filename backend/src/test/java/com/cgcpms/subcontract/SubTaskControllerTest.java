package com.cgcpms.subcontract;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.common.JwtHttpTestTokenFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.List;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("SubTaskController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubTaskControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtHttpTestTokenFactory tokenFactory;
    @Autowired private JdbcTemplate jdbc;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 99187001L, CROSS_PROJECT_ID = 99187002L;
    private static final long SCHEDULE_ID = 99187101L, CROSS_SCHEDULE_ID = 99187102L;
    private static final long WBS_ID = 99187201L, CROSS_WBS_ID = 99187202L;
    private Long taskId;
    private Long predecessorTaskId;
    private Long dependentTaskId;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                tokenFactory.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @BeforeAll
    void setUpExecutionFixtures() {
        projectFixture(PROJECT_ID, SCHEDULE_ID, WBS_ID, "SUBTASK-HTTP", "SUBTASK-WBS");
        projectFixture(CROSS_PROJECT_ID, CROSS_SCHEDULE_ID, CROSS_WBS_ID, "SUBTASK-CROSS", "SUBTASK-CROSS-WBS");
    }

    @AfterAll
    void cleanUpExecutionFixtures() {
        jdbc.update("UPDATE sub_task SET predecessor_task_id=NULL WHERE project_id IN (?,?)", PROJECT_ID, CROSS_PROJECT_ID);
        jdbc.update("DELETE FROM sub_task WHERE project_id IN (?,?)", PROJECT_ID, CROSS_PROJECT_ID);
        jdbc.update("DELETE FROM project_wbs_task WHERE id IN (?,?)", WBS_ID, CROSS_WBS_ID);
        jdbc.update("DELETE FROM project_schedule_plan WHERE id IN (?,?)", SCHEDULE_ID, CROSS_SCHEDULE_ID);
        jdbc.update("DELETE FROM pm_project WHERE id IN (?,?)", PROJECT_ID, CROSS_PROJECT_ID);
    }

    private void projectFixture(long projectId, long scheduleId, long wbsId, String code, String wbsCode) {
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'分包任务HTTP测试项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", projectId, code);
        jdbc.update("INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,'BASELINE',1,'2026-01-01','2026-12-31','ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", scheduleId, projectId, code + "-SP", code + "基线");
        jdbc.update("INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,task_code,task_name,planned_start_date,planned_end_date,weight_percent,actual_quantity,actual_progress,status,sort_order,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,'分包任务WBS','2026-01-01','2026-12-31',100,0,0,'NOT_STARTED',1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", wbsId, projectId, scheduleId, wbsCode);
    }

    @Test @Order(1) @DisplayName("GET /sub-tasks without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/sub-tasks")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /sub-tasks -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/sub-tasks").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("POST /sub-tasks -> 200 creates task")
    void testCreate() throws Exception {
        String body = "{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"测试分包任务\","
                + "\"workArea\":\"1.1 地基施工\",\"plannedStartDate\":\"2026-07-01\","
                + "\"plannedEndDate\":\"2026-07-15\",\"actualStartDate\":\"2026-07-02\","
                + "\"progressPercent\":35.50,\"status\":\"IN_PROGRESS\"}";
        String resp = mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        taskId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(taskId);
    }

    @Test @Order(4) @DisplayName("POST /sub-tasks creates a same-project FS dependency")
    void testCreateFsDependency() throws Exception {
        String predecessorResponse = mockMvc.perform(p("/sub-tasks").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"前置任务\",\"plannedStartDate\":\"2026-07-01\",\"plannedEndDate\":\"2026-07-10\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        predecessorTaskId = Long.parseLong(predecessorResponse.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));

        String dependentResponse = mockMvc.perform(p("/sub-tasks").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"predecessorTaskId\":" + predecessorTaskId
                                + ",\"plannedStartDate\":\"2026-07-10\",\"plannedEndDate\":\"2026-07-20\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        dependentTaskId = Long.parseLong(dependentResponse.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));

        mockMvc.perform(g("/sub-tasks/" + dependentTaskId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.predecessorTaskId").value(predecessorTaskId.toString()))
                .andExpect(jsonPath("$.data.predecessorTaskName").value("前置任务"))
                .andExpect(jsonPath("$.data.predecessorStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.predecessorPlannedEndDate").value("2026-07-10"));
        mockMvc.perform(g("/sub-tasks").cookie(adminCookie())
                        .param("projectId", String.valueOf(PROJECT_ID)).param("taskName", "后续任务"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].predecessorTaskName").value("前置任务"));
    }

    @Test @Order(5) @DisplayName("POST and PUT /sub-tasks reject invalid FS dates and cycles")
    void testRejectsInvalidFsDatesAndCycles() throws Exception {
        mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"错误FS任务\",\"predecessorTaskId\":" + predecessorTaskId
                                + ",\"plannedStartDate\":\"2026-07-09\",\"plannedEndDate\":\"2026-07-20\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(u("/sub-tasks/" + predecessorTaskId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"前置任务\",\"predecessorTaskId\":" + dependentTaskId
                                + ",\"plannedStartDate\":\"2026-07-01\",\"plannedEndDate\":\"2026-07-10\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(d("/sub-tasks/" + predecessorTaskId).cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUB_TASK_DEPENDENCY_IN_USE"));
        mockMvc.perform(g("/sub-tasks/" + predecessorTaskId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskCode", startsWith("SUB-")));
        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"predecessorTaskId\":null,\"plannedStartDate\":\"2026-07-10\",\"plannedEndDate\":\"2026-07-20\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(g("/sub-tasks/" + dependentTaskId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.predecessorTaskId").doesNotExist());
        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"predecessorTaskId\":" + predecessorTaskId
                                + ",\"plannedStartDate\":\"2026-07-10\",\"plannedEndDate\":\"2026-07-20\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk());
    }

    @Test @Order(6) @DisplayName("POST and PUT enforce predecessor completion gate")
    void testPredecessorCompletionGate() throws Exception {
        mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"未完成前置创建门禁\",\"predecessorTaskId\":" + predecessorTaskId
                                + ",\"plannedStartDate\":\"2026-07-10\",\"actualStartDate\":\"2026-07-10\",\"progressPercent\":10,\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUB_TASK_PREDECESSOR_NOT_COMPLETED"));

        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"actualStartDate\":\"2026-07-10\",\"progressPercent\":10,\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUB_TASK_PREDECESSOR_NOT_COMPLETED"));

        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"actualStartDate\":\"2026-07-10\",\"actualEndDate\":\"2026-07-11\",\"progressPercent\":100,\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUB_TASK_PREDECESSOR_NOT_COMPLETED"));

        mockMvc.perform(u("/sub-tasks/" + predecessorTaskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"前置任务\",\"actualStartDate\":\"2026-07-01\",\"actualEndDate\":\"2026-07-10\",\"progressPercent\":100,\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"actualStartDate\":\"2026-07-10\",\"progressPercent\":10,\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"predecessorTaskId\":" + taskId
                                + ",\"plannedStartDate\":\"2026-07-15\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUB_TASK_PREDECESSOR_NOT_COMPLETED"));
        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"predecessorTaskId\":null}"))
                .andExpect(status().isOk());
        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"predecessorTaskId\":" + predecessorTaskId + "}"))
                .andExpect(status().isOk());

        String crossProjectResponse = mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + CROSS_PROJECT_ID + ",\"wbsTaskId\":" + CROSS_WBS_ID + ",\"taskName\":\"跨项目前置\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long crossProjectPredecessorId = Long.parseLong(crossProjectResponse.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"后续任务\",\"predecessorTaskId\":" + crossProjectPredecessorId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUB_TASK_DEPENDENCY_INVALID"));
    }

    @Test @Order(7) @DisplayName("POST /sub-tasks missing required -> 400")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(8) @DisplayName("POST /sub-tasks rejects inconsistent schedule data without persisting")
    void testCreate_InvalidScheduleData() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String[] invalidBodies = {
                "{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"invalid-progress-%s\",\"progressPercent\":101,\"status\":\"IN_PROGRESS\"}".formatted(suffix),
                "{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"invalid-plan-dates-%s\",\"plannedStartDate\":\"2026-07-02\",\"plannedEndDate\":\"2026-07-01\"}".formatted(suffix),
                "{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"invalid-actual-dates-%s\",\"actualStartDate\":\"2026-07-02\",\"actualEndDate\":\"2026-07-01\",\"progressPercent\":100,\"status\":\"COMPLETED\"}".formatted(suffix),
                "{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"invalid-completed-%s\",\"actualStartDate\":\"2026-07-01\",\"progressPercent\":99,\"status\":\"COMPLETED\"}".formatted(suffix)
        };
        for (String body : invalidBodies) {
            mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(g("/sub-tasks").cookie(adminCookie()).param("taskName", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isEmpty());

        mockMvc.perform(u("/sub-tasks/" + taskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"invalid-update\",\"progressPercent\":100,\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(g("/sub-tasks/" + taskId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value("35.50"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

    }

    @Test @Order(9) @DisplayName("PUT /sub-tasks/{id} rejects blank status without persisting")
    void testUpdate_BlankStatus() throws Exception {
        String createResponse = mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"blank-status-test\",\"progressPercent\":20,\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long id = Long.parseLong(createResponse.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));

        mockMvc.perform(u("/sub-tasks/" + id).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"blank-status-update\",\"status\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(g("/sub-tasks/" + id).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        mockMvc.perform(d("/sub-tasks/" + id).cookie(adminCookie())).andExpect(status().isOk());
    }

    @Test @Order(10) @DisplayName("GET /sub-tasks/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(taskId);
        mockMvc.perform(g("/sub-tasks/" + taskId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.taskCode", startsWith("SUB-")))
                .andExpect(jsonPath("$.data.wbsTaskId").value(String.valueOf(WBS_ID)))
                .andExpect(jsonPath("$.data.workArea").value("1.1 地基施工"))
                .andExpect(jsonPath("$.data.plannedStartDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.plannedEndDate").value("2026-07-15"))
                .andExpect(jsonPath("$.data.actualStartDate").value("2026-07-02"))
                .andExpect(jsonPath("$.data.progressPercent").value("35.50"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test @Order(11) @DisplayName("GET /sub-tasks filters project schedule rows for gantt")
    void testListScheduleRowsByProject() throws Exception {
        Assertions.assertNotNull(taskId);
        mockMvc.perform(g("/sub-tasks").cookie(adminCookie())
                        .param("projectId", String.valueOf(PROJECT_ID))
                        .param("taskName", "测试分包任务")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].projectId").value(String.valueOf(PROJECT_ID)))
                .andExpect(jsonPath("$.data.records[0].taskCode", startsWith("SUB-")))
                .andExpect(jsonPath("$.data.records[0].plannedStartDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.records[0].plannedEndDate").value("2026-07-15"))
                .andExpect(jsonPath("$.data.records[0].progressPercent").value("35.50"));
    }

    @Test @Order(12) @DisplayName("PUT /sub-tasks/{id} -> 200")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(taskId);
        String body = "{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskCode\":\"ST-UPD-" + System.nanoTime()
                + "\",\"taskName\":\"更新分包任务\",\"actualEndDate\":\"2026-07-14\",\"progressPercent\":100,\"status\":\"COMPLETED\"}";
        mockMvc.perform(u("/sub-tasks/" + taskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(13) @DisplayName("DELETE permits reusing and deleting the same daily task code")
    void testDeleteReusedDailyTaskCode() throws Exception {
        String firstResponse = mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"软删除编号复用-1\",\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long firstId = Long.parseLong(firstResponse.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        String firstCode = mockMvc.perform(g("/sub-tasks/" + firstId).cookie(adminCookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"taskCode\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(d("/sub-tasks/" + firstId).cookie(adminCookie())).andExpect(status().isOk());

        String secondResponse = mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"wbsTaskId\":" + WBS_ID + ",\"taskName\":\"软删除编号复用-2\",\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long secondId = Long.parseLong(secondResponse.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        mockMvc.perform(g("/sub-tasks/" + secondId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskCode").value(firstCode));
        mockMvc.perform(d("/sub-tasks/" + secondId).cookie(adminCookie())).andExpect(status().isOk());
    }

    @Test @Order(14) @DisplayName("DELETE /sub-tasks/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(taskId);
        mockMvc.perform(d("/sub-tasks/" + dependentTaskId).cookie(adminCookie())).andExpect(status().isOk());
        mockMvc.perform(d("/sub-tasks/" + predecessorTaskId).cookie(adminCookie())).andExpect(status().isOk());
        mockMvc.perform(d("/sub-tasks/" + taskId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(15) @DisplayName("GET /sub-tasks/form-options returns only active project WBS tasks")
    void testFormOptions() throws Exception {
        mockMvc.perform(g("/sub-tasks/form-options").cookie(adminCookie())
                        .param("projectId", String.valueOf(PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.wbsTasks", hasSize(1)))
                .andExpect(jsonPath("$.data.wbsTasks[0].id").value(String.valueOf(WBS_ID)))
                .andExpect(jsonPath("$.data.wbsTasks[0].taskCode").value("SUBTASK-WBS"))
                .andExpect(jsonPath("$.data.wbsTasks[0].taskName").value("分包任务WBS"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
