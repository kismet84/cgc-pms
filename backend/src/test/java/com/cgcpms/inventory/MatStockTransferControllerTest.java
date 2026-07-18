package com.cgcpms.inventory;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.inventory.vo.StockTransferVO;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "jwt.secret=stock-transfer-controller-test-secret-key-at-least-sixty-four-characters-long")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MatStockTransferControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @MockitoBean private MatStockService service;

    @BeforeEach
    void stubService() {
        StockTransferVO result = new StockTransferVO();
        result.setId(9001L);
        result.setStatus("COMPLETED");
        when(service.transfer(any())).thenReturn(result);
    }

    @Test
    void bothPermissionsCanPostTransfer() throws Exception {
        mockMvc.perform(request(cookie(List.of("inventory:stock:edit", "inventory:transaction:add"), List.of("PURCHASE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void adminCanPostTransfer() throws Exception {
        mockMvc.perform(request(cookie(List.of(), List.of("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void eitherSinglePermissionIsRejected() throws Exception {
        mockMvc.perform(request(cookie(List.of("inventory:stock:edit"), List.of("PURCHASE_MANAGER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(request(cookie(List.of("inventory:transaction:add"), List.of("PURCHASE_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(Cookie cookie) {
        return post("/inventory/stock/transfers")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceStockId":1,"targetStockId":2,"quantity":"1.0000",
                         "idempotencyKey":"controller-key","reason":"controller test"}
                        """);
    }

    private Cookie cookie(List<String> permissions, List<String> roles) {
        String token = jwtUtils.generateToken(7L, "transfer-user", 0L, roles, permissions);
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }
}
