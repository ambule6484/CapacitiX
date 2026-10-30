package com.capacitix.repository;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssessmentAssignmentRepository
        extends JpaRepository<AssessmentAssignment, Long> {

    /*
     * ============================================================
     * FIND ASSIGNMENTS
     * ============================================================
     */

    /**
     * Find a specific assessment assignment for an employee.
     */
    Optional<AssessmentAssignment> findByAssessmentAndEmployee(
            Assessment assessment,
            User employee
    );

    /**
     * Find all assessments assigned to an employee.
     *
     * Used by:
     * /employee/assessments
     */
    List<AssessmentAssignment> findByEmployeeOrderByAssignedAtDesc(
            User employee
    );

    /**
     * Find all assignments for an employee using employee ID.
     */
    List<AssessmentAssignment> findByEmployeeIdOrderByAssignedAtDesc(
            Long employeeId
    );

    /**
     * Find all assignments created by a trainer.
     *
     * Used by:
     * /trainer/assessments/assigned
     */
    List<AssessmentAssignment> findByTrainerOrderByAssignedAtDesc(
            User trainer
    );

    /**
     * Find all assignments for a particular assessment.
     */
    List<AssessmentAssignment> findByAssessmentOrderByAssignedAtDesc(
            Assessment assessment
    );

    /**
     * Find assignments by status.
     *
     * Examples:
     * ASSIGNED
     * IN_PROGRESS
     * COMPLETED
     * CANCELLED
     */
    List<AssessmentAssignment> findByStatusOrderByAssignedAtDesc(
            String status
    );


    /*
     * ============================================================
     * EXISTENCE CHECKS
     * ============================================================
     */

    /**
     * Check whether this assessment is already assigned
     * to this particular employee.
     */
    boolean existsByAssessmentAndEmployee(
            Assessment assessment,
            User employee
    );


    /*
     * ============================================================
     * COUNT METHODS
     * ============================================================
     */

    /**
     * Count total assessments assigned to an employee.
     */
    long countByEmployee(User employee);

    /**
     * Count assignments having a particular status.
     */
    long countByStatus(String status);

    /**
     * Count assignments created by a trainer.
     */
    long countByTrainer(User trainer);


    /*
     * ============================================================
     * ADDITIONAL USEFUL METHODS
     * ============================================================
     */

    /**
     * Count assignments for a particular assessment.
     *
     * Useful for checking whether an AI-generated assessment
     * has already been assigned.
     */
    long countByAssessment(Assessment assessment);

    /**
     * Count assignments for a particular assessment and status.
     *
     * Example:
     * countByAssessmentAndStatus(assessment, "ASSIGNED")
     */
    long countByAssessmentAndStatus(
            Assessment assessment,
            String status
    );

    /**
     * Check whether an assessment has any assignment.
     */
    boolean existsByAssessment(Assessment assessment);

    /**
     * Check whether an employee has an assignment with
     * a particular status.
     */
    boolean existsByEmployeeAndStatus(
            User employee,
            String status
    );

    /**
     * Count assignments for a trainer having a particular status.
     */
    long countByTrainerAndStatus(
            User trainer,
            String status
    );

    /**
     * Find assignments for an employee with a particular status.
     */
    List<AssessmentAssignment> findByEmployeeAndStatusOrderByAssignedAtDesc(
            User employee,
            String status
    );

    /**
     * Find assignments for a trainer with a particular status.
     */
    List<AssessmentAssignment> findByTrainerAndStatusOrderByAssignedAtDesc(
            User trainer,
            String status
    );


    /*
     * ============================================================
     * COMPETENCY → MAPPED EMPLOYEE COUNTS
     * ============================================================
     *
     * Mapping logic:
     *
     * AssessmentAssignment
     *        ↓
     * Assessment
     *        ↓
     * AssessmentQuestion
     *        ↓
     * Competency
     *
     * Therefore an employee is considered mapped to a competency
     * when the employee has an assigned assessment containing
     * at least one question belonging to that competency.
     *
     * COUNT(DISTINCT employee.id) prevents the same employee
     * from being counted multiple times for the same competency.
     */

    /**
     * Returns the number of distinct employees mapped to
     * each competency.
     *
     * Result:
     *
     * Object[0] = competency ID
     * Object[1] = number of distinct mapped employees
     *
     * Example:
     *
     * Java      -> 5 employees
     * Python    -> 3 employees
     * SQL       -> 7 employees
     */
    @Query("""
            SELECT
                aq.competency.id,
                COUNT(DISTINCT aa.employee.id)
            FROM AssessmentAssignment aa
            JOIN AssessmentQuestion aq
                ON aq.assessment = aa.assessment
            WHERE aq.competency IS NOT NULL
              AND aa.employee IS NOT NULL
            GROUP BY aq.competency.id
            """)
    List<Object[]> findMappedEmployeeCountsByCompetency();


    /**
     * Count all distinct employees that are mapped to
     * at least one competency through assigned assessments.
     *
     * This is used for the top-level "Mapped Employees"
     * statistic on the Admin Competencies page.
     *
     * DISTINCT is important because one employee may have
     * multiple competencies.
     */
    @Query("""
            SELECT COUNT(DISTINCT aa.employee.id)
            FROM AssessmentAssignment aa
            JOIN AssessmentQuestion aq
                ON aq.assessment = aa.assessment
            WHERE aq.competency IS NOT NULL
              AND aa.employee IS NOT NULL
            """)
    long countDistinctMappedEmployees();

}