package com.cgcpms.security;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.dto.PurchaseRequestCreateCommand;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.quality.dto.QualitySafetyModels.RectificationCommand;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.entity.MatRequisitionItem;
import com.cgcpms.schedule.dto.ProjectScheduleModels.DailyProgressBatch;
import com.cgcpms.site.entity.SiteDailyLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@ControllerAdvice
public class BusinessAmountRequestAdvice implements RequestBodyAdvice {

    private static final Set<Class<?>> REGISTERED_TARGETS = Set.of(
            PurchaseRequestCreateCommand.class,
            MatRequisition.class,
            MatPurchaseRequestItem.class,
            MatRequisitionItem.class,
            SiteDailyLog.class,
            DailyProgressBatch.class,
            RectificationCommand.class);

    private final ObjectMapper objectMapper;

    public BusinessAmountRequestAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] bytes = inputMessage.getBody().readAllBytes();
        byte[] validated = validateJson(targetType, bytes);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(inputMessage.getHeaders());
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(validated); }
            @Override public HttpHeaders getHeaders() { return headers; }
        };
    }

    public byte[] validateJson(Type targetType, byte[] rawBody) {
        if (!isRegisteredTarget(targetType) || BusinessAmountAccess.canView()
                || rawBody == null || rawBody.length == 0) return rawBody;
        try {
            rejectAmounts(objectMapper.readTree(rawBody), "$");
            return rawBody;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            return rawBody; // malformed JSON remains the message converter's responsibility
        }
    }

    private static boolean isRegisteredTarget(Type targetType) {
        if (targetType instanceof Class<?> type) return REGISTERED_TARGETS.contains(type);
        if (!(targetType instanceof ParameterizedType parameterized)
                || !(parameterized.getRawType() instanceof Class<?> rawType)
                || !Iterable.class.isAssignableFrom(rawType)) return false;
        for (Type elementType : parameterized.getActualTypeArguments()) {
            if (isRegisteredTarget(elementType)) return true;
        }
        return false;
    }

    private void rejectAmounts(JsonNode node, String path) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) rejectAmounts(node.get(index), path + "[" + index + "]");
            return;
        }
        if (!node.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (BusinessAmountFieldCatalog.isAmountField(field.getKey())
                    && value != null && !value.isNull()
                    && (!value.isTextual() || !value.asText().isBlank())) {
                throw new BusinessException("AMOUNT_FIELD_FORBIDDEN",
                        "当前账号不得提交金额字段: " + path + "." + field.getKey());
            }
            rejectAmounts(value, path + "." + field.getKey());
        }
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public @Nullable Object handleEmptyBody(@Nullable Object body, HttpInputMessage inputMessage,
                                            MethodParameter parameter, Type targetType,
                                            Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
