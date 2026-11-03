package com.capacitix.service;

import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.PracticalTaskSubmission;
import com.capacitix.entity.TrainingModule;
import com.capacitix.entity.User;
import com.capacitix.repository.PracticalTaskRepository;
import com.capacitix.repository.PracticalTaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PracticalTaskServiceImpl implements PracticalTaskService {

    private final PracticalTaskRepository practicalTaskRepository;

    private final PracticalTaskSubmissionRepository
            practicalTaskSubmissionRepository;

    private final AIPracticalTaskGenerator
            aiPracticalTaskGenerator;

    @Override
    public List<PracticalTask> generateTasksForLearningPath(
            User employee,
            LearningPath learningPath
    ) {

        validateEmployee(employee);

        if (learningPath == null) {
            throw new IllegalArgumentException(
                    "Learning path cannot be null."
            );
        }

        if (learningPath.getId() == null) {
            throw new IllegalArgumentException(
                    "Learning path must have a valid ID."
            );
        }

        if (learningPath.getEmployee() == null
                || learningPath.getEmployee().getId() == null
                || !learningPath.getEmployee()
                .getId()
                .equals(employee.getId())) {

            throw new IllegalArgumentException(
                    "This learning path does not belong to the employee."
            );
        }

        if (learningPath.getItems() == null
                || learningPath.getItems().isEmpty()) {

            return Collections.emptyList();
        }

        List<PracticalTask> allTasks = new ArrayList<>();

        for (LearningPathItem item : learningPath.getItems()) {

            if (item == null) {
                continue;
            }

            try {

                List<PracticalTask> itemTasks =
                        generateTasksForLearningPathItem(
                                employee,
                                item
                        );

                if (itemTasks != null
                        && !itemTasks.isEmpty()) {

                    allTasks.addAll(itemTasks);
                }

            } catch (Exception exception) {

                log.error(
                        "Failed to generate practical tasks. employeeId={}, itemId={}",
                        employee.getId(),
                        item.getId(),
                        exception
                );
            }
        }

        return allTasks;
    }

    @Override
    public List<PracticalTask> generateTasksForLearningPathItem(
            User employee,
            LearningPathItem learningPathItem
    ) {

        validateEmployee(employee);

        if (learningPathItem == null) {
            throw new IllegalArgumentException(
                    "Learning path item cannot be null."
            );
        }

        if (learningPathItem.getId() == null) {
            throw new IllegalArgumentException(
                    "Learning path item must have a valid ID."
            );
        }

        LearningPath learningPath =
                learningPathItem.getLearningPath();

        if (learningPath == null) {
            throw new IllegalArgumentException(
                    "Learning path item is not connected to a learning path."
            );
        }

        if (learningPath.getEmployee() == null
                || learningPath.getEmployee().getId() == null
                || !learningPath.getEmployee()
                .getId()
                .equals(employee.getId())) {

            throw new IllegalArgumentException(
                    "This learning path item does not belong to the employee."
            );
        }

        if (hasTasksForLearningPathItem(
                employee,
                learningPathItem
        )) {

            return practicalTaskRepository
                    .findByEmployeeAndLearningPathItemOrderByTaskOrderAsc(
                            employee,
                            learningPathItem
                    );
        }

        TrainingModule trainingModule =
                learningPathItem.getTrainingModule();

        List<PracticalTask> generatedTasks;

        try {

            generatedTasks =
                    aiPracticalTaskGenerator.generateTasksForItem(
                            employee,
                            learningPathItem,
                            trainingModule
                    );

        } catch (Exception exception) {

            log.error(
                    "AI practical task generation failed. employeeId={}, itemId={}",
                    employee.getId(),
                    learningPathItem.getId(),
                    exception
            );

            return Collections.emptyList();
        }

        if (generatedTasks == null
                || generatedTasks.isEmpty()) {

            return Collections.emptyList();
        }

        List<PracticalTask> tasksToSave =
                new ArrayList<>();

        int taskOrder = 1;

        for (PracticalTask task : generatedTasks) {

            if (task == null) {
                continue;
            }

            task.setEmployee(employee);
            task.setLearningPathItem(learningPathItem);
            task.setTrainingModule(trainingModule);

            if (task.getSkillName() == null
                    || task.getSkillName().isBlank()) {

                String targetSkill =
                        learningPathItem.getTargetSkill();

                if (targetSkill != null
                        && !targetSkill.isBlank()) {

                    task.setSkillName(
                            targetSkill.trim()
                    );
                }
            }

            if (task.getSkillName() == null
                    || task.getSkillName().isBlank()) {

                continue;
            }

            task.setStatus(
                    PracticalTask.TaskStatus.PENDING
            );

            task.setProgressPercentage(0);

            task.setAiGenerated(true);

            task.setTaskOrder(taskOrder++);

            tasksToSave.add(task);
        }

        if (tasksToSave.isEmpty()) {
            return Collections.emptyList();
        }

        return practicalTaskRepository.saveAll(
                tasksToSave
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticalTask> getEmployeeTasks(
            User employee
    ) {

        validateEmployee(employee);

        return practicalTaskRepository
                .findByEmployeeOrderByCreatedAtDesc(
                        employee
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PracticalTask> getEmployeeTask(
            Long taskId,
            User employee
    ) {

        validateEmployee(employee);
        validateTaskId(taskId);

        return practicalTaskRepository
                .findByIdAndEmployee(
                        taskId,
                        employee
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticalTask> getEmployeeTasksBySkill(
            User employee,
            String skillName
    ) {

        validateEmployee(employee);

        if (skillName == null
                || skillName.isBlank()) {

            return Collections.emptyList();
        }

        return practicalTaskRepository
                .findByEmployeeAndSkillNameIgnoreCaseOrderByCreatedAtDesc(
                        employee,
                        skillName.trim()
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticalTask> getEmployeeTasksByStatus(
            User employee,
            PracticalTask.TaskStatus status
    ) {

        validateEmployee(employee);

        if (status == null) {
            return Collections.emptyList();
        }

        return practicalTaskRepository
                .findByEmployeeAndStatusOrderByCreatedAtDesc(
                        employee,
                        status
                );
    }

    @Override
    public PracticalTask startTask(
            Long taskId,
            User employee
    ) {

        PracticalTask task =
                getRequiredEmployeeTask(
                        taskId,
                        employee
                );

        if (task.isCompleted()) {
            return task;
        }

        if (task.isSubmitted()) {

            throw new IllegalStateException(
                    "This task has already been submitted and is waiting for trainer review."
            );
        }

        task.startTask();

        if (task.getProgressPercentage() == null
                || task.getProgressPercentage() > 99) {

            task.setProgressPercentage(1);
        }

        return practicalTaskRepository.save(task);
    }

    @Override
    public PracticalTask updateProgress(
            Long taskId,
            User employee,
            Integer progressPercentage
    ) {

        PracticalTask task =
                getRequiredEmployeeTask(
                        taskId,
                        employee
                );

        if (progressPercentage == null) {

            throw new IllegalArgumentException(
                    "Progress percentage cannot be null."
            );
        }

        if (progressPercentage < 0
                || progressPercentage > 99) {

            throw new IllegalArgumentException(
                    "Employee progress must be between 0 and 99%. 100% is reserved for trainer approval."
            );
        }

        if (task.isCompleted()) {
            return task;
        }

        if (task.isSubmitted()) {

            throw new IllegalStateException(
                    "Submitted task cannot be modified until trainer review."
            );
        }

        task.updateProgress(progressPercentage);

        return practicalTaskRepository.save(task);
    }

    @Override
    public PracticalTaskSubmission submitTask(
            Long taskId,
            User employee,
            String solutionDescription,
            String submissionUrl
    ) {

        validateEmployee(employee);
        validateTaskId(taskId);

        PracticalTask task =
                getRequiredEmployeeTask(
                        taskId,
                        employee
                );

        if (task.isCompleted()) {

            throw new IllegalStateException(
                    "This practical task has already been approved and completed."
            );
        }

        if (task.isSubmitted()) {

            throw new IllegalStateException(
                    "This practical task has already been submitted and is waiting for trainer review."
            );
        }

        if (solutionDescription == null
                || solutionDescription.isBlank()) {

            throw new IllegalArgumentException(
                    "Solution description cannot be empty."
            );
        }

        String cleanSolutionDescription =
                solutionDescription.trim();

        String cleanSubmissionUrl = null;

        if (submissionUrl != null
                && !submissionUrl.isBlank()) {

            cleanSubmissionUrl =
                    submissionUrl.trim();
        }

        PracticalTaskSubmission submission =
                practicalTaskSubmissionRepository
                        .findByPracticalTaskAndEmployee(
                                task,
                                employee
                        )
                        .orElseGet(() -> {

                            PracticalTaskSubmission newSubmission =
                                    new PracticalTaskSubmission();

                            newSubmission.setPracticalTask(task);
                            newSubmission.setEmployee(employee);

                            return newSubmission;
                        });

        if (submission.needsImprovement()) {
            submission.prepareForResubmission();
        }

        if (submission.getStatus()
                == PracticalTaskSubmission.SubmissionStatus.APPROVED) {

            throw new IllegalStateException(
                    "This submission has already been approved."
            );
        }

        submission.setSolutionDescription(
                cleanSolutionDescription
        );

        submission.setSubmissionUrl(
                cleanSubmissionUrl
        );

        if (submission.getStartedAt() == null) {
            submission.start();
        }

        submission.submit();

        PracticalTaskSubmission savedSubmission =
                practicalTaskSubmissionRepository.save(
                        submission
                );

        task.markSubmitted();

        practicalTaskRepository.save(task);

        log.info(
                "Practical task submitted. employeeId={}, taskId={}, submissionId={}",
                employee.getId(),
                task.getId(),
                savedSubmission.getId()
        );

        return savedSubmission;
    }

    @Override
    public PracticalTaskSubmission prepareForResubmission(
            Long taskId,
            User employee
    ) {

        validateEmployee(employee);
        validateTaskId(taskId);

        PracticalTask task =
                getRequiredEmployeeTask(
                        taskId,
                        employee
                );

        if (task.isCompleted()) {

            throw new IllegalStateException(
                    "Completed practical task cannot be resubmitted."
            );
        }

        PracticalTaskSubmission submission =
                practicalTaskSubmissionRepository
                        .findByPracticalTaskAndEmployee(
                                task,
                                employee
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No practical-task submission exists."
                                )
                        );

        if (!submission.needsImprovement()) {

            throw new IllegalStateException(
                    "This submission is not waiting for improvement."
            );
        }

        submission.prepareForResubmission();

        task.startTask();

        if (task.getProgressPercentage() == null
                || task.getProgressPercentage() > 99) {

            task.setProgressPercentage(1);
        }

        practicalTaskRepository.save(task);

        return practicalTaskSubmissionRepository.save(
                submission
        );
    }

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

    @Override
    @Transactional(readOnly = true)
    public Optional<PracticalTaskSubmission> getTrainerSubmission(
            Long submissionId,
            User trainer
    ) {

        validateTrainer(trainer);
        validateSubmissionId(submissionId);

        return practicalTaskSubmissionRepository
                .findByIdAndPracticalTaskEmployeeTrainer(
                        submissionId,
                        trainer
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PracticalTaskSubmission> getTrainerSubmissionForTask(
            Long taskId,
            User trainer
    ) {

        validateTrainer(trainer);
        validateTaskId(taskId);

        return getTrainerTask(taskId, trainer)
                .flatMap(task -> {

                    User employee =
                            task.getEmployee();

                    if (employee == null
                            || employee.getId() == null) {

                        return Optional.empty();
                    }

                    return practicalTaskSubmissionRepository
                            .findByPracticalTaskAndEmployee(
                                    task,
                                    employee
                            );
                });
    }

    @Override
    public PracticalTaskSubmission approveTask(
            Long submissionId,
            User trainer,
            String feedback
    ) {

        validateTrainer(trainer);
        validateSubmissionId(submissionId);

        PracticalTaskSubmission submission =
                practicalTaskSubmissionRepository
                        .findByIdAndPracticalTaskEmployeeTrainer(
                                submissionId,
                                trainer
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Submission not found or access denied."
                                )
                        );

        if (!submission.isSubmitted()) {

            throw new IllegalStateException(
                    "Only submitted work can be approved."
            );
        }

        PracticalTask task =
                submission.getPracticalTask();

        if (task == null) {

            throw new IllegalStateException(
                    "Submission is not connected to a practical task."
            );
        }

        User employee =
                submission.getEmployee();

        if (employee == null) {

            throw new IllegalStateException(
                    "Submission is not connected to an employee."
            );
        }

        if (employee.getTrainer() == null
                || employee.getTrainer().getId() == null
                || !employee.getTrainer()
                .getId()
                .equals(trainer.getId())) {

            throw new IllegalStateException(
                    "You are not the assigned trainer for this employee."
            );
        }

        submission.approve(
                null,
                cleanFeedback(feedback)
        );

        task.markCompleted();

        practicalTaskRepository.save(task);

        PracticalTaskSubmission savedSubmission =
                practicalTaskSubmissionRepository.save(
                        submission
                );

        log.info(
                "Practical task approved. trainerId={}, employeeId={}, taskId={}, submissionId={}",
                trainer.getId(),
                employee.getId(),
                task.getId(),
                submission.getId()
        );

        return savedSubmission;
    }

    @Override
    public PracticalTaskSubmission requestImprovement(
            Long submissionId,
            User trainer,
            String feedback
    ) {

        validateTrainer(trainer);
        validateSubmissionId(submissionId);

        if (feedback == null
                || feedback.isBlank()) {

            throw new IllegalArgumentException(
                    "Trainer feedback is required when requesting improvement."
            );
        }

        PracticalTaskSubmission submission =
                practicalTaskSubmissionRepository
                        .findByIdAndPracticalTaskEmployeeTrainer(
                                submissionId,
                                trainer
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Submission not found or access denied."
                                )
                        );

        if (!submission.isSubmitted()) {

            throw new IllegalStateException(
                    "Only submitted work can be sent for improvement."
            );
        }

        PracticalTask task =
                submission.getPracticalTask();

        if (task == null) {

            throw new IllegalStateException(
                    "Submission is not connected to a practical task."
            );
        }

        User employee =
                submission.getEmployee();

        if (employee == null) {

            throw new IllegalStateException(
                    "Submission is not connected to an employee."
            );
        }

        if (employee.getTrainer() == null
                || employee.getTrainer().getId() == null
                || !employee.getTrainer()
                .getId()
                .equals(trainer.getId())) {

            throw new IllegalStateException(
                    "You are not the assigned trainer for this employee."
            );
        }

        submission.requestImprovement(
                feedback.trim()
        );

        task.markNeedsImprovement();

        if (task.getProgressPercentage() == null
                || task.getProgressPercentage() > 99) {

            task.setProgressPercentage(99);
        }

        practicalTaskRepository.save(task);

        return practicalTaskSubmissionRepository.save(
                submission
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PracticalTask> getTrainerTask(
            Long taskId,
            User trainer
    ) {

        validateTrainer(trainer);
        validateTaskId(taskId);

        return practicalTaskRepository
                .findByIdAndEmployeeTrainer(
                        taskId,
                        trainer
                );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasTasksForLearningPathItem(
            User employee,
            LearningPathItem learningPathItem
    ) {

        validateEmployee(employee);

        if (learningPathItem == null
                || learningPathItem.getId() == null) {

            return false;
        }

        return practicalTaskRepository
                .existsByEmployeeAndLearningPathItem(
                        employee,
                        learningPathItem
                );
    }

    @Override
    @Transactional(readOnly = true)
    public long countTasks(
            User employee
    ) {

        validateEmployee(employee);

        return practicalTaskRepository
                .countByEmployee(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTasksByStatus(
            User employee,
            PracticalTask.TaskStatus status
    ) {

        validateEmployee(employee);

        if (status == null) {
            return 0;
        }

        return practicalTaskRepository
                .countByEmployeeAndStatus(
                        employee,
                        status
                );
    }

    @Override
    @Transactional(readOnly = true)
    public long countTasksBySkill(
            User employee,
            String skillName
    ) {

        validateEmployee(employee);

        if (skillName == null
                || skillName.isBlank()) {

            return 0;
        }

        return practicalTaskRepository
                .countByEmployeeAndSkillNameIgnoreCase(
                        employee,
                        skillName.trim()
                );
    }

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

    @Override
    @Transactional(readOnly = true)
    public long countCompletedTasksForTrainer(
            User trainer
    ) {

        validateTrainer(trainer);

        return practicalTaskSubmissionRepository
                .countByPracticalTaskEmployeeTrainerAndStatus(
                        trainer,
                        PracticalTaskSubmission.SubmissionStatus.APPROVED
                );
    }

    @Override
    @Transactional(readOnly = true)
    public long countImprovementTasksForTrainer(
            User trainer
    ) {

        validateTrainer(trainer);

        return practicalTaskSubmissionRepository
                .countByPracticalTaskEmployeeTrainerAndStatus(
                        trainer,
                        PracticalTaskSubmission.SubmissionStatus.NEEDS_IMPROVEMENT
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticalTask> getTrainerTasks(
            User trainer
    ) {

        validateTrainer(trainer);

        return practicalTaskRepository
                .findByEmployeeTrainerOrderByCreatedAtDesc(
                        trainer
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticalTask> getTrainerTasksByStatus(
            User trainer,
            PracticalTask.TaskStatus status
    ) {

        validateTrainer(trainer);

        if (status == null) {
            return Collections.emptyList();
        }

        return practicalTaskRepository
                .findByEmployeeTrainerAndStatusOrderByCreatedAtDesc(
                        trainer,
                        status
                );
    }

    @Override
    @Transactional(readOnly = true)
    public long countTasksForTrainer(
            User trainer
    ) {

        validateTrainer(trainer);

        return practicalTaskRepository
                .countByEmployeeTrainer(
                        trainer
                );
    }

    @Override
    @Transactional(readOnly = true)
    public long countTasksForTrainerByStatus(
            User trainer,
            PracticalTask.TaskStatus status
    ) {

        validateTrainer(trainer);

        if (status == null) {
            return 0;
        }

        return practicalTaskRepository
                .countByEmployeeTrainerAndStatus(
                        trainer,
                        status
                );
    }

    private PracticalTask getRequiredEmployeeTask(
            Long taskId,
            User employee
    ) {

        validateEmployee(employee);
        validateTaskId(taskId);

        return practicalTaskRepository
                .findByIdAndEmployee(
                        taskId,
                        employee
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Practical task not found or access denied."
                        )
                );
    }

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

        if (employee.getRole()
                != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Only employees can perform employee practical-task actions."
            );
        }

        if (!employee.isEnabled()) {
            throw new IllegalArgumentException(
                    "Employee account is disabled."
            );
        }
    }

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

        if (trainer.getRole()
                != User.Role.TRAINER) {

            throw new IllegalArgumentException(
                    "Only trainers can review practical-task submissions."
            );
        }

        if (!trainer.isEnabled()) {
            throw new IllegalArgumentException(
                    "Trainer account is disabled."
            );
        }
    }

    private void validateTaskId(
            Long taskId
    ) {

        if (taskId == null
                || taskId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid practical task ID."
            );
        }
    }

    private void validateSubmissionId(
            Long submissionId
    ) {

        if (submissionId == null
                || submissionId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid practical-task submission ID."
            );
        }
    }

    private String cleanFeedback(
            String feedback
    ) {

        if (feedback == null
                || feedback.isBlank()) {

            return null;
        }

        return feedback.trim();
    }
}