package com.cgcpms.subcontract;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.List;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("SubTaskController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubTaskControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private Long taskId;
    private Long predecessorTaskId;
    private Long dependentTaskId;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
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
        String body = "{\"projectId\":10001,\"contractId\":30001,\"taskName\":\"测试分包任务\","
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
                        .content("{\"projectId\":10001,\"taskName\":\"前置任务\",\"plannedStartDate\":\"2026-07-01\",\"plannedEndDate\":\"2026-07-10\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        predecessorTaskId = Long.parseLong(predecessorResponse.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));

        String dependentResponse = mockMvc.perform(p("/sub-tasks").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"taskName\":\"后续任务\",\"predecessorTaskId\":" + predecessorTaskId
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
                        .param("projectId", "10001").param("taskName", "后续任务"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].predecessorTaskName").value("前置任务"));
    }

    @Test @Order(5) @DisplayName("POST and PUT /sub-tasks reject invalid FS dates and cycles")
    void testRejectsInvalidFsDatesAndCycles() throws Exception {
        mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"taskName\":\"错误FS任务\",\"predecessorTaskId\":" + predecessorTaskId
                                + ",\"plannedStartDate\":\"2026-07-09\",\"plannedEndDate\":\"2026-07-20\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(u("/sub-tasks/" + predecessorTaskId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"taskName\":\"前置任务\",\"predecessorTaskId\":" + dependentTaskId
                                + ",\"plannedStartDate\":\"2026-07-01\",\"plannedEndDate\":\"2026-07-10\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(d("/sub-tasks/" + predecessorTaskId).cookie(adminCookie()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"taskName\":\"后续任务\",\"predecessorTaskId\":null,\"plannedStartDate\":\"2026-07-10\",\"plannedEndDate\":\"2026-07-20\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(g("/sub-tasks/" + dependentTaskId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.predecessorTaskId").doesNotExist());
        mockMvc.perform(u("/sub-tasks/" + dependentTaskId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"taskName\":\"后续任务\",\"predecessorTaskId\":" + predecessorTaskId
                                + ",\"plannedStartDate\":\"2026-07-10\",\"plannedEndDate\":\"2026-07-20\",\"progressPercent\":0,\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk());
    }

    @Test @Order(6) @DisplayName("POST /sub-tasks missing required -> 400")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(7) @DisplayName("POST /sub-tasks rejects inconsistent schedule data without persisting")
    void testCreate_InvalidScheduleData() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String[] invalidBodies = {
                "{\"projectId\":10001,\"taskName\":\"invalid-progress-%s\",\"progressPercent\":101,\"status\":\"IN_PROGRESS\"}".formatted(suffix),
                "{\"projectId\":10001,\"taskName\":\"invalid-plan-dates-%s\",\"plannedStartDate\":\"2026-07-02\",\"plannedEndDate\":\"2026-07-01\"}".formatted(suffix),
                "{\"projectId\":10001,\"taskName\":\"invalid-actual-dates-%s\",\"actualStartDate\":\"2026-07-02\",\"actualEndDate\":\"2026-07-01\",\"progressPercent\":100,\"status\":\"COMPLETED\"}".formatted(suffix),
                "{\"projectId\":10001,\"taskName\":\"invalid-completed-%s\",\"actualStartDate\":\"2026-07-01\",\"progressPercent\":99,\"status\":\"COMPLETED\"}".formatted(suffix)
        };
        for (String body : invalidBodies) {
            mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(g("/sub-tasks").cookie(adminCookie()).param("taskName", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isEmpty());

        mockMvc.perform(u("/sub-tasks/" + taskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"taskName\":\"invalid-update\",\"progressPercent\":100,\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(g("/sub-tasks/" + taskId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value("35.50"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

    }

    @Test @Order(8) @DisplayName("PUT /sub-tasks/{id} rejects blank status without persisting")
    void testUpdate_BlankStatus() throws Exception {
        String createResponse = mockMvc.perform(p("/sub-tasks").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"taskName\":\"blank-status-test\",\"progressPercent\":20,\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long id = Long.parseLong(createResponse.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));

        mockMvc.perform(u("/sub-tasks/" + id).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"taskName\":\"blank-status-update\",\"status\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(g("/sub-tasks/" + id).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        mockMvc.perform(d("/sub-tasks/" + id).cookie(adminCookie())).andExpect(status().isOk());
    }

    @Test @Order(9) @DisplayName("GET /sub-tasks/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(taskId);
        mockMvc.perform(g("/sub-tasks/" + taskId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.taskCode", startsWith("SUB-")))
                .andExpect(jsonPath("$.data.workArea").value("1.1 地基施工"))
                .andExpect(jsonPath("$.data.plannedStartDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.plannedEndDate").value("2026-07-15"))
                .andExpect(jsonPath("$.data.actualStartDate").value("2026-07-02"))
                .andExpect(jsonPath("$.data.progressPercent").value("35.50"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test @Order(10) @DisplayName("GET /sub-tasks filters project schedule rows for gantt")
    void testListScheduleRowsByProject() throws Exception {
        Assertions.assertNotNull(taskId);
        mockMvc.perform(g("/sub-tasks").cookie(adminCookie())
                        .param("projectId", "10001")
                        .param("taskName", "测试分包任务")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].projectId").value("10001"))
                .andExpect(jsonPath("$.data.records[0].taskCode", startsWith("SUB-")))
                .andExpect(jsonPath("$.data.records[0].plannedStartDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.records[0].plannedEndDate").value("2026-07-15"))
                .andExpect(jsonPath("$.data.records[0].progressPercent").value("35.50"));
    }

    @Test @Order(11) @DisplayName("PUT /sub-tasks/{id} -> 200")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(taskId);
        String body = "{\"projectId\":10001,\"contractId\":30001,\"taskCode\":\"ST-UPD-" + System.nanoTime()
                + "\",\"taskName\":\"更新分包任务\",\"actualEndDate\":\"2026-07-14\",\"progressPercent\":100,\"status\":\"COMPLETED\"}";
        mockMvc.perform(u("/sub-tasks/" + taskId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(12) @DisplayName("DELETE /sub-tasks/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(taskId);
        mockMvc.perform(d("/sub-tasks/" + dependentTaskId).cookie(adminCookie())).andExpect(status().isOk());
        mockMvc.perform(d("/sub-tasks/" + predecessorTaskId).cookie(adminCookie())).andExpect(status().isOk());
        mockMvc.perform(d("/sub-tasks/" + taskId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
