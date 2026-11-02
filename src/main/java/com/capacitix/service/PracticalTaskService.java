package com.capacitix.service;

import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.PracticalTaskSubmission;
import com.capacitix.entity.User;

import java.util.List;
import java.util.Optional;

public interface PracticalTaskService {

    List<PracticalTask> generateTasksForLearningPath(
            User employee,
            LearningPath learningPath
    );

    List<PracticalTask> generateTasksForLearningPathItem(
            User employee,
            LearningPathItem learningPathItem
    );

    List<PracticalTask> getEmployeeTasks(
            User employee
    );

    Optional<PracticalTask> getEmployeeTask(
            Long taskId,
            User employee
    );

    List<PracticalTask> getEmployeeTasksBySkill(
            User employee,
            String skillName
    );

    List<PracticalTask> getEmployeeTasksByStatus(
            User employee,
            PracticalTask.TaskStatus status
    );

    PracticalTask startTask(
            Long taskId,
            User employee
    );

    PracticalTask updateProgress(
            Long taskId,
            User employee,
            Integer progressPercentage
    );

    PracticalTaskSubmission submitTask(
            Long taskId,
            User employee,
            String solutionDescription,
            String submissionUrl
    );

    PracticalTaskSubmission prepareForResubmission(
            Long taskId,
            User employee
    );

    List<PracticalTaskSubmission> getPendingTrainerReviews(
            User trainer
    );

    Optional<PracticalTaskSubmission> getTrainerSubmission(
            Long submissionId,
            User trainer
    );

    Optional<PracticalTaskSubmission> getTrainerSubmissionForTask(
            Long taskId,
            User trainer
    );

    PracticalTaskSubmission approveTask(
            Long submissionId,
            User trainer,
            String feedback
    );

    PracticalTaskSubmission requestImprovement(
            Long submissionId,
            User trainer,
            String feedback
    );

    Optional<PracticalTask> getTrainerTask(
            Long taskId,
            User trainer
    );

    boolean hasTasksForLearningPathItem(
            User employee,
            LearningPathItem learningPathItem
    );

    long countTasks(
            User employee
    );

    long countTasksByStatus(
            User employee,
            PracticalTask.TaskStatus status
    );

    long countTasksBySkill(
            User employee,
            String skillName
    );

    long countPendingTrainerReviews(
            User trainer
    );

    long countCompletedTasksForTrainer(
            User trainer
    );

    long countImprovementTasksForTrainer(
            User trainer
    );

    List<PracticalTask> getTrainerTasks(
            User trainer
    );

    List<PracticalTask> getTrainerTasksByStatus(
            User trainer,
            PracticalTask.TaskStatus status
    );

    long countTasksForTrainer(
            User trainer
    );

    long countTasksForTrainerByStatus(
            User trainer,
            PracticalTask.TaskStatus status
    );
}