package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================
    // USER EMAIL
    // =========================================================

    @Column(nullable = false)
    private String email;

    // =========================================================
    // OTP
    // =========================================================

    @Column(nullable = false, length = 6)
    private String otp;

    // =========================================================
    // OTP EXPIRY TIME
    // =========================================================

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    // =========================================================
    // OTP USED STATUS
    // =========================================================

    @Column(nullable = false)
    private boolean verified = false;

    // =========================================================
    // CREATED TIME
    // =========================================================

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // =========================================================
    // BEFORE INSERT
    // =========================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}