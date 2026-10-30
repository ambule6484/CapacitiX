
package com.capacitix.repository;

import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningPathRepository
        extends JpaRepository<LearningPath, Long> {


    /**
     * Find all learning paths created for an employee.
     */
    List<LearningPath> findByEmployee(
            User employee
    );


    /**
     * Find all learning paths for an employee
     * with the given status.
     */
    List<LearningPath> findByEmployeeAndStatus(
            User employee,
            LearningPath.LearningPathStatus status
    );


    /**
     * Find latest learning path generated for an employee.
     */
    Optional<LearningPath>
    findTopByEmployeeOrderByCreatedAtDesc(
            User employee
    );


    /**
     * Find latest active learning path.
     */
    Optional<LearningPath>
    findTopByEmployeeAndStatusOrderByCreatedAtDesc(
            User employee,
            LearningPath.LearningPathStatus status
    );


    /**
     * Check whether employee already has
     * a learning path with the given status.
     */
    boolean existsByEmployeeAndStatus(
            User employee,
            LearningPath.LearningPathStatus status
    );


    /**
     * Find the latest learning path generated from
     * a specific assessment assignment.
     *
     * IMPORTANT:
     * There are currently duplicate records in the database.
     * Therefore, this method intentionally returns the latest
     * record instead of expecting exactly one record.
     */
    Optional<LearningPath>
    findTopByAssessmentAssignmentOrderByCreatedAtDesc(
            AssessmentAssignment assessmentAssignment
    );


    /**
     * Check whether a learning path already exists
     * for a specific assessment assignment.
     */
    boolean existsByAssessmentAssignment(
            AssessmentAssignment assessmentAssignment
    );


    /**
     * Delete all learning paths belonging to an employee.
     */
    void deleteByEmployee(
            User employee
    );
}