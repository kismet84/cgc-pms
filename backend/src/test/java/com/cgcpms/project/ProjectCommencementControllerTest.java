package com.cgcpms.project;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.common.JwtHttpTestTokenFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("项目开工准入审批人权限")
class ProjectCommencementControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtHttpTestTokenFactory jwt;

    @ParameterizedTest
    @ValueSource(strings = {"DEPARTMENT_MANAGER", "GENERAL_MANAGER"})
    void approversCanReadButCannotMutateCommencement(String roleCode) throws Exception {
        Cookie queryOnly = cookie(roleCode, List.of("project:commencement:query"));

        mockMvc.perform(get("/projects/999999999/activation-readiness").cookie(queryOnly))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/projects/999999999/commencement").cookie(queryOnly))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/projects/999999999/commencement").cookie(queryOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"plannedStartDate":"2026-08-05","basisType":"OWNER_CONTRACT"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/999999999/commencement/submit").cookie(queryOnly).param("version", "0"))
                .andExpect(status().isForbidden());
    }

    private Cookie cookie(String roleCode, List<String> permissions) {
        String token = jwt.generateToken(1L, roleCode.toLowerCase(), 0L, List.of(roleCode), permissions);
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }
}
