package com.cgcpms.common.result;

import com.cgcpms.common.context.TraceIdContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.Data;

import java.io.Serializable;

/**
 * Unified API response envelope.
 *
 * @param <T> payload type
 */
@Data
public class ApiResponse<T> implements Serializable {

    /** "0" for success, otherwise a business / system error code. */
    private String code;

    private String message;

    private String traceId;

    private T data;

    public static final String SUCCESS_CODE = "0";

    public ApiResponse() {
        this.traceId = TraceIdContext.get();
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(SUCCESS_CODE);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    public static <T> ApiResponse<T> fail(BusinessException e) {
        return fail(e.getCode(), e.getMessage());
    }
}
