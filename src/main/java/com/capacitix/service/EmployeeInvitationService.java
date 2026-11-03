package com.capacitix.service;

import com.capacitix.entity.EmployeeInvitation;
import com.capacitix.entity.User;
import com.capacitix.repository.EmployeeInvitationRepository;
import com.capacitix.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeInvitationService {

    private final UserRepository userRepository;

    private final EmployeeInvitationRepository employeeInvitationRepository;

    private final EmailService emailService;

    private final PasswordEncoder passwordEncoder;


    // =========================================================
    // BASE URL
    // =========================================================
    /*
     * application.properties:
     *
     * app.base-url=http://localhost:8080
     *
     * IMPORTANT:
     * We never allow baseUrl to become "null".
     */

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;


    // =========================================================
    // CREATE EMPLOYEE + SEND INVITATION
    // =========================================================

    @Transactional
    public void createEmployeeAndSendInvitation(
            String name,
            String email,
            String department,
            String designation,
            String employeeId,
            String phone,
            String message) {

        // =====================================================
        // CLEAN INPUT
        // =====================================================

        name = cleanRequired(
                name,
                "Employee name is required."
        );

        email = cleanRequired(
                email,
                "Employee email is required."
        ).toLowerCase();

        employeeId = cleanRequired(
                employeeId,
                "Employee ID is required."
        );

        department = cleanOptional(department);
        designation = cleanOptional(designation);
        phone = cleanOptional(phone);
        message = cleanOptional(message);


        // =====================================================
        // CHECK DUPLICATE EMAIL
        // =====================================================

        if (userRepository.existsByEmail(email)) {

            throw new IllegalArgumentException(
                    "An account with this email already exists."
            );
        }


        // =====================================================
        // CHECK DUPLICATE EMPLOYEE ID
        // =====================================================

        if (userRepository.existsByEmployeeId(employeeId)) {

            throw new IllegalArgumentException(
                    "Employee ID already exists."
            );
        }


        // =====================================================
        // GENERATE UNIQUE INVITATION TOKEN
        // =====================================================

        String token;

        do {
            token = UUID.randomUUID().toString();
        }
        while (employeeInvitationRepository.existsByToken(token));


        // =====================================================
        // CREATE TEMPORARY PASSWORD
        // =====================================================
        /*
         * User.password is nullable=false.
         *
         * Therefore we cannot save NULL.
         *
         * A random temporary password is generated and encoded.
         *
         * This password is NOT sent to the employee.
         *
         * Employee cannot login because enabled=false.
         *
         * Employee must create their own password using the
         * invitation link.
         */

        String temporaryPassword =
                UUID.randomUUID().toString();

        String encodedPassword =
                passwordEncoder.encode(temporaryPassword);


        // =====================================================
        // CREATE EMPLOYEE USER
        // =====================================================

        User employee =
                User.builder()

                        .fullName(name)

                        .email(email)

                        .phoneNumber(phone)

                        .department(department)

                        .designation(designation)

                        .employeeId(employeeId)

                        .password(encodedPassword)

                        .role(User.Role.EMPLOYEE)

                        /*
                         * Employee cannot login until they
                         * create their own password.
                         */
                        .enabled(false)

                        .build();


        userRepository.save(employee);


        // =====================================================
        // CREATE EMPLOYEE INVITATION
        // =====================================================

        EmployeeInvitation invitation =
                EmployeeInvitation.builder()

                        .name(name)

                        .email(email)

                        .department(department)

                        .designation(designation)

                        .employeeId(employeeId)

                        .phone(phone)

                        .message(message)

                        .token(token)

                        /*
                         * Invitation valid for 24 hours.
                         */
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusHours(24)
                        )

                        .used(false)

                        .build();


        employeeInvitationRepository.save(invitation);


        // =====================================================
        // CREATE SAFE BASE URL
        // =====================================================

        String finalBaseUrl = getBaseUrl();


        // =====================================================
        // CREATE INVITATION LINK
        // =====================================================

        String invitationLink =
                finalBaseUrl
                        + "/employee/accept-invitation?token="
                        + token;


        // =====================================================
        // PRINT LINK IN CONSOLE
        // =====================================================
        /*
         * This is useful for debugging.
         *
         * When admin sends an invitation, console should show:
         *
         * EMPLOYEE INVITATION LINK:
         * http://localhost:8080/employee/accept-invitation?token=xxxx
         */

        System.out.println();
        System.out.println("=================================================");
        System.out.println("EMPLOYEE INVITATION CREATED");
        System.out.println("Employee : " + name);
        System.out.println("Email    : " + email);
        System.out.println("Token    : " + token);
        System.out.println("Base URL : " + finalBaseUrl);
        System.out.println("INVITATION LINK:");
        System.out.println(invitationLink);
        System.out.println("=================================================");
        System.out.println();


        // =====================================================
        // SEND EMAIL
        // =====================================================

        emailService.sendEmployeeInvitation(
                email,
                name,
                invitationLink,
                message
        );
    }


    // =========================================================
    // GET VALID INVITATION
    // =========================================================

    public EmployeeInvitation getValidInvitation(
            String token) {

        // =====================================================
        // CHECK TOKEN
        // =====================================================

        if (token == null ||
                token.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Invitation token is missing."
            );
        }


        String cleanToken =
                token.trim();


        // =====================================================
        // FIND INVITATION
        // =====================================================

        EmployeeInvitation invitation =
                employeeInvitationRepository
                        .findByToken(cleanToken)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid invitation link."
                                )
                        );


        // =====================================================
        // CHECK USED
        // =====================================================

        if (invitation.isUsed()) {

            throw new IllegalArgumentException(
                    "This invitation has already been used."
            );
        }


        // =====================================================
        // CHECK EXPIRATION
        // =====================================================

        if (invitation.isExpired()) {

            throw new IllegalArgumentException(
                    "This invitation has expired."
            );
        }


        return invitation;
    }


    // =========================================================
    // ACCEPT INVITATION + CREATE PASSWORD
    // =========================================================

    @Transactional
    public void acceptInvitation(
            String token,
            String password,
            String confirmPassword) {

        // =====================================================
        // PASSWORD REQUIRED
        // =====================================================

        if (password == null ||
                password.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Password is required."
            );
        }


        // =====================================================
        // CONFIRM PASSWORD REQUIRED
        // =====================================================

        if (confirmPassword == null ||
                confirmPassword.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Please confirm your password."
            );
        }


        // =====================================================
        // MINIMUM PASSWORD LENGTH
        // =====================================================

        if (password.length() < 6) {

            throw new IllegalArgumentException(
                    "Password must contain at least 6 characters."
            );
        }


        // =====================================================
        // PASSWORD MATCH
        // =====================================================

        if (!password.equals(confirmPassword)) {

            throw new IllegalArgumentException(
                    "Passwords do not match."
            );
        }


        // =====================================================
        // GET VALID INVITATION
        // =====================================================

        EmployeeInvitation invitation =
                getValidInvitation(token);


        // =====================================================
        // FIND EMPLOYEE ACCOUNT
        // =====================================================

        User employee =
                userRepository
                        .findByEmail(
                                invitation.getEmail()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Employee account not found."
                                )
                        );


        // =====================================================
        // CHECK ROLE
        // =====================================================

        if (employee.getRole()
                != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Invalid employee account."
            );
        }


        // =====================================================
        // SET NEW PASSWORD
        // =====================================================

        String encodedPassword =
                passwordEncoder.encode(password);

        employee.setPassword(encodedPassword);


        // =====================================================
        // ENABLE EMPLOYEE ACCOUNT
        // =====================================================

        employee.setEnabled(true);


        // =====================================================
        // SAVE EMPLOYEE
        // =====================================================

        userRepository.save(employee);


        // =====================================================
        // MARK INVITATION AS USED
        // =====================================================

        invitation.setUsed(true);

        employeeInvitationRepository.save(
                invitation
        );
    }


    // =========================================================
    // GET SAFE BASE URL
    // =========================================================

    private String getBaseUrl() {

        /*
         * If Spring cannot read app.base-url,
         * use localhost automatically.
         */

        if (baseUrl == null ||
                baseUrl.trim().isEmpty() ||
                baseUrl.trim().equalsIgnoreCase("null")) {

            return "http://localhost:8080";
        }


        /*
         * Remove spaces and trailing /
         *
         * Example:
         *
         * http://localhost:8080/
         *
         * becomes:
         *
         * http://localhost:8080
         */

        return baseUrl
                .trim()
                .replaceAll("/+$", "");
    }


    // =========================================================
    // CLEAN OPTIONAL VALUE
    // =========================================================

    private String cleanOptional(
            String value) {

        if (value == null ||
                value.trim().isEmpty()) {

            return null;
        }

        return value.trim();
    }


    // =========================================================
    // CLEAN REQUIRED VALUE
    // =========================================================

    private String cleanRequired(
            String value,
            String errorMessage) {

        if (value == null ||
                value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }

        return value.trim();
    }
}