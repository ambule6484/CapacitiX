
package com.capacitix.repository;

import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // =========================================================
    // BASIC USER SEARCH
    // =========================================================

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);


    // =========================================================
    // EMPLOYEE
    // =========================================================

    boolean existsByEmployeeId(String employeeId);

    Optional<User> findByEmployeeId(String employeeId);


    // =========================================================
    // TRAINER
    // =========================================================

    boolean existsByTrainerId(String trainerId);

    Optional<User> findByTrainerId(String trainerId);


    // =========================================================
    // ROLE
    // =========================================================

    List<User> findByRole(User.Role role);

    List<User> findByRoleOrderByFullNameAsc(User.Role role);


    // =========================================================
    // TRAINER → EMPLOYEES
    // =========================================================

    /*
     * Returns only EMPLOYEE users assigned to the given TRAINER.
     *
     * This is important for video assignment.
     *
     * Example:
     *
     * Trainer = Omkar Pawar
     *
     * Result:
     *     Prashant Ambule
     *     Employee 2
     *     Employee 3
     *
     * Other trainers' employees are NOT returned.
     */

    List<User> findByTrainerAndRoleOrderByFullNameAsc(
            User trainer,
            User.Role role
    );


    // =========================================================
    // TRAINER → ENABLED EMPLOYEES
    // =========================================================

    /*
     * Returns only active employees assigned to the trainer.
     *
     * Useful when the trainer is assigning a video.
     */

    List<User> findByTrainerAndRoleAndEnabledTrueOrderByFullNameAsc(
            User trainer,
            User.Role role
    );


    // =========================================================
    // FIND PARTICULAR EMPLOYEE ASSIGNED TO TRAINER
    // =========================================================

    /*
     * Used as an additional security check.
     *
     * Before assigning a video:
     *
     *     trainer → employee
     *
     * the service can verify that the employee actually
     * belongs to that trainer.
     */

    Optional<User> findByIdAndTrainerAndRole(
            Long id,
            User trainer,
            User.Role role
    );


    // =========================================================
    // TRAINER → EMPLOYEE COUNT
    // =========================================================

    long countByTrainerAndRole(
            User trainer,
            User.Role role
    );

    long countByTrainerAndRoleAndEnabled(
            User trainer,
            User.Role role,
            boolean enabled
    );


    // =========================================================
    // ROLE COUNTS
    // =========================================================

    long countByRole(User.Role role);

    long countByRoleAndEnabled(
            User.Role role,
            boolean enabled
    );


    // =========================================================
    // DEPARTMENT
    // =========================================================

    long countByRoleAndDepartment(
            User.Role role,
            String department
    );

    long countByRoleAndDepartmentAndEnabled(
            User.Role role,
            String department,
            boolean enabled
    );
}