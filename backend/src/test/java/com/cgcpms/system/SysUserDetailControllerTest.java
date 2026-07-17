package com.cgcpms.system;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.system.service.SysUserService;
import com.cgcpms.system.vo.SysUserVO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "jwt.secret=user-detail-controller-test-secret-key-at-least-sixty-four-characters-long")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SysUserDetailControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @MockitoBean private SysUserService service;

    @BeforeEach
    void stubService() {
        SysUserVO result = new SysUserVO();
        result.setId(1L);
        result.setUsername("detail-user");
        result.setRoleIds(List.of(2L));
        when(service.getById(1L)).thenReturn(result);
    }

    @Test
    void adminCanReadDetailWithoutPasswordField() throws Exception {
        mockMvc.perform(request(cookie(List.of(), List.of("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("detail-user"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void queryPermissionCanReadDetail() throws Exception {
        mockMvc.perform(request(cookie(List.of("system:user:query"), List.of("COMMON_USER"))))
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedPermissionIsRejected() throws Exception {
        mockMvc.perform(request(cookie(List.of("system:user:edit"), List.of("COMMON_USER"))))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(Cookie cookie) {
        return get("/system/users/1").cookie(cookie);
    }

    private Cookie cookie(List<String> permissions, List<String> roles) {
        String token = jwtUtils.generateToken(7L, "detail-reader", 0L, roles, permissions);
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }
}
