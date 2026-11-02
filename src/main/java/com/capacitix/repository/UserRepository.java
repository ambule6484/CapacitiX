
package com.capacitix.repository;

import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /*
     * Fetch assigned trainer along with each employee.
     *
     * Required by admin/employees.html because the page accesses:
     * employee.trainer.fullName
     * employee.trainer.trainerId
     *
     * Prevents LazyInitializationException.
     */

    @EntityGraph(attributePaths = "trainer")
    List<User> findByRoleOrderByFullNameAsc(User.Role role);


    // =========================================================
    // TRAINER → EMPLOYEES
    // =========================================================

    List<User> findByTrainerAndRoleOrderByFullNameAsc(
            User trainer,
            User.Role role
    );


    // =========================================================
    // TRAINER → ENABLED EMPLOYEES
    // =========================================================

    List<User> findByTrainerAndRoleAndEnabledTrueOrderByFullNameAsc(
            User trainer,
            User.Role role
    );


    // =========================================================
    // FIND PARTICULAR EMPLOYEE ASSIGNED TO TRAINER
    // =========================================================

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
