
package com.capacitix.config;

import com.capacitix.service.CustomUserDetailsService;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityConfig.class);

    private final CustomUserDetailsService userDetailsService;
    private final String rememberMeKey;
    private final boolean secureCookie;

    public SecurityConfig(
            CustomUserDetailsService userDetailsService,

            @Value("${capacitix.security.remember-me-key:capacitix-dev-only-change-me}")
            String rememberMeKey,

            @Value("${capacitix.security.cookie-secure:false}")
            boolean secureCookie
    ) {
        this.userDetailsService = userDetailsService;
        this.rememberMeKey = rememberMeKey;
        this.secureCookie = secureCookie;
    }

    // =========================================================
    // PASSWORD ENCODER
    // =========================================================

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // =========================================================
    // CSRF TOKEN REPOSITORY
    // =========================================================

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {

        CookieCsrfTokenRepository repository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();

        repository.setCookiePath("/");
        repository.setHeaderName("X-XSRF-TOKEN");

        // HTTPS protection in production.
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secureCookie)
                .sameSite("Lax")
        );

        return repository;
    }

    // =========================================================
    // SECURITY FILTER CHAIN
    // =========================================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository
    ) throws Exception {

        http

            // -------------------------------------------------
            // CSRF PROTECTION
            // -------------------------------------------------

            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(
                            new SpaCsrfTokenRequestHandler()
                    )
            )

            .addFilterAfter(
                    new CsrfCookieFilter(),
                    CsrfFilter.class
            )

            // -------------------------------------------------
            // SESSION MANAGEMENT
            // -------------------------------------------------

            .sessionManagement(session -> session
                    .sessionCreationPolicy(
                            SessionCreationPolicy.IF_REQUIRED
                    )
                    .sessionFixation(fixation ->
                            fixation.migrateSession()
                    )
            )

            // -------------------------------------------------
            // AUTHORIZATION
            // -------------------------------------------------

            .authorizeHttpRequests(auth -> auth

                    .dispatcherTypeMatchers(
                            DispatcherType.ERROR
                    ).permitAll()

                    .requestMatchers(
                            "/",
                            "/login",
                            "/error",
                            "/forgot-password",
                            "/verify-otp",
                            "/reset-password",
                            "/css/**",
                            "/js/**",
                            "/images/**",
                            "/favicon.ico"
                    ).permitAll()

                    .requestMatchers(
                            "/employee/accept-invitation",
                            "/employee/set-password",
                            "/employee/create-password"
                    ).permitAll()

                    .requestMatchers("/videos/**")
                    .authenticated()

                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")

                    .requestMatchers("/organization/**")
                    .hasRole("ORGANIZATION")

                    .requestMatchers("/trainer/**")
                    .hasRole("TRAINER")

                    .requestMatchers("/employee/**")
                    .hasRole("EMPLOYEE")

                    .anyRequest().authenticated()
            )

            .userDetailsService(userDetailsService)

            // -------------------------------------------------
            // LOGIN CONFIGURATION
            // -------------------------------------------------

            .formLogin(form -> form

                    .loginPage("/login")
                    .loginProcessingUrl("/login")

                    .usernameParameter("username")
                    .passwordParameter("password")

                    .successHandler((request, response, authentication) -> {

                        log.info(
                                "Login successful. User={}, Authorities={}",
                                authentication.getName(),
                                authentication.getAuthorities()
                        );

                        if (hasRole(authentication, "ROLE_ADMIN")) {

                            response.sendRedirect("/admin/dashboard");

                        } else if (hasRole(
                                authentication,
                                "ROLE_ORGANIZATION"
                        )) {

                            response.sendRedirect(
                                    "/organization/dashboard"
                            );

                        } else if (hasRole(
                                authentication,
                                "ROLE_TRAINER"
                        )) {

                            response.sendRedirect("/trainer/dashboard");

                        } else if (hasRole(
                                authentication,
                                "ROLE_EMPLOYEE"
                        )) {

                            response.sendRedirect("/employee/dashboard");

                        } else {

                            log.warn(
                                    "User {} has no recognized application role: {}",
                                    authentication.getName(),
                                    authentication.getAuthorities()
                            );

                            response.sendRedirect("/login?error=true");
                        }
                    })

                    .failureHandler((request, response, exception) -> {

                        log.warn(
                                "Login failed for username={}",
                                request.getParameter("username")
                        );

                        response.sendRedirect("/login?error=true");
                    })

                    .permitAll()
            )

            // -------------------------------------------------
            // REMEMBER ME
            // -------------------------------------------------

            .rememberMe(remember -> remember

                    .userDetailsService(userDetailsService)

                    .key(rememberMeKey)

                    .tokenValiditySeconds(
                            30 * 24 * 60 * 60
                    )

                    .rememberMeParameter("remember-me")

                    .rememberMeCookieName(
                            "CAPACITIX_REMEMBER_ME"
                    )

                    // HTTP locally; HTTPS in production.
                    .useSecureCookie(secureCookie)
            )

            // -------------------------------------------------
            // LOGOUT
            // -------------------------------------------------

            .logout(logout -> logout

                    .logoutUrl("/logout")

                    .logoutSuccessUrl("/login?logout=true")

                    .invalidateHttpSession(true)

                    .clearAuthentication(true)

                    .deleteCookies(
                            "JSESSIONID",
                            "CAPACITIX_REMEMBER_ME",
                            "XSRF-TOKEN"
                    )

                    .permitAll()
            )

            // -------------------------------------------------
            // EXCEPTION HANDLING
            // -------------------------------------------------

            .exceptionHandling(exceptions -> exceptions

                    .accessDeniedHandler((request, response, exception) -> {

                        Authentication authentication =
                                SecurityContextHolder.getContext()
                                        .getAuthentication();

                        boolean anonymous =
                                authentication == null
                                || authentication instanceof
                                AnonymousAuthenticationToken;

                        String username = anonymous
                                ? "anonymous"
                                : authentication.getName();

                        String authorities = anonymous
                                ? "none"
                                : authentication.getAuthorities().toString();

                        boolean csrfFailure =
                                exception instanceof CsrfException;

                        // Never log passwords, tokens, cookies or session IDs.
                        log.error(
                                "403 Forbidden: type={}, method={}, uri={}, "
                                        + "user={}, authorities={}, exception={}",
                                csrfFailure ? "CSRF" : "AUTHORIZATION",
                                request.getMethod(),
                                request.getRequestURI(),
                                username,
                                authorities,
                                exception.getClass().getSimpleName()
                        );

                        if (csrfFailure && anonymous) {

                            response.sendRedirect(
                                    "/login?expired=true"
                            );

                            return;
                        }

                        response.sendError(
                                HttpServletResponse.SC_FORBIDDEN,
                                csrfFailure
                                        ? "Security token missing or expired. "
                                          + "Reload the page and try again."
                                        : "Access denied."
                        );
                    })
            );

        return http.build();
    }

    // =========================================================
    // ROLE CHECK
    // =========================================================

    private boolean hasRole(
            Authentication authentication,
            String requiredAuthority
    ) {

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority()
                                .equals(requiredAuthority)
                );
    }

    // =========================================================
    // CSRF COOKIE FILTER
    // =========================================================

    private static final class CsrfCookieFilter
            extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

            CsrfToken csrfToken =
                    (CsrfToken) request.getAttribute("_csrf");

            if (csrfToken != null) {
                csrfToken.getToken();
            }

            filterChain.doFilter(request, response);
        }
    }

    // =========================================================
    // SPA CSRF REQUEST HANDLER
    // =========================================================

    private static final class SpaCsrfTokenRequestHandler
            extends CsrfTokenRequestAttributeHandler {

        private final CsrfTokenRequestHandler xor =
                new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(
                HttpServletRequest request,
                HttpServletResponse response,
                Supplier<CsrfToken> csrfToken
        ) {

            // Masked token for Thymeleaf.
            this.xor.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(
                HttpServletRequest request,
                CsrfToken csrfToken
        ) {

            String headerValue =
                    request.getHeader(csrfToken.getHeaderName());

            // Raw cookie token for JavaScript AJAX requests.
            if (StringUtils.hasText(headerValue)) {

                return super.resolveCsrfTokenValue(
                        request,
                        csrfToken
                );
            }

            // Masked token for Thymeleaf forms.
            return this.xor.resolveCsrfTokenValue(
                    request,
                    csrfToken
            );
        }
    }
}
