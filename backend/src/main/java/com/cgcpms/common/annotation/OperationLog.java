package com.cgcpms.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解 — 当前未被使用。
 * @deprecated 计划在下一个主要版本中删除，或与审计日志框架集成。
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** Operation description. */
    String value() default "";

    /** Whether to record method parameters. */
    boolean saveParams() default true;

    /** Whether to record method return value. */
    boolean saveResult() default false;
}
