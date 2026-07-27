package com.cgcpms.material;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.material.service.MdMaterialService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=material-controller-test-secret-key-at-least-sixty-four-characters-long",
        "auth.csrf.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MdMaterialControllerSecurityTest {

    private static final String MATERIAL_JSON = """
            {"materialCode":"MAT-SEC-001","materialName":"权限测试材料","status":"ENABLE"}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @MockitoBean private MdMaterialService service;

    @BeforeEach
    void stubCreate() {
        when(service.create(any())).thenReturn(101L);
    }

    @Test
    void listPermissionCanReadListAndDetail() throws Exception {
        Cookie reader = authCookie(List.of("material:dict:list"), List.of("USER"));
        mockMvc.perform(getApi("/materials").cookie(reader)).andExpect(status().isOk());
        mockMvc.perform(getApi("/materials/101").cookie(reader)).andExpect(status().isOk());
    }

    @Test
    void legacyQueryPermissionCannotReadListOrDetail() throws Exception {
        Cookie reader = authCookie(List.of("material:query"), List.of("USER"));
        mockMvc.perform(getApi("/materials").cookie(reader)).andExpect(status().isForbidden());
        mockMvc.perform(getApi("/materials/101").cookie(reader)).andExpect(status().isForbidden());
    }

    @Test
    void listPermissionCannotCreateEditOrChangeStatus() throws Exception {
        Cookie reader = authCookie(List.of("material:dict:list"), List.of("USER"));
        mockMvc.perform(withCsrf(postApi("/materials").cookie(reader).content(MATERIAL_JSON)))
                .andExpect(status().isForbidden());
        mockMvc.perform(withCsrf(putApi("/materials/101").cookie(reader).content(MATERIAL_JSON)))
                .andExpect(status().isForbidden());
        mockMvc.perform(withCsrf(putApi("/materials/101/status").cookie(reader).param("status", "DISABLE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorRoleCanWriteWithCsrf() throws Exception {
        Cookie administrator = authCookie(List.of(), List.of("ADMIN"));
        mockMvc.perform(withCsrf(postApi("/materials").cookie(administrator).content(MATERIAL_JSON)))
                .andExpect(status().isOk());
        mockMvc.perform(withCsrf(putApi("/materials/101").cookie(administrator).content(MATERIAL_JSON)))
                .andExpect(status().isOk());
        mockMvc.perform(withCsrf(putApi("/materials/101/status").cookie(administrator).param("status", "DISABLE")))
                .andExpect(status().isOk());
    }

    @Test
    void cookieJwtWriteWithoutCsrfIsRejected() throws Exception {
        Cookie administrator = authCookie(List.of(), List.of("ADMIN"));
        mockMvc.perform(postApi("/materials").cookie(administrator).content(MATERIAL_JSON))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder getApi(String path) {
        return get("/api" + path).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postApi(String path) {
        return post("/api" + path).contextPath("/api").contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder putApi(String path) {
        return put("/api" + path).contextPath("/api").contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", "material-controller-csrf"))
                .header("X-XSRF-TOKEN", "material-controller-csrf");
    }

    private Cookie authCookie(List<String> permissions, List<String> roles) {
        String token = jwtUtils.generateToken(7L, "material-user", 0L, roles, permissions);
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }
}
