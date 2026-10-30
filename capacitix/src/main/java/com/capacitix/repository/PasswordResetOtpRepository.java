package com.capacitix.repository;

import com.capacitix.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    // =========================================================
    // FIND LATEST OTP BY EMAIL
    // =========================================================
    Optional<PasswordResetOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    // =========================================================
    // FIND VALID OTP
    // =========================================================
    Optional<PasswordResetOtp> findByEmailAndOtpAndVerifiedFalse(
            String email,
            String otp
    );

    // =========================================================
    // DELETE OLD OTPs OF USER
    // =========================================================
    void deleteByEmail(String email);

    // =========================================================
    // DELETE EXPIRED OTPs
    // =========================================================
    void deleteByExpiryTimeBefore(LocalDateTime time);
}