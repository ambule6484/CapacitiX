
package com.capacitix.repository;

import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.PracticalTaskSubmission;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PracticalTaskSubmissionRepository
        extends JpaRepository<PracticalTaskSubmission, Long> {


    // ============================================================
    // EMPLOYEE SUBMISSIONS
    // ============================================================

    /**
     * Get all submissions of an employee.
     */
    List<PracticalTaskSubmission>
    findByEmployeeOrderByCreatedAtDesc(
            User employee
    );


    /**
     * Get all submissions of an employee by employee ID.
     */
    List<PracticalTaskSubmission>
    findByEmployeeIdOrderByCreatedAtDesc(
            Long employeeId
    );


    /**
     * Find a specific submission belonging to an employee.
     *
     * Used to prevent one employee from accessing
     * another employee's submission.
     */
    Optional<PracticalTaskSubmission>
    findByIdAndEmployee(
            Long id,
            User employee
    );


    /**
     * Find the current submission for a practical task
     * belonging to a specific employee.
     */
    Optional<PracticalTaskSubmission>
    findByPracticalTaskAndEmployee(
            PracticalTask practicalTask,
            User employee
    );


    /**
     * Check whether an employee already has a submission
     * for a practical task.
     */
    boolean existsByPracticalTaskAndEmployee(
            PracticalTask practicalTask,
            User employee
    );


    /**
     * Get employee submissions filtered by status.
     */
    List<PracticalTaskSubmission>
    findByEmployeeAndStatusOrderByCreatedAtDesc(
            User employee,
            PracticalTaskSubmission.SubmissionStatus status
    );


    // ============================================================
    // PRACTICAL TASK SUBMISSIONS
    // ============================================================

    /**
     * Get all submissions for a practical task.
     *
     * Useful if submission history is needed later.
     */
    List<PracticalTaskSubmission>
    findByPracticalTaskOrderByCreatedAtDesc(
            PracticalTask practicalTask
    );


    /**
     * Count submissions for a practical task.
     */
    long countByPracticalTask(
            PracticalTask practicalTask
    );


    // ============================================================
    // TRAINER REVIEW
    // ============================================================

    /**
     * Get submissions belonging only to employees
     * assigned to the specified trainer.
     *
     * Important relationship:
     *
     * PracticalTask
     *      ↓
     * employee
     *      ↓
     * trainer
     */
    List<PracticalTaskSubmission>
    findByPracticalTaskEmployeeTrainerAndStatusOrderBySubmittedAtDesc(
            User trainer,
            PracticalTaskSubmission.SubmissionStatus status
    );


    /**
     * Get all practical-task submissions belonging
     * to employees assigned to the trainer.
     */
    List<PracticalTaskSubmission>
    findByPracticalTaskEmployeeTrainerOrderBySubmittedAtDesc(
            User trainer
    );


    /**
     * Find one submission only when the employee
     * belongs to the specified trainer.
     *
     * This is important for trainer authorization.
     *
     * Example:
     *
     * Trainer A cannot review Employee B's task
     * when Employee B belongs to Trainer B.
     */
    Optional<PracticalTaskSubmission>
    findByIdAndPracticalTaskEmployeeTrainer(
            Long submissionId,
            User trainer
    );


    /**
     * Count trainer's submissions by status.
     *
     * Example:
     *
     * SUBMITTED = pending reviews
     * APPROVED = approved submissions
     * NEEDS_IMPROVEMENT = returned submissions
     */
    long countByPracticalTaskEmployeeTrainerAndStatus(
            User trainer,
            PracticalTaskSubmission.SubmissionStatus status
    );


    // ============================================================
    // EMPLOYEE COUNTS
    // ============================================================

    /**
     * Total submissions made by an employee.
     */
    long countByEmployee(
            User employee
    );


    /**
     * Count employee submissions by status.
     */
    long countByEmployeeAndStatus(
            User employee,
            PracticalTaskSubmission.SubmissionStatus status
    );
}