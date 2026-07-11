package com.cgcpms.sitedaily;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
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

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SiteDailyLogControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtils jwtUtils;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(1L, "admin", 0L, List.of("ADMIN"), List.of()));
    }

    @Test
    void draftCanBeEditedAndSubmittedOnlyOnce() throws Exception {
        String body = "{\"projectId\":10001,\"reportDate\":\"2099-01-01\","
                + "\"constructionContent\":\"完成基础施工\",\"issuesDelays\":\"材料晚到\","
                + "\"nextDayPlan\":\"开始主体施工\"}";
        String response = mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1");

        mockMvc.perform(post("/api/site-daily-logs").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/site-daily-logs/" + id).contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":10001,\"reportDate\":\"2099-01-01\",\"constructionContent\":\"已更新施工内容\"}"))
                .andExpect(status().isOk());
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
}
