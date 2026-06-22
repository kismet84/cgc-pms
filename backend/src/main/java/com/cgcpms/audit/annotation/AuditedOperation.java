package com.cgcpms.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注控制器方法以记录操作审计事件。
 * 切面在 finally 块中发布 OperationAuditEvent，确保异常时也能记录。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedOperation {

    /** 操作类型: LOGIN / LOGOUT / CREATE / UPDATE / DELETE / SUBMIT / APPROVE / UPLOAD / DOWNLOAD */
    String type();

    /** 业务类型: CONTRACT / RECEIPT / SETTLEMENT / INVOICE 等 */
    String businessType() default "";

    /** SpEL expression to extract business id from method args, e.g. "#id" or "#contract.id" */
    String businessIdExpression() default "";
}
