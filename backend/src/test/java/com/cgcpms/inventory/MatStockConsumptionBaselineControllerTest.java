package com.cgcpms.inventory;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.inventory.vo.StockConsumptionBaselineVO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "jwt.secret=stock-baseline-controller-test-secret-key-at-least-sixty-four-characters-long")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MatStockConsumptionBaselineControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @MockitoBean private MatStockService service;

    @BeforeEach
    void stubService() {
        StockConsumptionBaselineVO result = new StockConsumptionBaselineVO();
        result.setNetIssued30(new BigDecimal("8.0000"));
        when(service.getConsumptionBaseline(any())).thenReturn(result);
    }

    @Test
    void stockListPermissionCanReadBaseline() throws Exception {
        mockMvc.perform(request(cookie(List.of("inventory:stock:list"), List.of("PURCHASE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netIssued30").value(8.0000));
    }

    @Test
    void adminCanReadBaseline() throws Exception {
        mockMvc.perform(request(cookie(List.of(), List.of("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedPermissionIsRejected() throws Exception {
        mockMvc.perform(request(cookie(List.of("inventory:stock:edit"), List.of("PURCHASE_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(Cookie cookie) {
        return get("/inventory/stock/1/consumption-baseline").cookie(cookie);
    }

    private Cookie cookie(List<String> permissions, List<String> roles) {
        String token = jwtUtils.generateToken(7L, "baseline-user", 0L, roles, permissions);
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }
}
