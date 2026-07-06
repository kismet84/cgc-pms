package com.cgcpms.auth.config;

import com.cgcpms.auth.filter.JwtAuthenticationFilter;
import com.cgcpms.common.filter.GlobalWriteRateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpMethod;

import java.io.IOException;

/**
 * Stateless Spring Security configuration wiring in the JWT filter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    public static final String[] AUTH_WHITELIST_PATHS = {
            "/auth/login",
            "/auth/refresh"
    };
    public static final String DEV_LOGIN_PATH = "/auth/dev-login";

    public static final String[] DOC_WHITELIST_PATHS = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**"
    };

    public static final String[] HEALTH_WHITELIST_PATHS = {
            "/actuator/health/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final GlobalWriteRateLimitFilter globalWriteRateLimitFilter;
    private final Environment environment;
    private final boolean devLoginEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          GlobalWriteRateLimitFilter globalWriteRateLimitFilter,
                          Environment environment,
                          @Value("${auth.dev-login.enabled:false}") boolean devLoginEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.globalWriteRateLimitFilter = globalWriteRateLimitFilter;
        this.environment = environment;
        this.devLoginEnabled = devLoginEnabled;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/auth/login", "POST")))
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .xssProtection(xss -> xss.disable()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(AUTH_WHITELIST_PATHS).permitAll();
                    if (isDevLoginExposed()) {
                        auth.requestMatchers(HttpMethod.GET, DEV_LOGIN_PATH).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.GET, HEALTH_WHITELIST_PATHS).permitAll();
                    auth.anyRequest().authenticated();
                })
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(globalWriteRateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    public boolean isDevLoginExposed() {
        return devLoginEnabled && environment.acceptsProfiles(Profiles.of("dev", "local"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }

}
