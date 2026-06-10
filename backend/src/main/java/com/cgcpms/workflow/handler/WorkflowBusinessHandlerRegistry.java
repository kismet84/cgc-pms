package com.cgcpms.workflow.handler;

import com.cgcpms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that collects all {@link WorkflowBusinessHandler} implementations
 * and dispatches to the correct handler by business type.
 */
@Component
public class WorkflowBusinessHandlerRegistry {

    private final Map<String, WorkflowBusinessHandler> handlerMap;

    public WorkflowBusinessHandlerRegistry(List<WorkflowBusinessHandler> handlers) {
        handlerMap = new HashMap<>();
        for (WorkflowBusinessHandler handler : handlers) {
            handlerMap.put(handler.supportBusinessType(), handler);
        }
    }

    public WorkflowBusinessHandler get(String businessType) {
        WorkflowBusinessHandler handler = handlerMap.get(businessType);
        if (handler == null) {
            throw new BusinessException("HANDLER_NOT_FOUND",
                    "未找到业务类型 [" + businessType + "] 的审批处理器");
        }
        return handler;
    }

    public boolean hasHandler(String businessType) {
        return handlerMap.containsKey(businessType);
    }
}
