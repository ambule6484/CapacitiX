package com.capacitix.config;

import com.capacitix.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;


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
    public CsrfTokenRepository csrfTokenRepository() {

        CookieCsrfTokenRepository repository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();

        /*
         * Cookie:
         *
         * XSRF-TOKEN
         *
         * JavaScript can read this cookie and send the token
         * through the X-XSRF-TOKEN request header.
         */
        repository.setCookiePath("/");

        /*
         * Explicitly use the standard Spring Security
         * CSRF request header.
         */
        repository.setHeaderName("X-XSRF-TOKEN");

        return repository;
    }


    // =========================================================
    // SECURITY FILTER CHAIN
    // =========================================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository
    ) throws Exception {

        /*
         * Use request attribute handler so the CSRF token is
         * properly made available during requests.
         */
        CsrfTokenRequestAttributeHandler csrfRequestHandler =
                new CsrfTokenRequestAttributeHandler();


        http

                // =================================================
                // CSRF
                // =================================================

                .csrf(csrf -> csrf

                        .csrfTokenRepository(
                                csrfTokenRepository
                        )

                        .csrfTokenRequestHandler(
                                csrfRequestHandler
                        )

                        /*
                         * Logout currently does not contain a CSRF
                         * hidden field in the existing application.
                         */
                        .ignoringRequestMatchers(
                                "/logout"
                        )
                )


                // =================================================
                // AUTHORIZATION
                // =================================================

                .authorizeHttpRequests(auth -> auth

                        // =================================================
                        // PUBLIC
                        // =================================================

                        .requestMatchers(
                                "/",
                                "/login",

                                "/forgot-password",
                                "/verify-otp",
                                "/reset-password",

                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico"
                        ).permitAll()


                        // =================================================
                        // EMPLOYEE INVITATION
                        // =================================================

                        .requestMatchers(
                                "/employee/accept-invitation",
                                "/employee/set-password",
                                "/employee/create-password"
                        ).permitAll()


                        // =================================================
                        // VIDEO
                        // =================================================

                        .requestMatchers(
                                "/videos/**"
                        ).authenticated()


                        // =================================================
                        // ADMIN
                        // =================================================

                        .requestMatchers(
                                "/admin/**"
                        ).hasRole("ADMIN")


                        // =================================================
                        // ORGANIZATION
                        // =================================================

                        .requestMatchers(
                                "/organization/**"
                        ).hasRole("ORGANIZATION")


                        // =================================================
                        // TRAINER
                        // =================================================

                        .requestMatchers(
                                "/trainer/**"
                        ).hasRole("TRAINER")


                        // =================================================
                        // EMPLOYEE
                        // =================================================

                        .requestMatchers(
                                "/employee/**"
                        ).hasRole("EMPLOYEE")


                        // =================================================
                        // EVERYTHING ELSE
                        // =================================================

                        .anyRequest().authenticated()
                )


                // =================================================
                // USER DETAILS
                // =================================================

                .userDetailsService(
                        userDetailsService
                )


                // =================================================
                // FORM LOGIN
                // =================================================

                .formLogin(form -> form

                        .loginPage("/login")

                        .loginProcessingUrl("/login")


                        // =================================================
                        // LOGIN SUCCESS
                        // =================================================

                        .successHandler(
                                (request, response, authentication) -> {

                                    boolean isAdmin =
                                            authentication
                                                    .getAuthorities()
                                                    .stream()
                                                    .anyMatch(authority ->
                                                            authority
                                                                    .getAuthority()
                                                                    .equals("ROLE_ADMIN")
                                                    );


                                    boolean isOrganization =
                                            authentication
                                                    .getAuthorities()
                                                    .stream()
                                                    .anyMatch(authority ->
                                                            authority
                                                                    .getAuthority()
                                                                    .equals("ROLE_ORGANIZATION")
                                                    );


                                    boolean isTrainer =
                                            authentication
                                                    .getAuthorities()
                                                    .stream()
                                                    .anyMatch(authority ->
                                                            authority
                                                                    .getAuthority()
                                                                    .equals("ROLE_TRAINER")
                                                    );


                                    boolean isEmployee =
                                            authentication
                                                    .getAuthorities()
                                                    .stream()
                                                    .anyMatch(authority ->
                                                            authority
                                                                    .getAuthority()
                                                                    .equals("ROLE_EMPLOYEE")
                                                    );


                                    // =================================================
                                    // ADMIN
                                    // =================================================

                                    if (isAdmin) {

                                        response.sendRedirect(
                                                "/admin/dashboard"
                                        );

                                        return;
                                    }


                                    // =================================================
                                    // ORGANIZATION
                                    // =================================================

                                    if (isOrganization) {

                                        response.sendRedirect(
                                                "/organization/dashboard"
                                        );

                                        return;
                                    }


                                    // =================================================
                                    // TRAINER
                                    // =================================================

                                    if (isTrainer) {

                                        response.sendRedirect(
                                                "/trainer/dashboard"
                                        );

                                        return;
                                    }


                                    // =================================================
                                    // EMPLOYEE
                                    // =================================================

                                    if (isEmployee) {

                                        response.sendRedirect(
                                                "/employee/dashboard"
                                        );

                                        return;
                                    }


                                    // =================================================
                                    // UNKNOWN ROLE
                                    // =================================================

                                    response.sendRedirect(
                                            "/login?error=true"
                                    );
                                }
                        )


                        // =================================================
                        // LOGIN FAILURE
                        // =================================================

                        .failureUrl(
                                "/login?error=true"
                        )

                        .permitAll()
                )


                // =================================================
                // REMEMBER ME
                // =================================================

                .rememberMe(remember -> remember

                        .userDetailsService(
                                userDetailsService
                        )

                        .key(
                                "capacitix-remember-me-secret-key-2026"
                        )

                        .tokenValiditySeconds(
                                30 * 24 * 60 * 60
                        )

                        .rememberMeParameter(
                                "remember-me"
                        )

                        .rememberMeCookieName(
                                "CAPACITIX_REMEMBER_ME"
                        )

                        .useSecureCookie(false)
                )


                // =================================================
                // LOGOUT
                // =================================================

                .logout(logout -> logout

                        .logoutUrl(
                                "/logout"
                        )

                        .logoutSuccessUrl(
                                "/login?logout=true"
                        )

                        .invalidateHttpSession(
                                true
                        )

                        .clearAuthentication(
                                true
                        )

                        .deleteCookies(
                                "JSESSIONID",
                                "CAPACITIX_REMEMBER_ME",
                                "XSRF-TOKEN"
                        )

                        .permitAll()
                );


        // =========================================================
        // BUILD
        // =========================================================

        return http.build();
    }
}