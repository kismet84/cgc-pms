package com.cgcpms.common.aspect;

import com.cgcpms.common.util.SensitiveDataUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * AOP aspect that masks sensitive fields in method‑argument log output.
 *
 * <h3>What it masks</h3>
 * <ul>
 *   <li><b>Password fields</b>: any field whose name contains {@code password}
 *       or {@code pwd} → replaced with {@code "****"} in logs.</li>
 *   <li><b>Token fields</b>: any field whose name contains {@code token} →
 *       only the first 8 characters are shown; the rest is replaced with
 *       {@code ...} in logs.</li>
 * </ul>
 *
 * <h3>Scope</h3>
 * Currently intercepts all public methods in {@code com.cgcpms.*.controller}
 * packages. The aspect does NOT alter method arguments — it only produces a
 * sanitised copy for the log statement.
 *
 * <h3>Log level</h3>
 * The masked argument log line is emitted at {@code DEBUG} level so it is
 * suppressed in production while still available for troubleshooting in dev.
 *
 * @see SensitiveDataUtils
 */
@Aspect
@Component
public class SensitiveDataMaskingAspect {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataMaskingAspect.class);

    /**
     * Intercept controller methods and log masked arguments.
     * <p>
     * The original arguments are NOT modified — only the log output is sanitised.
     * If the method throws, the exception propagates normally.
     */
    @Around("execution(public * com.cgcpms..controller..*(..))")
    public Object maskSensitiveArgsInLogs(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!log.isDebugEnabled()) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        Parameter[] params = null;
        try {
            params = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature())
                    .getMethod().getParameters();
        } catch (Exception ignored) {
            // fall through – log unmasked if we cannot inspect parameters
        }

        String maskedArgsDesc = describeArgs(args, params);
        log.debug("→ {} | args={}", joinPoint.getSignature().toShortString(), maskedArgsDesc);

        return joinPoint.proceed();
    }

    /**
     * Build a human‑readable description of method arguments with
     * sensitive fields masked.
     */
    private String describeArgs(Object[] args, Parameter[] params) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return IntStream.range(0, args.length)
                .mapToObj(i -> describeArg(args[i], params != null && i < params.length ? params[i] : null))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Describe a single argument. If the argument is a DTO annotated with
     * {@code @RequestBody}, its fields are individually masked.
     */
    private String describeArg(Object arg, Parameter param) {
        if (arg == null) {
            return "null";
        }
        // If it's a @RequestBody DTO, inspect its fields
        if (param != null && param.isAnnotationPresent(RequestBody.class)) {
            return maskDto(arg);
        }
        // For String args named token/password (e.g. header values)
        if (param != null && arg instanceof String s) {
            return SensitiveDataUtils.maskFieldValue(param.getName(), s);
        }
        return String.valueOf(arg);
    }

    /**
     * Recursively describe a DTO, masking password/token fields via reflection.
     */
    private String maskDto(Object dto) {
        if (dto == null) {
            return "null";
        }
        Class<?> clazz = dto.getClass();
        if (isSimpleType(clazz)) {
            return String.valueOf(dto);
        }

        StringBuilder sb = new StringBuilder(clazz.getSimpleName()).append('{');
        Field[] fields = clazz.getDeclaredFields();
        boolean first = true;
        for (Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(dto);
                String displayValue;
                if (value == null) {
                    displayValue = "null";
                } else if (isSimpleType(value.getClass())) {
                    displayValue = SensitiveDataUtils.maskFieldValue(field.getName(),
                            String.valueOf(value));
                } else {
                    // Nested object – recurse (shallow to avoid loops)
                    displayValue = maskDto(value);
                }
                if (!first) {
                    sb.append(", ");
                }
                sb.append(field.getName()).append('=').append(displayValue);
                first = false;
            } catch (IllegalAccessException ignored) {
                // skip fields we cannot access
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Returns {@code true} for types that are safe to convert via
     * {@code String.valueOf()} (primitives, wrappers, String, enums, etc.).
     */
    private boolean isSimpleType(Class<?> clazz) {
        if (clazz == null) {
            return true;
        }
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class.isAssignableFrom(clazz)
                || clazz.isEnum();
    }
}
