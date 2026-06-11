package com.cgcpms.common.util;

import java.math.BigDecimal;

public final class BigDecimalUtils {
    private BigDecimalUtils() {}

    public static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
