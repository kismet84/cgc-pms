package com.cgcpms.common.aspect;

import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.exception.BusinessException;
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
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        bindRequest("10.0.0.8");
        when(lockoutStore.getRemainingLockoutMillis(anyString())).thenReturn(120_000L);

        ProceedingJoinPoint joinPoint = loginJoinPoint(ApiResponse.success("ok"), "admin");

        assertThrows(RateLimitExceededException.class,
                () -> aspect.around(joinPoint, loginRateLimit()));

        var key = ArgumentCaptor.forClass(String.class);
        verify(lockoutStore).getRemainingLockoutMillis(key.capture());
        assertTrue(key.getValue().matches("[a-f0-9]{64}"));
        verify(joinPoint, never()).proceed();
        verifyNoInteractions(counterStore);
    }

    @Test
    @DisplayName("登录失败时通过 store 记录失败次数")
    void recordsFailureViaStoreOnLoginFailure() throws Throwable {
        bindRequest("10.0.0.9");
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        ProceedingJoinPoint joinPoint = loginJoinPoint(
                ApiResponse.fail("AUTH_FAILED", "用户名或密码错误"), "admin");

        aspect.around(joinPoint, loginRateLimit());

        var key = ArgumentCaptor.forClass(String.class);
        verify(lockoutStore).recordFailure(key.capture(), eq(5), eq(15L), eq(30L));
        assertTrue(key.getValue().matches("[a-f0-9]{64}"));
        verify(lockoutStore, never()).clear(anyString());
    }

    @Test
    @DisplayName("登录成功时清理 store 中的失败状态")
    void clearsFailureStateViaStoreOnLoginSuccess() throws Throwable {
        bindRequest("10.0.0.10");
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        ProceedingJoinPoint joinPoint = loginJoinPoint(ApiResponse.success("ok"), "admin");

        aspect.around(joinPoint, loginRateLimit());

        verify(lockoutStore).clear(anyString());
        verify(lockoutStore, never()).recordFailure(anyString(), anyInt(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("同一出口IP的不同账号使用不同锁定键")
    void separatesAccountsBehindTheSameIp() throws Throwable {
        bindRequest("10.0.0.10");
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        aspect.around(loginJoinPoint(ApiResponse.success("ok"), "admin"), loginRateLimit());
        aspect.around(loginJoinPoint(ApiResponse.success("ok"), "finance"), loginRateLimit());

        var keys = ArgumentCaptor.forClass(String.class);
        verify(lockoutStore, times(2)).clear(keys.capture());
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
    }

    @Test
    @DisplayName("登录技术异常不计入凭据失败次数")
    void technicalFailureDoesNotCountAsCredentialFailure() throws Throwable {
        bindRequest("10.0.0.12");
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        assertThrows(IllegalStateException.class,
                () -> aspect.around(throwingLoginJoinPoint(
                        new IllegalStateException("database unavailable"), "admin"), loginRateLimit()));

        verify(lockoutStore, never()).recordFailure(anyString(), anyInt(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("认证失败业务异常计入凭据失败次数")
    void authFailureExceptionCountsAsCredentialFailure() throws Throwable {
        bindRequest("10.0.0.13");
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> aspect.around(throwingLoginJoinPoint(
                        new BusinessException("AUTH_FAILED", "用户名或密码错误"), "admin"), loginRateLimit()));

        verify(lockoutStore).recordFailure(anyString(), eq(5), eq(15L), eq(30L));
    }

    @Test
    @DisplayName("非认证失败业务异常不计入凭据失败次数")
    void otherBusinessFailureDoesNotCountAsCredentialFailure() throws Throwable {
        bindRequest("10.0.0.14");
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> aspect.around(throwingLoginJoinPoint(
                        new BusinessException("AUTH_DISABLED", "账号已停用"), "admin"), loginRateLimit()));

        verify(lockoutStore, never()).recordFailure(anyString(), anyInt(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("普通限流端点成功或失败均不读写登录锁定状态")
    void ordinaryRateLimitDoesNotTouchLoginLockoutState() throws Throwable {
        bindRequest("10.0.0.11");
        when(counterStore.increment(anyString(), anyInt())).thenReturn(1L);

        aspect.around(loginJoinPoint(ApiResponse.success("ok"), "admin"), ordinaryRateLimit());
        assertThrows(IllegalStateException.class,
                () -> aspect.around(throwingJoinPoint(), ordinaryRateLimit()));

        verifyNoInteractions(lockoutStore);
    }

    private MockHttpServletRequest bindRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private ProceedingJoinPoint loginJoinPoint(Object result, String username) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword("unused");
        when(signature.toShortString()).thenReturn("AuthController.login(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private ProceedingJoinPoint throwingJoinPoint() throws Throwable {
        ProceedingJoinPoint joinPoint = loginJoinPoint(null, "admin");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("ordinary failure"));
        return joinPoint;
    }

    private ProceedingJoinPoint throwingLoginJoinPoint(Throwable throwable, String username) throws Throwable {
        ProceedingJoinPoint joinPoint = loginJoinPoint(null, username);
        when(joinPoint.proceed()).thenThrow(throwable);
        return joinPoint;
    }

    private RateLimit loginRateLimit() throws NoSuchMethodException {
        Method method = FixtureController.class.getDeclaredMethod("login");
        return method.getAnnotation(RateLimit.class);
    }

    private RateLimit ordinaryRateLimit() throws NoSuchMethodException {
        Method method = FixtureController.class.getDeclaredMethod("ordinary");
        return method.getAnnotation(RateLimit.class);
    }

    static class FixtureController {
        @RateLimit(maxRequests = 5, windowSeconds = 60, loginLockout = true)
        public void login() {
        }

        @RateLimit(maxRequests = 5, windowSeconds = 60)
        public void ordinary() {
        }
    }
}
