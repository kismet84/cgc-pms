package com.cgcpms.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FallbackLoginLockoutStore - 本地降级锁定")
class FallbackLoginLockoutStoreTest {

    @Test
    @DisplayName("达到阈值后进入锁定")
    void locksAfterThresholdReached() {
        FallbackLoginLockoutStore store = new FallbackLoginLockoutStore();

        store.recordFailure("10.0.0.11", 3, 15, 30);
        store.recordFailure("10.0.0.11", 3, 15, 30);
        assertEquals(0L, store.getRemainingLockoutMillis("10.0.0.11"));

        store.recordFailure("10.0.0.11", 3, 15, 30);

        assertTrue(store.getRemainingLockoutMillis("10.0.0.11") > 0L);
    }

    @Test
    @DisplayName("clear 会清空失败和锁定状态")
    void clearRemovesState() {
        FallbackLoginLockoutStore store = new FallbackLoginLockoutStore();

        store.recordFailure("10.0.0.12", 1, 15, 30);
        assertTrue(store.getRemainingLockoutMillis("10.0.0.12") > 0L);

        store.clear("10.0.0.12");

        assertEquals(0L, store.getRemainingLockoutMillis("10.0.0.12"));
    }
}
