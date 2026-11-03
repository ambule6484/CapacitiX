
package com.capacitix.repository;

import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PracticalTaskRepository
        extends JpaRepository<PracticalTask, Long> {

    // =========================================================
    // FETCH GRAPH FOR PRACTICAL TASK RELATIONSHIPS
    // =========================================================

    /*
     * Fetches:
     *
     * PracticalTask
     *      |
     *      |-- learningPathItem
     *              |
     *              |-- learningPath
     *
     *      |-- trainingModule
     *
     * This prevents LazyInitializationException when
     * Thymeleaf accesses nested task properties.
     */

    // =========================================================
    // EMPLOYEE TASK QUERIES
    // =========================================================

    @EntityGraph(attributePaths = {
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByEmployeeOrderByCreatedAtDesc(
            User employee
    );

    @EntityGraph(attributePaths = {
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByEmployeeIdOrderByCreatedAtDesc(
            Long employeeId
    );

    @EntityGraph(attributePaths = {
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    Optional<PracticalTask> findByIdAndEmployee(
            Long id,
            User employee
    );

    @EntityGraph(attributePaths = {
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByLearningPathItemOrderByTaskOrderAsc(
            LearningPathItem learningPathItem
    );

    @EntityGraph(attributePaths = {
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByEmployeeAndLearningPathItemOrderByTaskOrderAsc(
            User employee,
            LearningPathItem learningPathItem
    );

    boolean existsByEmployeeAndLearningPathItem(
            User employee,
            LearningPathItem learningPathItem
    );

    @EntityGraph(attributePaths = {
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByEmployeeAndSkillNameIgnoreCaseOrderByCreatedAtDesc(
            User employee,
            String skillName
    );

    @EntityGraph(attributePaths = {
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByEmployeeAndStatusOrderByCreatedAtDesc(
            User employee,
            PracticalTask.TaskStatus status
    );


    // =========================================================
    // GENERAL TASK QUERIES
    // =========================================================

    @EntityGraph(attributePaths = {
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByStatusOrderByCreatedAtDesc(
            PracticalTask.TaskStatus status
    );


    // =========================================================
    // TRAINER TASK QUERIES
    // =========================================================

    @EntityGraph(attributePaths = {
            "employee",
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    Optional<PracticalTask> findByIdAndEmployeeTrainer(
            Long id,
            User trainer
    );

    @EntityGraph(attributePaths = {
            "employee",
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByEmployeeTrainerOrderByCreatedAtDesc(
            User trainer
    );

    @EntityGraph(attributePaths = {
            "employee",
            "learningPathItem",
            "learningPathItem.learningPath",
            "trainingModule"
    })
    List<PracticalTask> findByEmployeeTrainerAndStatusOrderByCreatedAtDesc(
            User trainer,
            PracticalTask.TaskStatus status
    );


    // =========================================================
    // TRAINER COUNTS
    // =========================================================

    long countByEmployeeTrainer(User trainer);

    long countByEmployeeTrainerAndStatus(
            User trainer,
            PracticalTask.TaskStatus status
    );


    // =========================================================
    // EMPLOYEE COUNTS
    // =========================================================

    long countByEmployee(User employee);

    long countByEmployeeAndStatus(
            User employee,
            PracticalTask.TaskStatus status
    );

    long countByEmployeeAndSkillNameIgnoreCase(
            User employee,
            String skillName
    );


    // =========================================================
    // GENERAL COUNTS
    // =========================================================

    long countByStatus(
            PracticalTask.TaskStatus status
    );
}
