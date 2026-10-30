
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_user_email", columnList = "email"),
                @Index(name = "idx_user_role", columnList = "role"),
                @Index(name = "idx_user_trainer_id", columnList = "trainer_id"),
                @Index(name = "idx_user_employee_id", columnList = "employee_id"),
                @Index(name = "idx_user_department", columnList = "department"),
                @Index(name = "idx_user_designation", columnList = "designation"),
                @Index(name = "idx_user_assigned_trainer", columnList = "assigned_trainer_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"password", "trainer", "employees"})
public class User {

    // =========================================================
    // PRIMARY KEY
    // =========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================
    // BASIC USER INFORMATION
    // =========================================================

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String designation;


    // =========================================================
    // EMPLOYEE BUSINESS ID
    // =========================================================

    /*
     * Example:
     *
     * EMP0001
     * EMP0002
     *
     * Used only for EMPLOYEE accounts.
     */

    @Column(name = "employee_id", unique = true, length = 50)
    private String employeeId;


    // =========================================================
    // TRAINER BUSINESS ID
    // =========================================================

    /*
     * Example:
     *
     * TRN0001
     * TRN0002
     *
     * Used only for TRAINER accounts.
     */

    @Column(name = "trainer_id", unique = true, length = 50)
    private String trainerId;

    @Column(length = 200)
    private String expertise;


    // =========================================================
    // EMPLOYEE → TRAINER RELATIONSHIP
    // =========================================================

    /*
     * An EMPLOYEE can be assigned to one TRAINER.
     *
     * Database:
     *
     * users.assigned_trainer_id
     *             ↓
     *         users.id
     *
     * Example:
     *
     * Employee:
     *     id = 18
     *     role = EMPLOYEE
     *     assigned_trainer_id = 5
     *
     * Trainer:
     *     id = 5
     *     role = TRAINER
     *
     * Java:
     *
     * employee.getTrainer()
     *
     * returns the assigned trainer.
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "assigned_trainer_id",
            referencedColumnName = "id"
    )
    private User trainer;


    // =========================================================
    // TRAINER → EMPLOYEES RELATIONSHIP
    // =========================================================

    /*
     * Reverse side of the Employee → Trainer relationship.
     *
     * trainer.getEmployees()
     *
     * returns employees assigned to this trainer.
     *
     * This is useful when a trainer uploads a video and needs
     * to select one of his/her assigned employees.
     */

    @OneToMany(
            mappedBy = "trainer",
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<User> employees = new ArrayList<>();


    // =========================================================
    // LOGIN PASSWORD
    // =========================================================

    /*
     * Password must always be stored as a BCrypt encoded value.
     */

    @Column(nullable = false)
    private String password;


    // =========================================================
    // ROLE
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Role role = Role.EMPLOYEE;


    // =========================================================
    // ACCOUNT STATUS
    // =========================================================

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;


    // =========================================================
    // ROLE ENUM
    // =========================================================

    public enum Role {

        ADMIN,

        ORGANIZATION,

        TRAINER,

        EMPLOYEE
    }
}