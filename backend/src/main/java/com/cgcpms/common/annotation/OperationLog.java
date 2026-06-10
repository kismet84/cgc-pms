package com.cgcpms.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose invocation should be recorded as an operation log entry.
 */
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
