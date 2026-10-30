
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "trainer_invitations",
        indexes = {
                @Index(name = "idx_invitation_token", columnList = "token"),
                @Index(name = "idx_invitation_email", columnList = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainerInvitation {

    /*
     * =========================================================
     * PRIMARY KEY
     * =========================================================
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /*
     * =========================================================
     * TRAINER INFORMATION
     * =========================================================
     */

    @Column(name = "full_name", nullable = false, length = 100)
    private String name;


    @Column(nullable = false, length = 150)
    private String email;


    @Column(length = 100)
    private String department;


    @Column(length = 200)
    private String expertise;


    @Column(name = "trainer_id", length = 50)
    private String trainerId;


    @Column(length = 15)
    private String phone;


    /*
     * =========================================================
     * INVITATION MESSAGE
     * =========================================================
     */

    @Column(columnDefinition = "TEXT")
    private String message;


    /*
     * =========================================================
     * SECURE INVITATION TOKEN
     * =========================================================
     *
     * A random token is generated when admin sends
     * the invitation.
     *
     * Example:
     *
     * http://localhost:8080/trainer/accept-invitation?token=...
     *
     * The token is NOT the trainer's password.
     */

    @Column(nullable = false, unique = true, length = 255)
    private String token;


    /*
     * =========================================================
     * TOKEN EXPIRATION
     * =========================================================
     *
     * Example:
     *
     * Invitation created:
     * 26 Sep 2026 21:30
     *
     * Expires:
     * 27 Sep 2026 21:30
     */

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;


    /*
     * =========================================================
     * INVITATION STATUS
     * =========================================================
     *
     * false = invitation not used yet
     * true  = trainer has already created password
     */

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;


    /*
     * =========================================================
     * HELPER METHOD
     * =========================================================
     *
     * Checks whether the invitation has expired.
     */

    public boolean isExpired() {

        return expiresAt == null
                || LocalDateTime.now().isAfter(expiresAt);
    }


    /*
     * =========================================================
     * HELPER METHOD
     * =========================================================
     *
     * Checks whether trainer can still use
     * this invitation.
     */

    public boolean isValid() {

        return !used && !isExpired();
    }
}

