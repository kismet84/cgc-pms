package com.cgcpms.security;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.contract.controller.CtContractController;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.financeops.controller.FinanceOperationsController;
import com.cgcpms.payment.controller.PaymentTraceController;
import com.cgcpms.payment.vo.PaymentTraceVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BusinessAmountResponseAdviceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusinessAmountResponseAdvice advice = new BusinessAmountResponseAdvice(objectMapper);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesDeclaredVoContractAndRedactsTextAmount() throws NoSuchMethodException {
        CtContractVO contract = new CtContractVO();
        contract.setId("1001");
        contract.setCurrentAmount("88.00");
        contract.setTaxRate("0.09");

        JsonNode result = objectMapper.valueToTree(apply(ApiResponse.success(contract),
                returnType(CtContractController.class, "getById", Long.class)));

        assertEquals("1001", result.at("/data/id").asText());
        assertTrue(result.at("/data/currentAmount").isNull());
        assertEquals("0.09", result.at("/data/taxRate").asText());
    }

    @Test
    void resolvesGenericListElementContractForNestedEntities() throws NoSuchMethodException {
        CtContract contract = new CtContract();
        contract.setCurrentAmount(new BigDecimal("77.00"));
        contract.setTaxRate(new BigDecimal("0.06"));
        PaymentTraceVO trace = new PaymentTraceVO();
        trace.setContract(contract);

        JsonNode result = objectMapper.valueToTree(apply(ApiResponse.success(List.of(trace)),
                returnType(PaymentTraceController.class, "byProject", Long.class)));

        assertTrue(result.at("/data/0/contract/currentAmount").isNull());
        assertEquals("0.06", result.at("/data/0/contract/taxRate").asText());
    }

    @Test
    void resolvesEndpointContractForJdbcMap() throws NoSuchMethodException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoice_amount", new BigDecimal("100.00"));
        data.put("unallocatedAmount", new BigDecimal("25.00"));
        data.put("writeOffRate", new BigDecimal("0.75"));
        data.put("allocations", List.of(Map.of("allocated_amount", new BigDecimal("75.00"))));

        JsonNode result = objectMapper.valueToTree(apply(ApiResponse.success(data),
                returnType(FinanceOperationsController.class, "writeOff", Long.class)));

        assertTrue(result.at("/data/invoice_amount").isNull());
        assertTrue(result.at("/data/unallocatedAmount").isNull());
        assertEquals(0, new BigDecimal("0.75")
                .compareTo(result.at("/data/writeOffRate").decimalValue()));
        assertTrue(result.at("/data/allocations/0/allocated_amount").isNull());
    }

    @Test
    void failsClosedForUnclassifiedNumericTextInDeclaredResponse() throws NoSuchMethodException {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> apply(ApiResponse.success(Map.of("unreviewedMetric", "1.25")),
                        returnType(CtContractController.class, "getById", Long.class)));

        assertEquals("AMOUNT_SCHEMA_UNCLASSIFIED", exception.getCode());
    }

    @Test
    void amountAuthorityPreservesSemanticJson() throws NoSuchMethodException {
        authenticate("business:amount:view");
        CtContractVO contract = new CtContractVO();
        contract.setCurrentAmount("99.10");
        ApiResponse<CtContractVO> body = ApiResponse.success(contract);

        assertEquals(objectMapper.valueToTree(body), objectMapper.valueToTree(apply(body,
                returnType(CtContractController.class, "getById", Long.class))));
    }

    @Test
    void administratorRoleAloneDoesNotBypassAmountPermission() throws NoSuchMethodException {
        authenticate("ROLE_SUPER_ADMIN");
        CtContractVO contract = new CtContractVO();
        contract.setCurrentAmount("99.10");

        JsonNode result = objectMapper.valueToTree(apply(ApiResponse.success(contract),
                returnType(CtContractController.class, "getById", Long.class)));

        assertTrue(result.at("/data/currentAmount").isNull());
    }

    private Object apply(Object body, MethodParameter returnType) {
        return advice.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                mock(ServerHttpRequest.class),
                mock(ServerHttpResponse.class));
    }

    private static MethodParameter returnType(Class<?> controllerType, String methodName,
                                              Class<?>... parameterTypes) throws NoSuchMethodException {
        return new MethodParameter(controllerType.getDeclaredMethod(methodName, parameterTypes), -1);
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
