package com.cgcpms.cashbook;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "jwt.secret=cash-journal-controller-test-secret-key-at-least-sixty-four-characters-long"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CashJournalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(g("/fund-accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(g("/cash-journal-entries")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateAccountManualEntryAndExportCurrentFilter() throws Exception {
        String code = "CTRL-" + System.nanoTime();
        String accountBody = """
                {"accountCode":"%s","accountName":"Controller Cash","accountType":"CASH",
                 "openingDate":"2026-07-01","openingBalance":100.00}
                """.formatted(code);
        String accountId = mockMvc.perform(p("/fund-accounts").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(accountBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([0-9]+)\\\".*", "$1");

        mockMvc.perform(p("/cash-journal-entries").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":%s,"direction":"IN","amount":12.34,
                                 "businessDate":"2026-07-10","summary":"controller test"}
                                """.formatted(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(g("/cash-journal-entries/summary").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCount").isString());

        mockMvc.perform(g("/cash-journal-entries/export?status=DRAFT").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"));
    }

    @Test
    void createAndUpdateRejectAmountsOutsideDecimalBoundary() throws Exception {
        String body = """
                {"direction":"IN","amount":1.001,
                 "businessDate":"2026-07-10","summary":"invalid amount"}
                """;
        mockMvc.perform(p("/cash-journal-entries").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/cash-journal-entries/1").contextPath("/api").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1.001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRejectsInvalidDirectionAndOversizedTextAtControllerBoundary() throws Exception {
        mockMvc.perform(put("/api/cash-journal-entries/1").contextPath("/api").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"direction\":\"SIDEWAYS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/api/cash-journal-entries/1").contextPath("/api").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"counterpartyName\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/api/cash-journal-entries/1").contextPath("/api").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(1L, "admin", 0L, List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private MockHttpServletRequestBuilder g(String path) {
        return get("/api" + path).contextPath("/api");
    }

    private MockHttpServletRequestBuilder p(String path) {
        return post("/api" + path).contextPath("/api");
    }
}
