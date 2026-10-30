
package com.capacitix.repository;

import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PracticalTaskRepository
        extends JpaRepository<PracticalTask, Long> {

    // =========================================================
    // EMPLOYEE TASK QUERIES
    // =========================================================

    List<PracticalTask> findByEmployeeOrderByCreatedAtDesc(
            User employee
    );

    List<PracticalTask> findByEmployeeIdOrderByCreatedAtDesc(
            Long employeeId
    );

    Optional<PracticalTask> findByIdAndEmployee(
            Long id,
            User employee
    );

    List<PracticalTask> findByLearningPathItemOrderByTaskOrderAsc(
            LearningPathItem learningPathItem
    );

    List<PracticalTask> findByEmployeeAndLearningPathItemOrderByTaskOrderAsc(
            User employee,
            LearningPathItem learningPathItem
    );

    boolean existsByEmployeeAndLearningPathItem(
            User employee,
            LearningPathItem learningPathItem
    );

    List<PracticalTask> findByEmployeeAndSkillNameIgnoreCaseOrderByCreatedAtDesc(
            User employee,
            String skillName
    );

    List<PracticalTask> findByEmployeeAndStatusOrderByCreatedAtDesc(
            User employee,
            PracticalTask.TaskStatus status
    );


    // =========================================================
    // GENERAL TASK QUERIES
    // =========================================================

    List<PracticalTask> findByStatusOrderByCreatedAtDesc(
            PracticalTask.TaskStatus status
    );


    // =========================================================
    // TRAINER TASK QUERIES
    // =========================================================
    //
    // PracticalTask
    //      ↓
    // employee
    //      ↓
    // trainer
    //
    // Spring Data automatically traverses:
    //
    // employee.trainer
    //
    // =========================================================

    /**
     * Get one practical task only when the employee
     * belongs to the specified trainer.
     *
     * Ownership path:
     *
     * PracticalTask.id
     *      ↓
     * PracticalTask.employee
     *      ↓
     * User.trainer
     */
    Optional<PracticalTask> findByIdAndEmployeeTrainer(
            Long id,
            User trainer
    );


    /**
     * Get all practical tasks belonging to employees
     * assigned to the specified trainer.
     */
    List<PracticalTask>
    findByEmployeeTrainerOrderByCreatedAtDesc(
            User trainer
    );


    /**
     * Get practical tasks belonging to employees assigned
     * to the trainer with a specific status.
     */
    List<PracticalTask>
    findByEmployeeTrainerAndStatusOrderByCreatedAtDesc(
            User trainer,
            PracticalTask.TaskStatus status
    );


    /**
     * Count all practical tasks belonging to trainer's employees.
     */
    long countByEmployeeTrainer(
            User trainer
    );


    /**
     * Count trainer's employee tasks by status.
     */
    long countByEmployeeTrainerAndStatus(
            User trainer,
            PracticalTask.TaskStatus status
    );


    // =========================================================
    // EMPLOYEE COUNTS
    // =========================================================

    long countByEmployee(
            User employee
    );

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