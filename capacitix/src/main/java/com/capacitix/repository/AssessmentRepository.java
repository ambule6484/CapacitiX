package com.capacitix.repository;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentRepository
        extends JpaRepository<Assessment, Long> {

    /*
     * =========================================================
     * ALL ASSESSMENTS
     * =========================================================
     */

    @EntityGraph(attributePaths = "employee")
    List<Assessment> findAllByOrderByCreatedAtDesc();


    /*
     * =========================================================
     * EMPLOYEE ASSESSMENTS
     * =========================================================
     */

    @EntityGraph(attributePaths = "employee")
    List<Assessment> findByEmployeeOrderByCreatedAtDesc(
            User employee
    );


    @EntityGraph(attributePaths = "employee")
    List<Assessment> findByEmployee_IdOrderByCreatedAtDesc(
            Long employeeId
    );


    /*
     * =========================================================
     * AI GENERATED ASSESSMENTS
     * =========================================================
     */

    /**
     * Returns all AI-generated assessments.
     *
     * Used for admin/general AI assessment views.
     */
    @EntityGraph(attributePaths = "employee")
    List<Assessment> findByAiGeneratedTrueOrderByCreatedAtDesc();


    /**
     * Returns AI-generated assessments which are unassigned.
     *
     * Conditions:
     *
     * ai_generated = true
     * employee_id  IS NULL
     *
     * Kept for backward compatibility with existing services/controllers.
     */
    @EntityGraph(attributePaths = "employee")
    List<Assessment>
    findByAiGeneratedTrueAndEmployeeIsNullOrderByCreatedAtDesc();


    /**
     * Returns AI-generated assessments that are actually ready
     * for trainer assignment.
     *
     * Conditions:
     *
     * ai_generated = true
     * status       = supplied status
     * employee_id  IS NULL
     *
     * Trainer Assessment Center should use:
     *
     * status = "GENERATED"
     */
    @EntityGraph(attributePaths = "employee")
    List<Assessment>
    findByAiGeneratedTrueAndStatusAndEmployeeIsNullOrderByCreatedAtDesc(
            String status
    );


    /*
     * =========================================================
     * ASSESSMENTS BY ROLE
     * =========================================================
     */

    @EntityGraph(attributePaths = "employee")
    List<Assessment> findByTargetRoleOrderByCreatedAtDesc(
            String targetRole
    );


    /*
     * =========================================================
     * ASSESSMENT COUNTS
     * =========================================================
     */

    long countByStatus(
            String status
    );


    long countByType(
            String type
    );


    long countByStatusAndEmployee(
            String status,
            User employee
    );


    long countByEmployee(
            User employee
    );


    /*
     * =========================================================
     * AI ASSESSMENT COUNTS
     * =========================================================
     */

    /**
     * Total number of AI-generated assessments.
     */
    long countByAiGeneratedTrue();


    /**
     * Total AI-generated assessments which have no employee.
     */
    long countByAiGeneratedTrueAndEmployeeIsNull();


    /*
     * =========================================================
     * TARGET ROLE COUNT
     * =========================================================
     */

    long countByTargetRole(
            String targetRole
    );
}