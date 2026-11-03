
package com.capacitix.service;

import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.PracticalTaskSubmission;
import com.capacitix.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing practical-task submissions and trainer review.
 *
 * Complete workflow:
 *
 * Employee
 *     ↓
 * Start Practical Task
 *     ↓
 * Work on Task
 *     ↓
 * Submit Solution
 *     ↓
 * SUBMITTED
 *     ↓
 * Trainer Review
 *     ↓
 * ┌───────────────────────────────┐
 * │                               │
 * APPROVED                  NEEDS_IMPROVEMENT
 * │                               │
 * ↓                               ↓
 * COMPLETED                 Employee revises
 * 100%                            ↓
 * │                           Resubmit
 * ↓                               ↓
 * Training Progress          SUBMITTED
 * │
 * ↓
 * Competency Update
 *
 * IMPORTANT:
 *
 * Employee submission does NOT mean task completion.
 *
 * Only the assigned trainer can approve a submission.
 *
 * Trainer approval:
 *
 *     Submission → APPROVED
 *     PracticalTask → COMPLETED
 *     PracticalTask → 100%
 *
 * Trainer improvement request:
 *
 *     Submission → NEEDS_IMPROVEMENT
 *     PracticalTask → NEEDS_IMPROVEMENT
 *     Employee → can revise and resubmit
 */
public interface PracticalTaskSubmissionService {

    // =========================================================
    // EMPLOYEE SUBMISSION
    // =========================================================

    /**
     * Submit an employee's practical task.
     *
     * Employee must own the practical task.
     *
     * Submission becomes:
     *
     *     SUBMITTED
     *     progress = 99%
     *
     * The practical task also becomes:
     *
     *     SUBMITTED
     *     progress = 99%
     *
     * It does NOT become COMPLETED here.
     *
     * The assigned trainer must review it.
     *
     * @param practicalTaskId practical task ID
     * @param employee authenticated employee
     * @param submissionText employee's solution/explanation
     * @param submissionUrl optional GitHub/project URL
     * @return saved submission
     */
    PracticalTaskSubmission submitTask(
            Long practicalTaskId,
            User employee,
            String submissionText,
            String submissionUrl
    );

    /**
     * Get a specific submission belonging to an employee.
     *
     * Ownership is checked so an employee cannot access
     * another employee's submission.
     *
     * @param submissionId submission ID
     * @param employee authenticated employee
     * @return submission if accessible
     */
    Optional<PracticalTaskSubmission> getEmployeeSubmission(
            Long submissionId,
            User employee
    );

    /**
     * Get the current submission for a practical task.
     *
     * The current design maintains one submission record per
     * employee and practical task and reuses it for resubmission.
     *
     * @param practicalTaskId practical task ID
     * @param employee authenticated employee
     * @return current submission
     */
    Optional<PracticalTaskSubmission> getTaskSubmission(
            Long practicalTaskId,
            User employee
    );

    /**
     * Get all submissions made by an employee.
     *
     * @param employee authenticated employee
     * @return employee submissions
     */
    List<PracticalTaskSubmission> getEmployeeSubmissions(
            User employee
    );

    /**
     * Get all submissions for a particular practical task
     * belonging to the employee.
     *
     * @param practicalTaskId practical task ID
     * @param employee authenticated employee
     * @return task submissions
     */
    List<PracticalTaskSubmission> getTaskSubmissions(
            Long practicalTaskId,
            User employee
    );

    // =========================================================
    // EMPLOYEE RESUBMISSION
    // =========================================================

    /**
     * Resubmit an existing practical task after the trainer
     * requested improvement.
     *
     * Expected flow:
     *
     * NEEDS_IMPROVEMENT
     *        ↓
     * Employee revises solution
     *        ↓
     * Resubmit
     *        ↓
     * SUBMITTED
     *        ↓
     * Trainer Review
     *
     * The existing submission record is reused.
     *
     * @param submissionId submission ID
     * @param employee authenticated employee
     * @param submissionText revised solution
     * @param submissionUrl optional updated project URL
     * @return updated submission
     */
    PracticalTaskSubmission resubmitTask(
            Long submissionId,
            User employee,
            String submissionText,
            String submissionUrl
    );

    /**
     * Check whether an employee has a submission for
     * a practical task.
     *
     * @param practicalTaskId practical task ID
     * @param employee authenticated employee
     * @return true if a submission exists
     */
    boolean hasSubmittedTask(
            Long practicalTaskId,
            User employee
    );

    // =========================================================
    // TRAINER REVIEW
    // =========================================================

    /**
     * Get all practical-task submissions waiting for trainer review.
     *
     * Only submissions belonging to employees assigned to the
     * specified trainer should be returned.
     *
     * Expected status:
     *
     *     SUBMITTED
     *
     * @param trainer authenticated trainer
     * @return pending submissions
     */
    List<PracticalTaskSubmission> getPendingTrainerReviews(
            User trainer
    );

    /**
     * Get a specific submission for trainer review.
     *
     * Authorization must verify that the practical task belongs
     * to an employee assigned to this trainer.
     *
     * @param submissionId submission ID
     * @param trainer authenticated trainer
     * @return accessible submission
     */
    Optional<PracticalTaskSubmission> getTrainerSubmission(
            Long submissionId,
            User trainer
    );

    /**
     * Approve an employee's practical-task submission.
     *
     * This is the ONLY operation that should complete the
     * practical task.
     *
     * Expected result:
     *
     * Submission:
     *     APPROVED
     *     progress = 100%
     *
     * PracticalTask:
     *     COMPLETED
     *     progress = 100%
     *     completedAt = current time
     *
     * After approval, the next business logic can update:
     *
     *     Training Progress
     *     Competency
     *     Skill Gap
     *
     * @param submissionId submission ID
     * @param trainer authenticated trainer
     * @param score trainer score from 0 to 100
     * @param feedback trainer feedback
     * @return approved submission
     */
    PracticalTaskSubmission approveSubmission(
            Long submissionId,
            User trainer,
            Integer score,
            String feedback
    );

    /**
     * Request improvement from the employee.
     *
     * Expected result:
     *
     * Submission:
     *     NEEDS_IMPROVEMENT
     *     progress = 99%
     *
     * PracticalTask:
     *     NEEDS_IMPROVEMENT
     *     progress = 99%
     *
     * The employee can then revise and resubmit.
     *
     * @param submissionId submission ID
     * @param trainer authenticated trainer
     * @param feedback required improvement feedback
     * @return updated submission
     */
    PracticalTaskSubmission requestImprovement(
            Long submissionId,
            User trainer,
            String feedback
    );

    // =========================================================
    // TRAINER REVIEW COUNTS
    // =========================================================

    /**
     * Count submissions waiting for a trainer's review.
     *
     * @param trainer authenticated trainer
     * @return number of pending reviews
     */
    long countPendingTrainerReviews(
            User trainer
    );

    // =========================================================
    // EMPLOYEE COUNTS
    // =========================================================

    /**
     * Count all submissions made by an employee.
     *
     * @param employee authenticated employee
     * @return total submission count
     */
    long countSubmissions(
            User employee
    );

    /**
     * Count employee submissions by status.
     *
     * @param employee authenticated employee
     * @param status submission status
     * @return number of matching submissions
     */
    long countSubmissionsByStatus(
            User employee,
            PracticalTaskSubmission.SubmissionStatus status
    );
}