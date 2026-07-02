package com.cgcpms.common.aspect;

import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.exception.RateLimitExceededException;
import com.cgcpms.common.ratelimit.LoginLockoutStore;
import com.cgcpms.common.ratelimit.RateLimitCounterStore;
import com.cgcpms.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("RateLimitAspect - 登录锁定存储委托")
class RateLimitAspectLockoutTest {

    private final RateLimitCounterStore counterStore = mock(RateLimitCounterStore.class);
    private final LoginLockoutStore lockoutStore = mock(LoginLockoutStore.class);
    private final RateLimitAspect aspect = new RateLimitAspect(counterStore, lockoutStore);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("已锁定时从 store 读取并直接拒绝请求")
    void blocksWhenStoreReportsActiveLockout() throws Throwable {
        MockHttpServletRequest request = bindRequest("10.0.0.8");
        when(lockoutStore.getRemainingLockoutMillis("10.0.0.8")).thenReturn(120_000L);

        ProceedingJoinPoint joinPoint = loginJoinPoint(ApiResponse.success("ok"));

        assertThrows(RateLimitExceededException.class,
                () -> aspect.around(joinPoint, loginRateLimit()));

        verify(lockoutStore).getRemainingLockoutMillis("10.0.0.8");
        verify(joinPoint, never()).proceed();
        verifyNoInteractions(counterStore);
    }

    @Test
    @DisplayName("登录失败时通过 store 记录失败次数")
    void recordsFailureViaStoreOnLoginFailure() throws Throwable {
        bindRequest("10.0.0.9");
        when(lockoutStore.getRemainingLockoutMillis("10.0.0.9")).thenReturn(0L);
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        ProceedingJoinPoint joinPoint = loginJoinPoint(ApiResponse.fail("AUTH_FAILED", "用户名或密码错误"));

        aspect.around(joinPoint, loginRateLimit());

        verify(lockoutStore).recordFailure(eq("10.0.0.9"), eq(5), eq(15L), eq(30L));
        verify(lockoutStore, never()).clear("10.0.0.9");
    }

    @Test
    @DisplayName("登录成功时清理 store 中的失败状态")
    void clearsFailureStateViaStoreOnLoginSuccess() throws Throwable {
        bindRequest("10.0.0.10");
        when(lockoutStore.getRemainingLockoutMillis("10.0.0.10")).thenReturn(0L);
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        ProceedingJoinPoint joinPoint = loginJoinPoint(ApiResponse.success("ok"));

        aspect.around(joinPoint, loginRateLimit());

        verify(lockoutStore).clear("10.0.0.10");
        verify(lockoutStore, never()).recordFailure(eq("10.0.0.10"), anyInt(), anyLong(), anyLong());
    }

    private MockHttpServletRequest bindRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private ProceedingJoinPoint loginJoinPoint(Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("AuthController.login(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private RateLimit loginRateLimit() throws NoSuchMethodException {
        Method method = FixtureController.class.getDeclaredMethod("login");
        return method.getAnnotation(RateLimit.class);
    }

    static class FixtureController {
        @RateLimit(maxRequests = 5, windowSeconds = 60)
        public void login() {
        }
    }
}
