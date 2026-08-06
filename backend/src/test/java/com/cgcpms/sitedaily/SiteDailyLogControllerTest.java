package com.cgcpms.sitedaily;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "jwt.secret=site-daily-log-controller-test-secret-key-at-least-sixty-four-characters-long"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SiteDailyLogControllerTest {
    private static final long PROJECT_ID = 99188001L;
    private static final long SCHEDULE_ID = 99188101L;
    private static final long WBS_ID = 99188201L;
    private static final long WEEKLY_ID = 99188301L;
    private static final long WEEKLY_ITEM_ID = 99188401L;
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtils jwtUtils;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    void setUpExecutionFixtures() {
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'SITE-DAILY-HTTP','现场日报HTTP测试项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT_ID);
        jdbc.update("INSERT INTO project_schedule_plan(id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,planned_start_date,planned_end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'SITE-DAILY-SP','现场日报测试基线','BASELINE',1,'2099-01-01','2099-12-31','ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", SCHEDULE_ID, PROJECT_ID);
        jdbc.update("INSERT INTO project_wbs_task(id,tenant_id,project_id,schedule_plan_id,task_code,task_name,planned_start_date,planned_end_date,weight_percent,planned_quantity,actual_quantity,actual_progress,status,sort_order,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,'SITE-DAILY-WBS','现场日报测试WBS','2099-01-01','2099-12-31',100,100,0,0,'NOT_STARTED',1,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", WBS_ID, PROJECT_ID, SCHEDULE_ID);
        jdbc.update("INSERT INTO project_period_plan(id,tenant_id,project_id,schedule_plan_id,period_type,period_code,period_name,start_date,end_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,'WEEKLY','SITE-DAILY-W01','现场日报测试周计划','2099-01-01','2099-01-07','APPROVED',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", WEEKLY_ID, PROJECT_ID, SCHEDULE_ID);
        jdbc.update("INSERT INTO project_period_plan_item(id,tenant_id,period_plan_id,wbs_task_id,target_progress,planned_quantity,created_by,created_at,updated_by,updated_at) VALUES(?,0,?,?,20,20,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)", WEEKLY_ITEM_ID, WEEKLY_ID, WBS_ID);
    }

    @AfterAll
    void cleanUpExecutionFixtures() {
        jdbc.update("DELETE FROM alert_log WHERE project_id=? AND source_type='PROJECT_PROGRESS_SNAPSHOT'", PROJECT_ID);
        jdbc.update("DELETE FROM project_progress_snapshot WHERE project_id=?", PROJECT_ID);
        jdbc.update("DELETE FROM site_daily_progress WHERE project_id=?", PROJECT_ID);
        jdbc.update("DELETE FROM site_daily_log WHERE project_id=?", PROJECT_ID);
        jdbc.update("DELETE FROM project_period_plan_item WHERE period_plan_id=?", WEEKLY_ID);
        jdbc.update("DELETE FROM project_period_plan WHERE id=?", WEEKLY_ID);
        jdbc.update("DELETE FROM project_wbs_task WHERE id=?", WBS_ID);
        jdbc.update("DELETE FROM project_schedule_plan WHERE id=?", SCHEDULE_ID);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT_ID);
    }

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(1L, "admin", 0L, List.of("ADMIN"), List.of()));
    }

    private Cookie permissionCookie(String... permissions) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(1L, "site-reader", 0L, List.of(), List.of(permissions)));
    }

    @Test
    void qualitySafetyFactsRequireSiteAndQualityPermissionsTogether() throws Exception {
        String path = "/api/site-daily-logs/999999999/quality-safety";
        mockMvc.perform(get(path).contextPath("/api").cookie(permissionCookie("site:daily:query")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path).contextPath("/api").cookie(permissionCookie("quality:safety:query")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path).contextPath("/api").cookie(
                        permissionCookie("site:daily:query", "quality:safety:query")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path).contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void draftCanBeEditedAndSubmittedOnlyOnce() throws Exception {
        String body = "{\"projectId\":" + PROJECT_ID + ",\"reportDate\":\"2099-01-01\","
                + "\"constructionContent\":\"完成基础施工\",\"issuesDelays\":\"材料晚到\","
                + "\"nextDayPlan\":\"开始主体施工\",\"weatherSummary\":\"晴\",\"onSiteHeadcount\":12}";
        String response = mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1");

        mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        String detailResponse = mockMvc.perform(get("/api/site-daily-logs/" + id).contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String expectedUpdatedAt = objectMapper.readTree(detailResponse).path("data").path("updatedAt").asText();
        mockMvc.perform(put("/api/site-daily-logs/" + id).contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"reportDate\":\"2099-01-01\",\"constructionContent\":\"已更新施工内容\",\"weatherSummary\":\"晴转多云\",\"onSiteHeadcount\":0,\"expectedUpdatedAt\":\"" + expectedUpdatedAt + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/site-daily-logs/" + id).contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weatherSummary").value("晴转多云"))
                .andExpect(jsonPath("$.data.onSiteHeadcount").value(0));
        mockMvc.perform(put("/api/project-schedules/daily-logs/" + id + "/progress").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"wbsTaskId\":" + WBS_ID + ",\"currentProgress\":10,\"completedQuantity\":10,\"workDescription\":\"完成基础施工\"}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/site-daily-logs/" + id + "/submit").contextPath("/api")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/site-daily-logs/" + id + "/submit").contextPath("/api")
                        .cookie(adminCookie()).param("expectedVersion", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/site-daily-logs/" + id).contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedBy").value("1"))
                .andExpect(jsonPath("$.data.submittedAt").exists());
        mockMvc.perform(put("/api/site-daily-logs/" + id).contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"reportDate\":\"2099-01-01\",\"constructionContent\":\"禁止修改\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/site-daily-logs/" + id + "/submit").contextPath("/api")
                        .cookie(adminCookie()).param("expectedVersion", "2"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/site-daily-logs").contextPath("/api").cookie(adminCookie())
                        .param("projectId", String.valueOf(PROJECT_ID)).param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].projectName").exists());
    }

    @Test
    void weatherAndHeadcountValidationRejectsInvalidInput() throws Exception {
        String base = "{\"projectId\":" + PROJECT_ID + ",\"reportDate\":\"2099-02-01\",\"constructionContent\":\"测试\",";
        mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "\"onSiteHeadcount\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "\"onSiteHeadcount\":100001}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "\"onSiteHeadcount\":1.5}"))
                .andExpect(status().isBadRequest());
        String longWeather = "晴".repeat(201);
        mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(base + "\"weatherSummary\":\"" + longWeather + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void omittedHeadcountRemainsUnknown() throws Exception {
        String response = mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + ",\"reportDate\":\"2099-03-01\",\"constructionContent\":\"测试未填写人数\",\"weatherSummary\":\"阴\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1");

        mockMvc.perform(get("/api/site-daily-logs/" + id).contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weatherSummary").value("阴"))
                .andExpect(jsonPath("$.data.onSiteHeadcount").value(nullValue()));
    }
}
