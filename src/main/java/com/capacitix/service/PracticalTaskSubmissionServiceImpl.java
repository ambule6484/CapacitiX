
package com.capacitix.service;

import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.PracticalTaskSubmission;
import com.capacitix.entity.User;
import com.capacitix.repository.PracticalTaskRepository;
import com.capacitix.repository.PracticalTaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for:
 *
 * 1. Employee practical-task submission
 * 2. Employee resubmission
 * 3. Employee submission queries
 * 4. Trainer review
 * 5. Trainer approval
 * 6. Trainer improvement requests
 *
 * Workflow:
 *
 * Employee
 *      ↓
 * Practical Task
 *      ↓
 * Submit Solution
 *      ↓
 * SUBMITTED / 99%
 *      ↓
 * Trainer Review
 *      ↓
 * ┌─────────────────────────────┐
 * │                             │
 * APPROVED               NEEDS_IMPROVEMENT
 * │                             │
 * ↓                             ↓
 * COMPLETED / 100%        Employee revises
 * │                             │
 * ↓                             ↓
 * Training Progress          Resubmit
 * │                             │
 * ↓                             └──────→ SUBMITTED
 * Competency Update
 *
 * IMPORTANT:
 *
 * Employee submission NEVER completes the practical task.
 *
 * Only trainer approval can call:
 *
 *     practicalTask.markCompleted();
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PracticalTaskSubmissionServiceImpl
        implements PracticalTaskSubmissionService {

    private final PracticalTaskSubmissionRepository
            practicalTaskSubmissionRepository;

    private final PracticalTaskRepository
            practicalTaskRepository;

    // =========================================================
    // EMPLOYEE SUBMIT
    // =========================================================

    /**
     * Submit an employee's practical-task solution.
     *
     * Result:
     *
     * Submission:
     *     SUBMITTED
     *     99%
     *
     * PracticalTask:
     *     SUBMITTED
     *     99%
     *
     * It does NOT become COMPLETED.
     */
    @Override
    public PracticalTaskSubmission submitTask(
            Long practicalTaskId,
            User employee,
            String submissionText,
            String submissionUrl
    ) {

        validateEmployee(employee);
        validateTaskId(practicalTaskId);

        PracticalTask practicalTask =
                getEmployeeTask(
                        practicalTaskId,
                        employee
                );

        validateSubmissionContent(
                submissionText,
                submissionUrl
        );

        /*
         * A completed task cannot be submitted again.
         */
        if (practicalTask.getStatus() ==
                PracticalTask.TaskStatus.COMPLETED) {

            throw new IllegalStateException(
                    "This practical task has already been approved and completed."
            );
        }

        /*
         * A currently submitted task is waiting for trainer review.
         */
        if (practicalTask.getStatus() ==
                PracticalTask.TaskStatus.SUBMITTED) {

            throw new IllegalStateException(
                    "This practical task has already been submitted and is waiting for trainer review."
            );
        }

        PracticalTaskSubmission submission =
                practicalTaskSubmissionRepository
                        .findByPracticalTaskAndEmployee(
                                practicalTask,
                                employee
                        )
                        .orElseGet(() -> {

                            PracticalTaskSubmission newSubmission =
                                    new PracticalTaskSubmission();

                            newSubmission.setPracticalTask(
                                    practicalTask
                            );

                            newSubmission.setEmployee(
                                    employee
                            );

                            return newSubmission;
                        });

        /*
         * Approved submissions cannot be modified.
         */
        if (submission.getStatus() ==
                PracticalTaskSubmission.SubmissionStatus.APPROVED) {

            throw new IllegalStateException(
                    "This submission has already been approved."
            );
        }

        /*
         * If improvement was requested, employee should use
         * resubmitTask() instead of submitTask().
         */
        if (submission.getStatus() ==
                PracticalTaskSubmission.SubmissionStatus.NEEDS_IMPROVEMENT) {

            throw new IllegalStateException(
                    "This submission requires improvement. Use resubmitTask() to submit the revised solution."
            );
        }

        /*
         * Save employee solution.
         */
        submission.setSolutionDescription(
                normalizeText(submissionText)
        );

        submission.setSubmissionUrl(
                normalizeText(submissionUrl)
        );

        /*
         * Mark submission as SUBMITTED.
         *
         * progress = 99%
         */
        submission.submit();

        /*
         * Practical task is now waiting for trainer review.
         *
         * status = SUBMITTED
         * progress = 99%
         */
        practicalTask.markSubmitted();

        practicalTaskRepository.save(
                practicalTask
        );

        PracticalTaskSubmission savedSubmission =
                practicalTaskSubmissionRepository.save(
                        submission
                );

        log.info(
                "Practical task submitted for trainer review. employeeId={}, taskId={}, submissionId={}",
                employee.getId(),
                practicalTask.getId(),
                savedSubmission.getId()
        );

        return savedSubmission;
    }

    // =========================================================
    // EMPLOYEE SUBMISSION QUERIES
    // =========================================================

    /**
     * Get a specific submission belonging to an employee.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<PracticalTaskSubmission> getEmployeeSubmission(
            Long submissionId,
            User employee
    ) {

        validateEmployee(employee);

        if (submissionId == null ||
                submissionId <= 0) {

            return Optional.empty();
        }

        return practicalTaskSubmissionRepository
                .findByIdAndEmployee(
                        submissionId,
                        employee
                );
    }

    /**
     * Get the current submission for a practical task.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<PracticalTaskSubmission> getTaskSubmission(
            Long practicalTaskId,
            User employee
    ) {

        validateEmployee(employee);
        validateTaskId(practicalTaskId);

        PracticalTask practicalTask =
                getEmployeeTask(
                        practicalTaskId,
                        employee
                );

        return practicalTaskSubmissionRepository
                .findByPracticalTaskAndEmployee(
                        practicalTask,
                        employee
                );
    }

    /**
     * Get all submissions made by an employee.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PracticalTaskSubmission> getEmployeeSubmissions(
            User employee
    ) {

        validateEmployee(employee);

        return practicalTaskSubmissionRepository
                .findByEmployeeOrderByCreatedAtDesc(
                        employee
                );
    }

    /**
     * Get submissions for one practical task.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PracticalTaskSubmission> getTaskSubmissions(
            Long practicalTaskId,
            User employee
    ) {

        validateEmployee(employee);
        validateTaskId(practicalTaskId);

        PracticalTask practicalTask =
                getEmployeeTask(
                        practicalTaskId,
                        employee
                );

        return practicalTaskSubmissionRepository
                .findByPracticalTaskOrderByCreatedAtDesc(
                        practicalTask
                );
    }

    // =========================================================
    // EMPLOYEE RESUBMIT
    // =========================================================

    /**
     * Resubmit a practical task after trainer requested improvement.
     *
     * Flow:
     *
     * NEEDS_IMPROVEMENT
     *        ↓
     * Employee revises solution
     *        ↓
     * SUBMITTED
     *        ↓
     * Trainer Review
     */
    @Override
    public PracticalTaskSubmission resubmitTask(
            Long submissionId,
            User employee,
            String submissionText,
            String submissionUrl
    ) {

        validateEmployee(employee);

        if (submissionId == null ||
                submissionId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid submission ID."
            );
        }

        validateSubmissionContent(
                submissionText,
                submissionUrl
        );

        /*
         * Employee ownership check.
         */
        PracticalTaskSubmission submission =
                practicalTaskSubmissionRepository
                        .findByIdAndEmployee(
                                submissionId,
                                employee
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Submission not found or access denied."
                                )
                        );

        PracticalTask practicalTask =
                submission.getPracticalTask();

        if (practicalTask == null) {

            throw new IllegalStateException(
                    "Practical task is missing for this submission."
            );
        }

        /*
         * Extra ownership validation.
         */
        if (practicalTask.getEmployee() == null ||
                practicalTask.getEmployee().getId() == null ||
                !practicalTask.getEmployee()
                        .getId()
                        .equals(employee.getId())) {

            throw new IllegalArgumentException(
                    "You are not authorized to resubmit this task."
            );
        }

        /*
         * Approved submission cannot be resubmitted.
         */
        if (submission.getStatus() ==
                PracticalTaskSubmission.SubmissionStatus.APPROVED) {

            throw new IllegalStateException(
                    "An approved submission cannot be resubmitted."
            );
        }

        /*
         * Only trainer-requested improvements can be resubmitted.
         */
        if (submission.getStatus() !=
                PracticalTaskSubmission.SubmissionStatus.NEEDS_IMPROVEMENT) {

            throw new IllegalStateException(
                    "Only submissions requiring improvement can be resubmitted."
            );
        }

        /*
         * Reset review information.
         *
         * Status becomes IN_PROGRESS.
         */
        submission.prepareForResubmission();

        /*
         * Save revised solution.
         */
        submission.setSolutionDescription(
                normalizeText(submissionText)
        );

        submission.setSubmissionUrl(
                normalizeText(submissionUrl)
        );

        /*
         * Submit the revised solution.
         *
         * Status = SUBMITTED
         * Progress = 99%
         */
        submission.submit();

        /*
         * Practical task returns to SUBMITTED.
         */
        practicalTask.markSubmitted();

        practicalTaskRepository.save(
                practicalTask
        );

        PracticalTaskSubmission savedSubmission =
                practicalTaskSubmissionRepository.save(
                        submission
                );

        log.info(
                "Practical task resubmitted for trainer review. employeeId={}, taskId={}, submissionId={}",
                employee.getId(),
                practicalTask.getId(),
                savedSubmission.getId()
        );

        return savedSubmission;
    }

    // =========================================================
    // TRAINER REVIEW - PENDING
    // =========================================================

    /**
     * Get all submissions waiting for the trainer's review.
     *
     * Repository query ensures that only submissions belonging
     * to employees assigned to this trainer are returned.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PracticalTaskSubmission> getPendingTrainerReviews(
            User trainer
    ) {

        validateTrainer(trainer);

        return practicalTaskSubmissionRepository
                .findByPracticalTaskEmployeeTrainerAndStatusOrderBySubmittedAtDesc(
                        trainer,
                        PracticalTaskSubmission.SubmissionStatus.SUBMITTED
                );
    }

    /**
     * Get a specific submission for trainer review.
     *
     * The repository query checks:
     *
     * submission
     *      ↓
     * practicalTask
     *      ↓
     * employee
     *      ↓
     * assigned trainer
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<PracticalTaskSubmission> getTrainerSubmission(
            Long submissionId,
            User trainer
    ) {

        validateTrainer(trainer);

        if (submissionId == null ||
                submissionId <= 0) {

            return Optional.empty();
        }

        return practicalTaskSubmissionRepository
                .findByIdAndPracticalTaskEmployeeTrainer(
                        submissionId,
                        trainer
                );
    }

    // =========================================================
    // TRAINER APPROVE
    // =========================================================

    /**
     * Approve an employee's practical-task submission.
     *
     * THIS IS THE ONLY METHOD THAT COMPLETES THE PRACTICAL TASK.
     *
     * Result:
     *
     * Submission:
     *     APPROVED
     *     100%
     *
     * PracticalTask:
     *     COMPLETED
     *     100%
     *
     * Next workflow can update:
     *
     *     Training Progress
     *     Competency
     *     Skill Gap
     */
    @Override
    public PracticalTaskSubmission approveSubmission(
            Long submissionId,
            User trainer,
            Integer score,
            String feedback
    ) {

        validateTrainer(trainer);
        validateSubmissionId(submissionId);

        /*
         * SECURITY:
         *
         * This query only returns submissions belonging to
         * employees assigned to this trainer.
         */
        PracticalTaskSubmission submission =
                practicalTaskSubmissionRepository
                        .findByIdAndPracticalTaskEmployeeTrainer(
                                submissionId,
                                trainer
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Submission not found or you are not authorized to review it."
                                )
                        );

        /*
         * Only SUBMITTED work can be approved.
         */
        if (submission.getStatus() !=
                PracticalTaskSubmission.SubmissionStatus.SUBMITTED) {

            throw new IllegalStateException(
                    "Only submitted work can be approved."
            );
        }

        PracticalTask practicalTask =
                submission.getPracticalTask();

        if (practicalTask == null) {

            throw new IllegalStateException(
                    "Practical task is missing for this submission."
            );
        }

        /*
         * Approve submission.
         *
         * This sets:
         *
         * status = APPROVED
         * progress = 100
         * reviewed = true
         * reviewedAt = now
         * score = trainer score
         */
        submission.approve(
                score,
                normalizeText(feedback)
        );

        /*
         * ONLY NOW can the practical task become completed.
         *
         * status = COMPLETED
         * progress = 100
         * completedAt = now
         */
        practicalTask.markCompleted();

        practicalTaskRepository.save(
                practicalTask
        );

        PracticalTaskSubmission savedSubmission =
                practicalTaskSubmissionRepository.save(
                        submission
                );

        log.info(
                "Practical task approved. trainerId={}, employeeId={}, taskId={}, submissionId={}, score={}",
                trainer.getId(),
                submission.getEmployee() != null
                        ? submission.getEmployee().getId()
                        : null,
                practicalTask.getId(),
                savedSubmission.getId(),
                savedSubmission.getScore()
        );

        return savedSubmission;
    }

    // =========================================================
    // TRAINER REQUEST IMPROVEMENT
    // =========================================================

    /**
     * Request improvement from an employee.
     *
     * Result:
     *
     * Submission:
     *     NEEDS_IMPROVEMENT
     *     99%
     *
     * PracticalTask:
     *     NEEDS_IMPROVEMENT
     *     99%
     *
     * Employee can then revise and resubmit.
     */
    @Override
    public PracticalTaskSubmission requestImprovement(
            Long submissionId,
            User trainer,
            String feedback
    ) {

        validateTrainer(trainer);
        validateSubmissionId(submissionId);

        if (feedback == null ||
                feedback.isBlank()) {

            throw new IllegalArgumentException(
                    "Trainer feedback is required when requesting improvement."
            );
        }

        /*
         * SECURITY:
         *
         * Only submissions belonging to this trainer's employees
         * can be reviewed.
         */
        PracticalTaskSubmission submission =
                practicalTaskSubmissionRepository
                        .findByIdAndPracticalTaskEmployeeTrainer(
                                submissionId,
                                trainer
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Submission not found or you are not authorized to review it."
                                )
                        );

        /*
         * Only SUBMITTED work can be sent for improvement.
         */
        if (submission.getStatus() !=
                PracticalTaskSubmission.SubmissionStatus.SUBMITTED) {

            throw new IllegalStateException(
                    "Only submitted work can be sent for improvement."
            );
        }

        PracticalTask practicalTask =
                submission.getPracticalTask();

        if (practicalTask == null) {

            throw new IllegalStateException(
                    "Practical task is missing for this submission."
            );
        }

        /*
         * Mark submission as needing improvement.
         */
        submission.requestImprovement(
                feedback.trim()
        );

        /*
         * Practical task also waits for employee revision.
         *
         * It remains below 100%.
         */
        practicalTask.markNeedsImprovement();

        practicalTaskRepository.save(
                practicalTask
        );

        PracticalTaskSubmission savedSubmission =
                practicalTaskSubmissionRepository.save(
                        submission
                );

        log.info(
                "Practical task sent for improvement. trainerId={}, employeeId={}, taskId={}, submissionId={}",
                trainer.getId(),
                submission.getEmployee() != null
                        ? submission.getEmployee().getId()
                        : null,
                practicalTask.getId(),
                savedSubmission.getId()
        );

        return savedSubmission;
    }

    // =========================================================
    // TRAINER REVIEW COUNT
    // =========================================================

    /**
     * Count submissions waiting for trainer review.
     */
    @Override
    @Transactional(readOnly = true)
    public long countPendingTrainerReviews(
            User trainer
    ) {

        validateTrainer(trainer);

        return practicalTaskSubmissionRepository
                .countByPracticalTaskEmployeeTrainerAndStatus(
                        trainer,
                        PracticalTaskSubmission.SubmissionStatus.SUBMITTED
                );
    }

    // =========================================================
    // EMPLOYEE SUBMISSION STATUS
    // =========================================================

    /**
     * Check whether an employee currently has a SUBMITTED
     * practical-task submission.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasSubmittedTask(
            Long practicalTaskId,
            User employee
    ) {

        validateEmployee(employee);
        validateTaskId(practicalTaskId);

        PracticalTask practicalTask =
                getEmployeeTask(
                        practicalTaskId,
                        employee
                );

        Optional<PracticalTaskSubmission> submission =
                practicalTaskSubmissionRepository
                        .findByPracticalTaskAndEmployee(
                                practicalTask,
                                employee
                        );

        return submission.isPresent()
                && submission.get().getStatus() ==
                PracticalTaskSubmission.SubmissionStatus.SUBMITTED;
    }

    // =========================================================
    // EMPLOYEE COUNTS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public long countSubmissions(
            User employee
    ) {

        validateEmployee(employee);

        return practicalTaskSubmissionRepository
                .countByEmployee(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSubmissionsByStatus(
            User employee,
            PracticalTaskSubmission.SubmissionStatus status
    ) {

        validateEmployee(employee);

        if (status == null) {
            return 0;
        }

        return practicalTaskSubmissionRepository
                .countByEmployeeAndStatus(
                        employee,
                        status
                );
    }

    // =========================================================
    // INTERNAL HELPERS
    // =========================================================

    /**
     * Get an employee-owned practical task.
     *
     * Prevents an employee from accessing another employee's task.
     */
    private PracticalTask getEmployeeTask(
            Long practicalTaskId,
            User employee
    ) {

        return practicalTaskRepository
                .findByIdAndEmployee(
                        practicalTaskId,
                        employee
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Practical task not found or access denied."
                        )
                );
    }

    /**
     * Validate employee.
     */
    private void validateEmployee(
            User employee
    ) {

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee cannot be null."
            );
        }

        if (employee.getId() == null) {

            throw new IllegalArgumentException(
                    "Employee must have a valid ID."
            );
        }
    }

    /**
     * Validate trainer.
     */
    private void validateTrainer(
            User trainer
    ) {

        if (trainer == null) {

            throw new IllegalArgumentException(
                    "Trainer cannot be null."
            );
        }

        if (trainer.getId() == null) {

            throw new IllegalArgumentException(
                    "Trainer must have a valid ID."
            );
        }
    }

    /**
     * Validate practical-task ID.
     */
    private void validateTaskId(
            Long practicalTaskId
    ) {

        if (practicalTaskId == null ||
                practicalTaskId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid practical task ID."
            );
        }
    }

    /**
     * Validate submission ID.
     */
    private void validateSubmissionId(
            Long submissionId
    ) {

        if (submissionId == null ||
                submissionId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid submission ID."
            );
        }
    }

    /**
     * Validate that at least a solution text or URL exists.
     */
    private void validateSubmissionContent(
            String submissionText,
            String submissionUrl
    ) {

        boolean hasText =
                submissionText != null &&
                !submissionText.isBlank();

        boolean hasUrl =
                submissionUrl != null &&
                !submissionUrl.isBlank();

        if (!hasText && !hasUrl) {

            throw new IllegalArgumentException(
                    "Please provide a solution description or submission URL."
            );
        }
    }

    /**
     * Normalize text before saving.
     */
    private String normalizeText(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }
}