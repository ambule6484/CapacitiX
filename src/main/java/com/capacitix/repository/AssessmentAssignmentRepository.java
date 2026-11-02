
package com.capacitix.repository;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssessmentAssignmentRepository
        extends JpaRepository<AssessmentAssignment, Long> {

    /*
     * FIND ASSIGNMENTS
     */

    @EntityGraph(attributePaths = {"assessment"})
    Optional<AssessmentAssignment> findByAssessmentAndEmployee(
            Assessment assessment,
            User employee
    );

    @EntityGraph(attributePaths = {"assessment"})
    List<AssessmentAssignment> findByEmployeeOrderByAssignedAtDesc(
            User employee
    );

    @EntityGraph(attributePaths = {"assessment"})
    List<AssessmentAssignment> findByEmployeeIdOrderByAssignedAtDesc(
            Long employeeId
    );

    @EntityGraph(attributePaths = {"assessment"})
    List<AssessmentAssignment> findByTrainerOrderByAssignedAtDesc(
            User trainer
    );

    @EntityGraph(attributePaths = {"assessment"})
    List<AssessmentAssignment> findByAssessmentOrderByAssignedAtDesc(
            Assessment assessment
    );

    @EntityGraph(attributePaths = {"assessment"})
    List<AssessmentAssignment> findByStatusOrderByAssignedAtDesc(
            String status
    );

    /*
     * EXISTENCE CHECKS
     */

    boolean existsByAssessmentAndEmployee(
            Assessment assessment,
            User employee
    );

    boolean existsByAssessment(Assessment assessment);

    boolean existsByEmployeeAndStatus(
            User employee,
            String status
    );

    /*
     * COUNT METHODS
     */

    long countByEmployee(User employee);

    long countByStatus(String status);

    long countByTrainer(User trainer);

    long countByAssessment(Assessment assessment);

    long countByAssessmentAndStatus(
            Assessment assessment,
            String status
    );

    long countByTrainerAndStatus(
            User trainer,
            String status
    );

    /*
     * FILTERED ASSIGNMENTS
     */

    @EntityGraph(attributePaths = {"assessment"})
    List<AssessmentAssignment> findByEmployeeAndStatusOrderByAssignedAtDesc(
            User employee,
            String status
    );

    @EntityGraph(attributePaths = {"assessment"})
    List<AssessmentAssignment> findByTrainerAndStatusOrderByAssignedAtDesc(
            User trainer,
            String status
    );

    /*
     * COMPETENCY → MAPPED EMPLOYEE COUNTS
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
