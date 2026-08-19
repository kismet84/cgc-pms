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
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public static final String PROMETHEUS_PATH = "/actuator/prometheus";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final GlobalWriteRateLimitFilter globalWriteRateLimitFilter;
    private final Environment environment;
    private final boolean devLoginEnabled;
    private final boolean csrfEnabled;
    private final String monitoringUsername;
    private final String monitoringPassword;
    private final String monitoringPasswordFile;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          GlobalWriteRateLimitFilter globalWriteRateLimitFilter,
                          Environment environment,
                          @Value("${auth.dev-login.enabled:false}") boolean devLoginEnabled,
                          @Value("${auth.csrf.enabled:true}") boolean csrfEnabled,
                          @Value("${monitoring.scrape.username:}") String monitoringUsername,
                          @Value("${monitoring.scrape.password:}") String monitoringPassword,
                          @Value("${monitoring.scrape.password-file:}") String monitoringPasswordFile) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.globalWriteRateLimitFilter = globalWriteRateLimitFilter;
        this.environment = environment;
        this.devLoginEnabled = devLoginEnabled;
        this.csrfEnabled = csrfEnabled;
        this.monitoringUsername = monitoringUsername;
        this.monitoringPassword = monitoringPassword;
        this.monitoringPasswordFile = monitoringPasswordFile;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain monitoringFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PROMETHEUS_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("MONITORING"))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (csrfEnabled) {
            http.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .ignoringRequestMatchers(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, "/auth/login")));
        } else {
            http.csrf(AbstractHttpConfigurer::disable);
        }

        http
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

    @Bean
    public UserDetailsService monitoringUserDetailsService(PasswordEncoder passwordEncoder) {
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        String password = resolveMonitoringPassword();
        if (!monitoringUsername.isBlank() && !password.isBlank()) {
            users.createUser(User.withUsername(monitoringUsername)
                    .password(passwordEncoder.encode(password))
                    .roles("MONITORING")
                    .build());
        }
        return users;
    }

    private String resolveMonitoringPassword() {
        if (!monitoringPasswordFile.isBlank()) {
            try {
                return Files.readString(Path.of(monitoringPasswordFile)).trim();
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read monitoring scrape password file", exception);
            }
        }
        if (!monitoringPassword.isBlank()
                && !environment.acceptsProfiles(Profiles.of("test", "local"))) {
            throw new IllegalStateException(
                    "Inline monitoring scrape passwords are allowed only in test or local profiles");
        }
        return monitoringPassword;
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
