package com.cgcpms.system.dict;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("SysDictDataController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SysDictDataControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private Long dictDataId;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /system/dict/data without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/system/dict/data")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /system/dict/data -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/system/dict/data").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("GET /system/dict/data/by-code/{dictCode} -> 200")
    void testGetByDictCode() throws Exception {
        mockMvc.perform(g("/system/dict/data/by-code/contract_type").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(4) @DisplayName("POST /system/dict/data -> 200 creates dict data")
    void testCreate() throws Exception {
        String body = "{\"dictTypeId\":1001,\"dictLabel\":\"TEST-" + System.nanoTime() + "\",\"dictValue\":\"TEST_VAL\",\"sortOrder\":999}";
        String resp = mockMvc.perform(p("/system/dict/data").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        dictDataId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(dictDataId);
    }

    @Test @Order(5) @DisplayName("POST /system/dict/data missing required -> 400")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/system/dict/data").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(6) @DisplayName("GET /system/dict/data/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(dictDataId);
        mockMvc.perform(g("/system/dict/data/" + dictDataId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(7) @DisplayName("PUT /system/dict/data/{id} -> 200")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(dictDataId);
        String body = "{\"dictTypeId\":1001,\"dictLabel\":\"TEST-UPD-" + System.nanoTime() + "\",\"dictValue\":\"TEST_VAL_UPD\"}";
        mockMvc.perform(u("/system/dict/data/" + dictDataId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("DELETE /system/dict/data/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(dictDataId);
        mockMvc.perform(d("/system/dict/data/" + dictDataId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
