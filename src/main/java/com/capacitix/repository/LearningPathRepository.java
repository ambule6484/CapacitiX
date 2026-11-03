
package com.capacitix.repository;

import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningPathRepository
        extends JpaRepository<LearningPath, Long> {

    @Override
    @EntityGraph(attributePaths = {"items"})
    Optional<LearningPath> findById(Long id);

    @EntityGraph(attributePaths = {"items"})
    List<LearningPath> findByEmployee(User employee);

    @EntityGraph(attributePaths = {"items"})
    List<LearningPath> findByEmployeeAndStatus(
            User employee,
            LearningPath.LearningPathStatus status
    );

    @EntityGraph(attributePaths = {"items"})
    Optional<LearningPath> findTopByEmployeeOrderByCreatedAtDesc(
            User employee
    );

    @EntityGraph(attributePaths = {"items"})
    Optional<LearningPath> findTopByEmployeeAndStatusOrderByCreatedAtDesc(
            User employee,
            LearningPath.LearningPathStatus status
    );

    boolean existsByEmployeeAndStatus(
            User employee,
            LearningPath.LearningPathStatus status
    );

    @EntityGraph(attributePaths = {"items"})
    Optional<LearningPath> findTopByAssessmentAssignmentOrderByCreatedAtDesc(
            AssessmentAssignment assessmentAssignment
    );

    boolean existsByAssessmentAssignment(
            AssessmentAssignment assessmentAssignment
    );

    void deleteByEmployee(User employee);
}
