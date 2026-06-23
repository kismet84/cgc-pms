package com.cgcpms.inventory;

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
@DisplayName("MatWarehouseController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatWarehouseControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private Long whId;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /inventory/warehouses without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/inventory/warehouses")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /inventory/warehouses -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/inventory/warehouses").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("POST /inventory/warehouses -> 200 creates warehouse")
    void testCreate() throws Exception {
        String body = "{\"warehouseCode\":\"WH-TEST-" + System.nanoTime() + "\",\"warehouseName\":\"测试仓库\",\"projectId\":10001}";
        String resp = mockMvc.perform(p("/inventory/warehouses").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        whId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(whId);
    }

    @Test @Order(4) @DisplayName("POST /inventory/warehouses missing required -> 4xx")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/inventory/warehouses").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(5) @DisplayName("GET /inventory/warehouses/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(whId);
        mockMvc.perform(g("/inventory/warehouses/" + whId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(6) @DisplayName("PUT /inventory/warehouses/{id}/status -> 200")
    void testUpdateStatus() throws Exception {
        Assertions.assertNotNull(whId);
        mockMvc.perform(u("/inventory/warehouses/" + whId + "/status").cookie(adminCookie()).param("status", "DISABLE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(7) @DisplayName("PUT /inventory/warehouses/{id} -> 200 updates warehouse")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(whId);
        String body = "{\"warehouseCode\":\"WH-UPD-" + System.nanoTime() + "\",\"warehouseName\":\"更新仓库\",\"projectId\":10001}";
        mockMvc.perform(u("/inventory/warehouses/" + whId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("DELETE /inventory/warehouses/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(whId);
        mockMvc.perform(d("/inventory/warehouses/" + whId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
