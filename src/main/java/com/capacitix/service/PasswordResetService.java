package com.capacitix.service;

import com.capacitix.entity.PasswordResetOtp;
import com.capacitix.entity.User;
import com.capacitix.repository.PasswordResetOtpRepository;
import com.capacitix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    // OTP validity: 10 minutes
    private static final int OTP_EXPIRY_MINUTES = 10;

    // =========================================================
    // SEND OTP
    // =========================================================

    @Transactional
    public boolean sendOtp(String email) {

        email = normalizeEmail(email);

        // Check whether user exists
        Optional<User> userOptional = userRepository.findByEmail(email);

        /*
         * Do not reveal whether an email exists.
         *
         * The controller will show a generic message.
         */
        if (userOptional.isEmpty()) {
            return true;
        }

        User user = userOptional.get();

        // -----------------------------------------------------
        // Delete previous OTPs for this email
        // -----------------------------------------------------

        passwordResetOtpRepository.deleteByEmail(email);

        // -----------------------------------------------------
        // Generate a secure 6-digit OTP
        // -----------------------------------------------------

        String otp = generateOtp();

        // -----------------------------------------------------
        // Create OTP entity
        // -----------------------------------------------------

        PasswordResetOtp passwordResetOtp = PasswordResetOtp.builder()
                .email(email)
                .otp(otp)
                .expiryTime(
                        LocalDateTime.now()
                                .plusMinutes(OTP_EXPIRY_MINUTES)
                )
                .verified(false)
                .createdAt(LocalDateTime.now())
                .build();

        // -----------------------------------------------------
        // Save OTP
        // -----------------------------------------------------

        passwordResetOtpRepository.save(passwordResetOtp);

        // -----------------------------------------------------
        // Send OTP to user's email
        // -----------------------------------------------------

        try {

            emailService.sendPasswordResetOtp(
                    user.getFullName(),
                    email,
                    otp
            );

            return true;

        } catch (Exception e) {

            /*
             * If email sending fails, remove the OTP that was
             * just created so that an unusable OTP is not left
             * in the database.
             */

            passwordResetOtpRepository.delete(passwordResetOtp);

            throw new RuntimeException(
                    "Unable to send password reset OTP.",
                    e
            );
        }
    }

    // =========================================================
    // VERIFY OTP
    // =========================================================

    @Transactional
    public boolean verifyOtp(String email, String otp) {

        email = normalizeEmail(email);
        otp = otp.trim();

        Optional<PasswordResetOtp> otpOptional =
                passwordResetOtpRepository
                        .findByEmailAndOtpAndVerifiedFalse(
                                email,
                                otp
                        );

        // OTP not found
        if (otpOptional.isEmpty()) {
            return false;
        }

        PasswordResetOtp passwordResetOtp = otpOptional.get();

        // -----------------------------------------------------
        // Check OTP expiry
        // -----------------------------------------------------

        if (passwordResetOtp.getExpiryTime()
                .isBefore(LocalDateTime.now())) {

            passwordResetOtpRepository.delete(passwordResetOtp);

            return false;
        }

        // -----------------------------------------------------
        // Mark OTP as verified
        // -----------------------------------------------------

        passwordResetOtp.setVerified(true);

        passwordResetOtpRepository.save(passwordResetOtp);

        return true;
    }

    // =========================================================
    // RESET PASSWORD
    // =========================================================

    @Transactional
    public boolean resetPassword(
            String email,
            String newPassword) {

        email = normalizeEmail(email);

        // -----------------------------------------------------
        // Find user
        // -----------------------------------------------------

        Optional<User> userOptional =
                userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return false;
        }

        User user = userOptional.get();

        // -----------------------------------------------------
        // Make sure OTP was verified
        // -----------------------------------------------------

        Optional<PasswordResetOtp> otpOptional =
                passwordResetOtpRepository
                        .findTopByEmailOrderByCreatedAtDesc(email);

        if (otpOptional.isEmpty()) {
            return false;
        }

        PasswordResetOtp passwordResetOtp = otpOptional.get();

        // OTP must have been verified
        if (!passwordResetOtp.isVerified()) {
            return false;
        }

        // -----------------------------------------------------
        // Check that verification is not expired
        // -----------------------------------------------------

        if (passwordResetOtp.getExpiryTime()
                .isBefore(LocalDateTime.now())) {

            passwordResetOtpRepository.delete(passwordResetOtp);

            return false;
        }

        // -----------------------------------------------------
        // Encrypt new password
        // -----------------------------------------------------

        String encodedPassword =
                passwordEncoder.encode(newPassword);

        user.setPassword(encodedPassword);

        // -----------------------------------------------------
        // Save updated user
        // -----------------------------------------------------

        userRepository.save(user);

        // -----------------------------------------------------
        // Delete OTP after successful password reset
        // -----------------------------------------------------

        passwordResetOtpRepository.delete(passwordResetOtp);

        return true;
    }

    // =========================================================
    // GENERATE 6-DIGIT OTP
    // =========================================================

    private String generateOtp() {

        int otpNumber =
                100000 + secureRandom.nextInt(900000);

        return String.valueOf(otpNumber);
    }

    // =========================================================
    // NORMALIZE EMAIL
    // =========================================================

    private String normalizeEmail(String email) {

        if (email == null) {
            return "";
        }

        return email.trim().toLowerCase();
    }
}