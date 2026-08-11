package com.cgcpms.security;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.controller.MatPurchaseRequestController;
import com.cgcpms.purchase.dto.PurchaseRequestCreateCommand;
import com.cgcpms.quality.dto.QualitySafetyModels.RectificationCommand;
import com.cgcpms.requisition.controller.MatRequisitionController;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.schedule.dto.ProjectScheduleModels.DailyProgressBatch;
import com.cgcpms.site.entity.SiteDailyLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BusinessAmountRequestAdviceTest {

    private final BusinessAmountRequestAdvice advice = new BusinessAmountRequestAdvice(new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forgedEmployeeBodies")
    void rejectsNonNullAmountFieldsForRegisteredEmployeeInputs(String name, Class<?> targetType, String json) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> advice.validateJson(targetType, bytes(json)));

        assertEquals("AMOUNT_FIELD_FORBIDDEN", exception.getCode());
    }

    @Test
    void allowsNullAmountFieldsAndReturnsOriginalBytes() {
        byte[] raw = bytes("{\"header\":{\"projectId\":100,\"cost\":null},"
                + "\"items\":[{\"materialId\":1,\"estimatedUnitPrice\":null}]}");

        assertSame(raw, advice.validateJson(PurchaseRequestCreateCommand.class, raw));
    }

    @Test
    void amountAuthorityReturnsOriginalBytesWithoutInspectionFailure() {
        authenticate("business:amount:view");
        byte[] raw = bytes("{\"projectId\":100,\"totalAmount\":88.60,\"unknownCost\":5.00}");

        assertSame(raw, advice.validateJson(MatRequisition.class, raw));
    }

    @Test
    void unregisteredRequestTypeIsUntouched() {
        byte[] raw = bytes("{\"amount\":88.60}");

        assertSame(raw, advice.validateJson(UnregisteredCommand.class, raw));
    }

    @Test
    void beforeBodyReadValidatesRawJsonBeforeDeserialization() {
        byte[] raw = bytes("{\"projectId\":100,\"totalAmount\":1.00}");
        MockHttpInputMessage input = new MockHttpInputMessage(raw);

        BusinessException exception = assertThrows(BusinessException.class, () -> advice.beforeBodyRead(
                input,
                mock(MethodParameter.class),
                MatRequisition.class,
                MappingJackson2HttpMessageConverter.class));

        assertEquals("AMOUNT_FIELD_FORBIDDEN", exception.getCode());
    }

    @Test
    void beforeBodyReadReturnsEquivalentOriginalBodyWhenAllowed() throws IOException {
        byte[] raw = bytes("{\"projectId\":100,\"totalAmount\":null}");
        HttpInputMessage result = advice.beforeBodyRead(
                new MockHttpInputMessage(raw),
                mock(MethodParameter.class),
                MatRequisition.class,
                MappingJackson2HttpMessageConverter.class);

        assertArrayEquals(raw, result.getBody().readAllBytes());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forgedListWrappedEmployeeBodies")
    void beforeBodyReadRejectsAmountsInRegisteredListElementTypes(
            String name, Class<?> controllerType, String json) throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(controllerType.getDeclaredMethod(
                "saveItemsBatch", Long.class, List.class), 1);

        BusinessException exception = assertThrows(BusinessException.class, () -> advice.beforeBodyRead(
                new MockHttpInputMessage(bytes(json)),
                parameter,
                parameter.getGenericParameterType(),
                MappingJackson2HttpMessageConverter.class));

        assertEquals("AMOUNT_FIELD_FORBIDDEN", exception.getCode());
    }

    private static Stream<Arguments> forgedEmployeeBodies() {
        return Stream.of(
                Arguments.of("采购申请嵌套单价", PurchaseRequestCreateCommand.class,
                        "{\"header\":{\"projectId\":100},\"items\":[{\"materialId\":1,\"quantity\":2,\"estimatedUnitPrice\":3.50}]}"),
                Arguments.of("材料领用只读总额", MatRequisition.class,
                        "{\"projectId\":100,\"totalAmount\":10.00}"),
                Arguments.of("现场日报未知成本", SiteDailyLog.class,
                        "{\"projectId\":100,\"constructionContent\":\"施工\",\"cost\":2.00}"),
                Arguments.of("日进度嵌套价格", DailyProgressBatch.class,
                        "{\"items\":[{\"wbsTaskId\":1,\"currentProgress\":10,\"completedQuantity\":2,\"workDescription\":\"施工\",\"price\":1.00}]}"),
                Arguments.of("本人整改未知金额", RectificationCommand.class,
                        "{\"issueId\":1,\"actionDescription\":\"整改\",\"responsibleUserId\":2,\"plannedCompleteDate\":\"2026-08-12\",\"amount\":1.00}"));
    }

    private static Stream<Arguments> forgedListWrappedEmployeeBodies() {
        return Stream.of(
                Arguments.of("采购自助批量明细", MatPurchaseRequestController.class,
                        "[{\"requestId\":1,\"materialId\":2,\"quantity\":3,\"estimatedUnitPrice\":4.50}]"),
                Arguments.of("领料自助批量明细", MatRequisitionController.class,
                        "[{\"requisitionId\":1,\"wbsTaskId\":2,\"materialId\":3,\"quantity\":4,\"unitPrice\":5.50}]"));
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record UnregisteredCommand(String value) {
    }
}
