package com.cgcpms.cost;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.cost.controller.CostControlController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CostControlControllerTest {
    private static final long PROJECT = 99189001L;
    private static final long TARGET = 99189002L;
    private static final long FORECAST = 99189003L;
    private static final long OUTSIDER = 99189004L;
    private static final long MEMBER = 99189005L;
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtils jwtUtils;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedOutsiderFixture() {
        cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) VALUES(?,0,'cost.control.outsider','x','成本控制项目外用户','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", OUTSIDER);
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) VALUES(?,0,'cost.control.member','x','成本控制项目成员','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", MEMBER);
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'COST-HTTP-SCOPE','成本控制HTTP权限项目','ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO cost_target(id,tenant_id,project_id,version_no,version_name,total_target_amount,total_bid_cost_amount,total_responsibility_amount,is_active,approval_status,effective_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,1,'HTTP基线',100,100,100,1,'APPROVED',CURRENT_DATE,'ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", TARGET, PROJECT);
        jdbc.update("INSERT INTO cost_forecast(id,tenant_id,project_id,cost_target_id,forecast_code,forecast_name,version_no,forecast_date,bid_cost_amount,target_cost_amount,responsibility_amount,committed_cost_amount,actual_cost_amount,estimated_remaining_amount,forecast_at_completion_amount,contract_income_amount,forecast_profit_amount,cost_variance_amount,profit_margin,status,formula_version,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,'FC-HTTP-SCOPE','HTTP越权预测',1,CURRENT_DATE,100,100,100,0,100,0,100,200,100,0,0.5,'CONTROLLED','COST_EAC_V1',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", FORECAST, PROJECT, TARGET);
        jdbc.update("INSERT INTO pm_project_member(id,tenant_id,project_id,user_id,role_code,status,created_at,updated_at,created_by,updated_by,deleted_flag) VALUES(99189006,0,?,?,'COST_MANAGER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1,0)", PROJECT, MEMBER);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM cost_forecast WHERE id=?", FORECAST);
        jdbc.update("DELETE FROM cost_target WHERE id=?", TARGET);
        jdbc.update("DELETE FROM pm_project_member WHERE id=99189006");
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
        jdbc.update("DELETE FROM sys_user WHERE id=?", OUTSIDER);
        jdbc.update("DELETE FROM sys_user WHERE id=?", MEMBER);
    }
    @Test
    void mutableExistingResourcesRequireClientVersionAndActionPermission() throws Exception {
        assertVersionAndPermission("updateForecast", "cost:forecast:maintain", Long.class, Integer.class,
                com.cgcpms.cost.dto.CostControlModels.ForecastRequest.class);
        assertVersionAndPermission("confirmForecast", "cost:forecast:confirm", Long.class, Integer.class);
        assertVersionAndPermission("updateCorrective", "cost:corrective:maintain", Long.class, Integer.class,
                com.cgcpms.cost.dto.CostControlModels.CorrectiveActionRequest.class);
        assertVersionAndPermission("submitCorrective", "cost:corrective:submit", Long.class, Integer.class);
        assertVersionAndPermission("closeCorrective", "cost:corrective:submit", Long.class, Integer.class,
                com.cgcpms.cost.dto.CostControlModels.CorrectiveCloseRequest.class);
    }

    @Test
    void ordinaryActionPermissionsPassSecurityButReadOnlyIsForbidden() throws Exception {
        mockMvc.perform(post("/api/cost-controls/forecasts/999999999/confirm").contextPath("/api")
                        .param("version", "0").cookie(cookie("cost:forecast:confirm")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/cost-controls/corrective-actions/999999999/submit").contextPath("/api")
                        .param("version", "0").cookie(cookie("cost:corrective:submit")))
                .andExpect(status().isBadRequest());
        Cookie readOnly = cookie("cost:control:query");
        mockMvc.perform(post("/api/cost-controls/forecasts/999999999/confirm").contextPath("/api")
                        .param("version", "0").cookie(readOnly))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cost-controls/corrective-actions/999999999/submit").contextPath("/api")
                        .param("version", "0").cookie(readOnly))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordinaryMaintainPermissionsReachBusinessLayerButReadOnlyIsForbidden() throws Exception {
        String forecastBody = """
                {"projectId":99189001,"forecastCode":"FC-PERMISSION","forecastName":"权限测试预测",
                 "forecastDate":"%s","items":[{"costSubjectId":999999999,"estimatedRemainingAmount":0}]}
                """.formatted(LocalDate.now());
        String correctiveBody = """
                {"forecastId":999999999,"actionCode":"CA-PERMISSION","actionTitle":"权限测试纠偏",
                 "rootCause":"权限测试根因","actionPlan":"权限测试措施","expectedSavingAmount":1,
                 "responsibleUserId":99189005,"dueDate":"%s"}
                """.formatted(LocalDate.now().plusDays(1));

        Cookie forecastMaintainer = memberCookie("cost:forecast:maintain");
        Cookie correctiveMaintainer = memberCookie("cost:corrective:maintain");
        mockMvc.perform(post("/api/cost-controls/forecasts").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(forecastBody).cookie(forecastMaintainer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/cost-controls/forecasts/999999999").contextPath("/api")
                        .param("version", "0").contentType(MediaType.APPLICATION_JSON).content(forecastBody).cookie(forecastMaintainer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/cost-controls/corrective-actions").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(correctiveBody).cookie(correctiveMaintainer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/cost-controls/corrective-actions/999999999").contextPath("/api")
                        .param("version", "0").contentType(MediaType.APPLICATION_JSON).content(correctiveBody).cookie(correctiveMaintainer))
                .andExpect(status().isBadRequest());

        Cookie readOnly = memberCookie("cost:control:query");
        mockMvc.perform(post("/api/cost-controls/forecasts").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(forecastBody).cookie(readOnly))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/cost-controls/forecasts/999999999").contextPath("/api")
                        .param("version", "0").contentType(MediaType.APPLICATION_JSON).content(forecastBody).cookie(readOnly))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cost-controls/corrective-actions").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(correctiveBody).cookie(readOnly))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/cost-controls/corrective-actions/999999999").contextPath("/api")
                        .param("version", "0").contentType(MediaType.APPLICATION_JSON).content(correctiveBody).cookie(readOnly))
                .andExpect(status().isForbidden());
    }

    @Test
    void overviewAndTraceRejectProjectOutsider() throws Exception {
        Cookie query = cookie("cost:control:query");
        mockMvc.perform(get("/api/cost-controls/projects/{id}/overview", PROJECT).contextPath("/api").cookie(query))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cost-controls/forecasts/{id}/trace", FORECAST).contextPath("/api").cookie(query))
                .andExpect(status().isForbidden());
    }

    private Cookie cookie(String... authorities) {
        String token = jwtUtils.generateToken(OUTSIDER, "cost.control.outsider", 0L, List.of(), List.of(authorities));
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private Cookie memberCookie(String... authorities) {
        String token = jwtUtils.generateToken(MEMBER, "cost.control.member", 0L, List.of(), List.of(authorities));
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private void assertVersionAndPermission(String name, String permission, Class<?>... parameterTypes) throws Exception {
        Method method = CostControlController.class.getMethod(name, parameterTypes);
        RequestParam version = method.getParameters()[1].getAnnotation(RequestParam.class);
        assertNotNull(version);
        assertTrue(version.required());
        PreAuthorize security = method.getAnnotation(PreAuthorize.class);
        assertNotNull(security);
        assertTrue(security.value().contains(permission));
    }
}
