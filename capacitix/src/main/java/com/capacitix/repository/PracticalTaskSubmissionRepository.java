
package com.capacitix.repository;

import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.PracticalTaskSubmission;
import com.capacitix.entity.User;

import org.springframework.data.jpa.repository.EntityGraph;
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

    List<PracticalTaskSubmission>
    findByEmployeeOrderByCreatedAtDesc(
            User employee
    );

    List<PracticalTaskSubmission>
    findByEmployeeIdOrderByCreatedAtDesc(
            Long employeeId
    );

    Optional<PracticalTaskSubmission>
    findByIdAndEmployee(
            Long id,
            User employee
    );

    @EntityGraph(attributePaths = {
            "practicalTask",
            "practicalTask.trainingModule",
            "practicalTask.learningPathItem",
            "practicalTask.learningPathItem.learningPath"
    })
    Optional<PracticalTaskSubmission>
    findByPracticalTaskAndEmployee(
            PracticalTask practicalTask,
            User employee
    );

    boolean existsByPracticalTaskAndEmployee(
            PracticalTask practicalTask,
            User employee
    );

    List<PracticalTaskSubmission>
    findByEmployeeAndStatusOrderByCreatedAtDesc(
            User employee,
            PracticalTaskSubmission.SubmissionStatus status
    );


    // ============================================================
    // PRACTICAL TASK SUBMISSIONS
    // ============================================================

    @EntityGraph(attributePaths = {
            "practicalTask",
            "practicalTask.trainingModule",
            "practicalTask.learningPathItem",
            "practicalTask.learningPathItem.learningPath",
            "employee"
    })
    List<PracticalTaskSubmission>
    findByPracticalTaskOrderByCreatedAtDesc(
            PracticalTask practicalTask
    );

    long countByPracticalTask(
            PracticalTask practicalTask
    );


    // ============================================================
    // TRAINER REVIEW
    // ============================================================

    @EntityGraph(attributePaths = {
            "practicalTask",
            "practicalTask.employee",
            "practicalTask.employee.trainer",
            "practicalTask.trainingModule",
            "practicalTask.learningPathItem",
            "practicalTask.learningPathItem.learningPath",
            "employee",
            "employee.trainer"
    })
    List<PracticalTaskSubmission>
    findByPracticalTaskEmployeeTrainerAndStatusOrderBySubmittedAtDesc(
            User trainer,
            PracticalTaskSubmission.SubmissionStatus status
    );


    @EntityGraph(attributePaths = {
            "practicalTask",
            "practicalTask.employee",
            "practicalTask.employee.trainer",
            "practicalTask.trainingModule",
            "practicalTask.learningPathItem",
            "practicalTask.learningPathItem.learningPath",
            "employee",
            "employee.trainer"
    })
    List<PracticalTaskSubmission>
    findByPracticalTaskEmployeeTrainerOrderBySubmittedAtDesc(
            User trainer
    );


    @EntityGraph(attributePaths = {
            "practicalTask",
            "practicalTask.employee",
            "practicalTask.employee.trainer",
            "practicalTask.trainingModule",
            "practicalTask.learningPathItem",
            "practicalTask.learningPathItem.learningPath",
            "employee",
            "employee.trainer"
    })
    Optional<PracticalTaskSubmission>
    findByIdAndPracticalTaskEmployeeTrainer(
            Long submissionId,
            User trainer
    );


    long countByPracticalTaskEmployeeTrainerAndStatus(
            User trainer,
            PracticalTaskSubmission.SubmissionStatus status
    );


    // ============================================================
    // EMPLOYEE COUNTS
    // ============================================================

    long countByEmployee(
            User employee
    );

    long countByEmployeeAndStatus(
            User employee,
            PracticalTaskSubmission.SubmissionStatus status
    );
}
