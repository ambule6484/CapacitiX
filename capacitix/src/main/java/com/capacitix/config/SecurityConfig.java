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

    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            @Value("${capacitix.security.remember-me-key:capacitix-dev-only-change-me}")
            String rememberMeKey
    ) {
        this.userDetailsService = userDetailsService;
        this.rememberMeKey = rememberMeKey;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();

        repository.setCookiePath("/");
        repository.setHeaderName("X-XSRF-TOKEN");

        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository
    ) throws Exception {

        http
            // CSRF stays enabled. The SPA-style handler accepts:
            //  - the masked token from Thymeleaf hidden inputs (_csrf), and
            //  - the raw cookie value sent by JavaScript in X-XSRF-TOKEN.
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
            )

            // Spring Security 6+/7 loads the CSRF token lazily, so the
            // XSRF-TOKEN cookie is only written if something reads the token.
            // This filter forces it on every response, including right after
            // login/logout when the token is rotated.
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)

            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(fixation -> fixation.migrateSession())
            )

            .authorizeHttpRequests(auth -> auth

                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

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

                    .requestMatchers("/videos/**").authenticated()

                    .requestMatchers("/admin/**").hasRole("ADMIN")

                    .requestMatchers("/organization/**")
                    .hasRole("ORGANIZATION")

                    .requestMatchers("/trainer/**").hasRole("TRAINER")

                    .requestMatchers("/employee/**").hasRole("EMPLOYEE")

                    .anyRequest().authenticated()
            )

            .userDetailsService(userDetailsService)

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
                        } else if (hasRole(authentication, "ROLE_ORGANIZATION")) {
                            response.sendRedirect("/organization/dashboard");
                        } else if (hasRole(authentication, "ROLE_TRAINER")) {
                            response.sendRedirect("/trainer/dashboard");
                        } else if (hasRole(authentication, "ROLE_EMPLOYEE")) {
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
                                "Login failed for username={}. Reason={}",
                                request.getParameter("username"),
                                exception.getMessage()
                        );

                        response.sendRedirect("/login?error=true");
                    })

                    .permitAll()
            )

            .rememberMe(remember -> remember
                    .userDetailsService(userDetailsService)
                    .key(rememberMeKey)
                    .tokenValiditySeconds(30 * 24 * 60 * 60)
                    .rememberMeParameter("remember-me")
                    .rememberMeCookieName("CAPACITIX_REMEMBER_ME")
                    // Local HTTP only. Set to true when served over HTTPS.
                    .useSecureCookie(false)
            )

            // Logout must be a POST with a valid CSRF token.
            // A plain <a href="/logout"> will NOT log out; it will fail.
            .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID", "CAPACITIX_REMEMBER_ME")
                    .permitAll()
            )

            .exceptionHandling(exceptions -> exceptions
                    .accessDeniedHandler((request, response, exception) -> {

                        Authentication authentication =
                                SecurityContextHolder.getContext()
                                        .getAuthentication();

                        boolean anonymous = authentication == null
                                || authentication instanceof AnonymousAuthenticationToken;

                        String username = anonymous
                                ? "anonymous"
                                : authentication.getName();

                        String authorities = anonymous
                                ? "none"
                                : authentication.getAuthorities().toString();

                        boolean csrfFailure = exception instanceof CsrfException;

                        // Never log tokens, cookies or session ids.
                        log.error(
                                "403 Forbidden: type={}, method={}, uri={}, user={}, "
                                        + "authorities={}, exception={}, reason={}",
                                csrfFailure ? "CSRF" : "AUTHORIZATION",
                                request.getMethod(),
                                request.getRequestURI(),
                                username,
                                authorities,
                                exception.getClass().getSimpleName(),
                                exception.getMessage()
                        );

                        // Expired session / stale form: send the user to login
                        // instead of a dead-end 403 page.
                        if (csrfFailure && anonymous) {
                            response.sendRedirect("/login?expired=true");
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

    private boolean hasRole(
            Authentication authentication,
            String requiredAuthority
    ) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals(requiredAuthority)
                );
    }

    /**
     * Forces the deferred CSRF token to load so the XSRF-TOKEN cookie is
     * always present in the browser, even on pages that render no form.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

            CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
            if (csrfToken != null) {
                // Reading the value triggers the repository to write the cookie.
                csrfToken.getToken();
            }

            filterChain.doFilter(request, response);
        }
    }

    /**
     * Supports both Thymeleaf hidden inputs (masked token) and JavaScript
     * requests that send the raw cookie value in the header.
     */
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
            // Expose the masked token to Thymeleaf as ${_csrf}.
            this.xor.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(
                HttpServletRequest request,
                CsrfToken csrfToken
        ) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());

            return (StringUtils.hasText(headerValue)
                    ? super.resolveCsrfTokenValue(request, csrfToken)
                    : this.xor.resolveCsrfTokenValue(request, csrfToken));
        }
    }
}