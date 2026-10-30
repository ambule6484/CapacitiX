package com.capacitix.service;

import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // =========================================================
    // LOAD USER FOR SPRING SECURITY LOGIN
    // =========================================================

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        if (email == null || email.trim().isEmpty()) {

            throw new UsernameNotFoundException(
                    "Email is required for login"
            );
        }

        String loginEmail = email.trim();

        User user = userRepository
                .findByEmail(loginEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found with email: " + loginEmail
                        )
                );

        /*
         * Current CapacitiX roles:
         *
         * ADMIN
         * ORGANIZATION
         * TRAINER
         * EMPLOYEE
         */
        User.Role role = user.getRole();

        if (role == null) {

            throw new UsernameNotFoundException(
                    "User role is not configured: " + loginEmail
            );
        }

        /*
         * Spring Security expects authorities such as:
         *
         * ROLE_ADMIN
         * ROLE_ORGANIZATION
         * ROLE_TRAINER
         * ROLE_EMPLOYEE
         */
        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority(
                        "ROLE_" + role.name()
                );

        /*
         * The 'enabled' value comes from the users table.
         *
         * If Admin disables an employee/trainer,
         * Spring Security will prevent that account from logging in.
         */
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(List.of(authority))
                .disabled(!user.isEnabled())
                .build();
    }
}