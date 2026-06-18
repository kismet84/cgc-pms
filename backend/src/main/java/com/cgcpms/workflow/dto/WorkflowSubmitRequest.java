package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.cgcpms.workflow.handler.WorkflowBusinessHandler;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WorkflowSubmitRequest {

    @NotBlank
    private String businessType;

    @NotNull
    private Long businessId;

    @NotBlank
    private String title;

    private BigDecimal amount;

    private Long projectId;

    private Long contractId;

    private String businessSummary;

    /**
     * 工作流变量，JSON Map 格式字符串。
     * 示例: {@code {"key1":"value1","key2":"value2"}}
     * 可为 null，提交后由对应 {@link WorkflowBusinessHandler} 解析并使用。
     */
    private String variables;

    /** Optional: cc (抄送) user IDs — if null/empty, no cc rows are created */
    private List<Long> ccUserIds;
}
