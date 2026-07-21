package com.cgcpms.sitedaily;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class SiteDailyLogControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtils jwtUtils;
    @Autowired ObjectMapper objectMapper;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(1L, "admin", 0L, List.of("ADMIN"), List.of()));
    }

    private Cookie permissionCookie(String... permissions) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(2L, "site-reader", 0L, List.of(), List.of(permissions)));
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
        String body = "{\"projectId\":10001,\"reportDate\":\"2099-01-01\","
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
                        .content("{\"projectId\":10001,\"reportDate\":\"2099-01-01\",\"constructionContent\":\"已更新施工内容\",\"weatherSummary\":\"晴转多云\",\"onSiteHeadcount\":0,\"expectedUpdatedAt\":\"" + expectedUpdatedAt + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/site-daily-logs/" + id).contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weatherSummary").value("晴转多云"))
                .andExpect(jsonPath("$.data.onSiteHeadcount").value(0));
        mockMvc.perform(post("/api/site-daily-logs/" + id + "/submit").contextPath("/api")
                        .cookie(adminCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/site-daily-logs/" + id).contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedBy").value("1"))
                .andExpect(jsonPath("$.data.submittedAt").exists());
        mockMvc.perform(put("/api/site-daily-logs/" + id).contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"reportDate\":\"2099-01-01\",\"constructionContent\":\"禁止修改\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/site-daily-logs/" + id + "/submit").contextPath("/api")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/site-daily-logs").contextPath("/api").cookie(adminCookie())
                        .param("projectId", "10001").param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].projectName").exists());
    }

    @Test
    void weatherAndHeadcountValidationRejectsInvalidInput() throws Exception {
        String base = "{\"projectId\":10001,\"reportDate\":\"2099-02-01\",\"constructionContent\":\"测试\",";
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
                        .content("{\"projectId\":10001,\"reportDate\":\"2099-03-01\",\"constructionContent\":\"测试未填写人数\",\"weatherSummary\":\"阴\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1");

        mockMvc.perform(get("/api/site-daily-logs/" + id).contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weatherSummary").value("阴"))
                .andExpect(jsonPath("$.data.onSiteHeadcount").value(nullValue()));
    }
}
