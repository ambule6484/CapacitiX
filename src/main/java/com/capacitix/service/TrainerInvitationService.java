package com.capacitix.service;

import com.capacitix.entity.TrainerInvitation;
import com.capacitix.entity.User;
import com.capacitix.repository.TrainerInvitationRepository;
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
public class TrainerInvitationService {

    private final TrainerInvitationRepository trainerInvitationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;


    /*
     * =========================================================
     * ADMIN - CREATE TRAINER DIRECTLY
     * =========================================================
     *
     * Used by:
     *
     * POST /admin/trainers/add
     *
     * Admin provides:
     * - Name
     * - Trainer ID
     * - Email
     * - Department
     * - Expertise
     * - Phone
     * - Password
     *
     * The trainer account is created immediately.
     */
    @Transactional
    public void createTrainer(
            String name,
            String trainerId,
            String email,
            String department,
            String expertise,
            String phone,
            String password,
            String role) {

        /*
         * ============================
         * CLEAN INPUT
         * ============================
         */

        name = name != null ? name.trim() : "";
        trainerId = trainerId != null ? trainerId.trim() : "";
        email = email != null
                ? email.trim().toLowerCase()
                : "";
        department = department != null
                ? department.trim()
                : "";
        expertise = expertise != null
                ? expertise.trim()
                : "";
        phone = phone != null
                ? phone.trim()
                : "";


        /*
         * ============================
         * BASIC VALIDATION
         * ============================
         */

        if (name.isEmpty()) {
            throw new RuntimeException(
                    "Trainer name is required."
            );
        }

        if (trainerId.isEmpty()) {
            throw new RuntimeException(
                    "Trainer ID is required."
            );
        }

        if (email.isEmpty()) {
            throw new RuntimeException(
                    "Trainer email is required."
            );
        }

        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException(
                    "Password is required."
            );
        }

        if (password.length() < 6) {
            throw new RuntimeException(
                    "Password must contain at least 6 characters."
            );
        }


        /*
         * ============================
         * CHECK EXISTING EMAIL
         * ============================
         */

        if (userRepository.existsByEmail(email)) {

            throw new RuntimeException(
                    "A user with this email already exists."
            );
        }


        /*
         * ============================
         * CREATE TRAINER USER
         * ============================
         *
         * Password is encoded using BCrypt.
         *
         * NEVER save the plain-text password.
         */

        User trainer =
                User.builder()
                        .fullName(name)
                        .email(email)
                        .phoneNumber(
                                phone.isEmpty()
                                        ? null
                                        : phone
                        )
                        .department(
                                department.isEmpty()
                                        ? null
                                        : department
                        )
                        .designation("Trainer")
                        .password(
                                passwordEncoder.encode(password)
                        )
                        .role(User.Role.TRAINER)
                        .enabled(true)
                        .build();


        /*
         * ============================
         * SAVE TRAINER
         * ============================
         */

        userRepository.save(trainer);
    }


    /*
     * =========================================================
     * SEND TRAINER INVITATION
     * =========================================================
     *
     * This method is kept for your OLD invitation system.
     *
     * It can still be used when you want:
     *
     * Admin → Send Invitation
     * Trainer → Receive email
     * Trainer → Create password
     */
    @Transactional
    public void sendInvitation(
            String name,
            String email,
            String department,
            String expertise,
            String trainerId,
            String phone,
            String message) {

        email = email.trim().toLowerCase();


        /*
         * Check existing user.
         */

        if (userRepository.existsByEmail(email)) {

            throw new RuntimeException(
                    "A user with this email already exists."
            );
        }


        /*
         * Check existing active invitation.
         */

        var existingInvitation =
                trainerInvitationRepository.findByEmail(email);

        if (existingInvitation.isPresent()
                && existingInvitation.get().isValid()) {

            throw new RuntimeException(
                    "An active invitation has already been sent to this email."
            );
        }


        /*
         * Generate secure token.
         */

        String token =
                UUID.randomUUID().toString();


        /*
         * Invitation valid for 24 hours.
         */

        LocalDateTime expiresAt =
                LocalDateTime.now().plusHours(24);


        /*
         * Create invitation.
         */

        TrainerInvitation invitation =
                TrainerInvitation.builder()
                        .name(name)
                        .email(email)
                        .department(department)
                        .expertise(expertise)
                        .trainerId(trainerId)
                        .phone(phone)
                        .message(message)
                        .token(token)
                        .expiresAt(expiresAt)
                        .used(false)
                        .build();


        /*
         * Save invitation.
         */

        trainerInvitationRepository.save(invitation);


        /*
         * Create invitation link.
         */

        String invitationLink =
                baseUrl
                        + "/trainer/accept-invitation?token="
                        + token;


        /*
         * Send email.
         */

        emailService.sendTrainerInvitation(
                email,
                name,
                invitationLink,
                message
        );
    }


    /*
     * =========================================================
     * VALIDATE INVITATION
     * =========================================================
     */

    @Transactional(readOnly = true)
    public TrainerInvitation validateInvitation(
            String token) {

        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        var invitation =
                trainerInvitationRepository.findByToken(
                        token.trim()
                );

        if (invitation.isEmpty()) {
            return null;
        }

        TrainerInvitation trainerInvitation =
                invitation.get();

        if (!trainerInvitation.isValid()) {
            return null;
        }

        return trainerInvitation;
    }


    /*
     * =========================================================
     * TRAINER - SET PASSWORD
     * =========================================================
     *
     * Used by the OLD email invitation flow.
     */
    @Transactional
    public boolean setTrainerPassword(
            String token,
            String password) {

        var invitationOptional =
                trainerInvitationRepository.findByToken(token);


        if (invitationOptional.isEmpty()) {
            return false;
        }


        TrainerInvitation invitation =
                invitationOptional.get();


        if (!invitation.isValid()) {
            return false;
        }


        if (password == null || password.trim().isEmpty()) {
            return false;
        }


        if (password.length() < 6) {
            return false;
        }


        /*
         * Check whether account already exists.
         */

        if (userRepository.existsByEmail(
                invitation.getEmail())) {

            return false;
        }


        /*
         * Create trainer account.
         */

        User trainer =
                User.builder()
                        .fullName(invitation.getName())
                        .email(invitation.getEmail())
                        .phoneNumber(invitation.getPhone())
                        .department(invitation.getDepartment())
                        .designation("Trainer")
                        .password(
                                passwordEncoder.encode(password)
                        )
                        .role(User.Role.TRAINER)
                        .enabled(true)
                        .build();


        /*
         * Save trainer.
         */

        userRepository.save(trainer);


        /*
         * Mark invitation as used.
         */

        invitation.setUsed(true);

        trainerInvitationRepository.save(invitation);


        return true;
    }
}