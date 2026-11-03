
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "employee_invitations",
        indexes = {
                @Index(name = "idx_employee_inv_token", columnList = "token"),
                @Index(name = "idx_employee_inv_email", columnList = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeInvitation {

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
     * EMPLOYEE INFORMATION
     * =========================================================
     */

    @Column(name = "full_name", nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String designation;

    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

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
     */

    @Column(nullable = false, unique = true, length = 255)
    private String token;


    /*
     * =========================================================
     * TOKEN EXPIRATION
     * =========================================================
     */

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;


    /*
     * =========================================================
     * INVITATION STATUS
     * =========================================================
     *
     * false = employee has not created password yet
     * true  = employee has created password
     */

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;


    /*
     * =========================================================
     * EXPIRATION CHECK
     * =========================================================
     */

    public boolean isExpired() {

        return expiresAt == null
                || LocalDateTime.now().isAfter(expiresAt);
    }


    /*
     * =========================================================
     * VALID INVITATION CHECK
     * =========================================================
     */

    public boolean isValid() {

        return !used && !isExpired();
    }
}