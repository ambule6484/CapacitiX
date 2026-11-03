
package com.capacitix.controller;

import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.PracticalTaskSubmission;
import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.AssessmentService;
import com.capacitix.service.PracticalTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * =========================================================
 * TRAINER CONTROLLER
 * =========================================================
 *
 * Handles:
 * Dashboard
 * Employees
 * Skill Gaps
 * Practical Tasks
 * Practical Task Review
 * Progress
 * Reports
 * Profile
 * Settings
 *
 * =========================================================
 */
@Controller
@RequestMapping("/trainer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TRAINER')")
public class TrainerController {

    private final UserRepository userRepository;

    private final AssessmentService assessmentService;

    private final PracticalTaskService practicalTaskService;


    // =========================================================
    // TRAINER DASHBOARD
    // URL: /trainer/dashboard
    // =========================================================

    @GetMapping("/dashboard")
    public String dashboard(
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        addTrainerDetails(model, trainer);

        List<User> employees = getAssignedEmployees(trainer);

        model.addAttribute("totalTrainees", employees.size());
        model.addAttribute("totalEmployees", employees.size());

        model.addAttribute(
                "totalAssessments",
                assessmentService.getTotalAssessments()
        );

        model.addAttribute(
                "activeAssessments",
                assessmentService.getActiveAssessments()
        );

        model.addAttribute(
                "completedAssessments",
                assessmentService.getCompletedAssessments()
        );

        model.addAttribute(
                "generatedAssessments",
                assessmentService.getGeneratedAssessments()
        );

        model.addAttribute(
                "assignedAssessments",
                assessmentService.getAssignedAssessments()
        );

        model.addAttribute(
                "pendingAssessments",
                assessmentService.getPendingAssessments()
        );

        model.addAttribute("activeTrainees", 0);
        model.addAttribute("completedTrainees", 0);
        model.addAttribute("averageProgress", 0);

        long pendingTaskReviews =
                practicalTaskService.countPendingTrainerReviews(trainer);

        long completedTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.COMPLETED
                );

        long improvementTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.NEEDS_IMPROVEMENT
                );

        long activeTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.IN_PROGRESS
                );

        long pendingTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.PENDING
                );

        long submittedTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.SUBMITTED
                );

        long totalTasks =
                practicalTaskService.countTasksForTrainer(trainer);

        model.addAttribute("pendingTaskReviews", pendingTaskReviews);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("improvementTasks", improvementTasks);
        model.addAttribute("activeTasks", activeTasks);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("submittedTasks", submittedTasks);
        model.addAttribute("totalTasks", totalTasks);

        return "trainer/dashboard";
    }


    // =========================================================
    // MY EMPLOYEES
    // URL: /trainer/employees
    // =========================================================

    @GetMapping("/employees")
    public String myEmployees(
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        addTrainerDetails(model, trainer);

        List<User> employees = getAssignedEmployees(trainer);

        model.addAttribute("trainees", employees);
        model.addAttribute("employees", employees);
        model.addAttribute("totalTrainees", employees.size());
        model.addAttribute("totalEmployees", employees.size());

        return "trainer/my-trainees";
    }


    // =========================================================
    // SKILL GAPS
    // URL: /trainer/skill-gaps
    // =========================================================

    @GetMapping("/skill-gaps")
    public String skillGaps(
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        addTrainerDetails(model, trainer);

        List<User> employees = getAssignedEmployees(trainer);

        model.addAttribute("employees", employees);
        model.addAttribute("trainees", employees);
        model.addAttribute("totalTrainees", employees.size());
        model.addAttribute("totalEmployees", employees.size());

        model.addAttribute("traineesWithGaps", 0);
        model.addAttribute("totalSkillGaps", 0);

        return "trainer/skill-gap";
    }


    // =========================================================
    // PRACTICAL TASK DASHBOARD
    // URL: /trainer/practical-tasks
    // =========================================================

    @GetMapping("/practical-tasks")
    public String practicalTasks(
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        addTrainerDetails(model, trainer);

        List<User> loadedEmployees = getAssignedEmployees(trainer);

        final List<User> employees = loadedEmployees == null
                ? Collections.emptyList()
                : loadedEmployees;

        model.addAttribute("employees", employees);
        model.addAttribute("trainees", employees);

        List<PracticalTask> loadedTasks =
                practicalTaskService.getTrainerTasks(trainer);

        final List<PracticalTask> tasks = loadedTasks == null
                ? Collections.emptyList()
                : loadedTasks;

        model.addAttribute("tasks", tasks);
        model.addAttribute("practicalTasks", tasks);

        long totalTasks =
                practicalTaskService.countTasksForTrainer(trainer);

        long pendingTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.PENDING
                );

        long activeTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.IN_PROGRESS
                );

        long submittedTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.SUBMITTED
                );

        long improvementTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.NEEDS_IMPROVEMENT
                );

        long completedTasks =
                practicalTaskService.countTasksForTrainerByStatus(
                        trainer,
                        PracticalTask.TaskStatus.COMPLETED
                );

        List<PracticalTaskSubmission> loadedReviews =
                practicalTaskService.getPendingTrainerReviews(trainer);

        final List<PracticalTaskSubmission> pendingReviews =
                loadedReviews == null
                        ? Collections.emptyList()
                        : loadedReviews;

        long pendingTaskReviews = pendingReviews.size();

        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("activeTasks", activeTasks);
        model.addAttribute("submittedTasks", submittedTasks);
        model.addAttribute("improvementTasks", improvementTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("pendingTaskReviews", pendingTaskReviews);
        model.addAttribute("pendingReviews", pendingReviews);
        model.addAttribute("hasTasks", !tasks.isEmpty());

        return "trainer/practical-tasks";
    }


    // =========================================================
    // VIEW PRACTICAL TASK SUBMISSION
    // URL: /trainer/practical-tasks/review/{submissionId}
    // =========================================================

    @GetMapping("/practical-tasks/review/{submissionId}")
    public String reviewPracticalTask(
            @PathVariable Long submissionId,
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        PracticalTaskSubmission submission =
                practicalTaskService.getTrainerSubmission(
                        submissionId,
                        trainer
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Practical task submission not found or access denied."
                        )
                );

        addTrainerDetails(model, trainer);

        model.addAttribute("trainer", trainer);
        model.addAttribute("submission", submission);
        model.addAttribute("task", submission.getPracticalTask());
        model.addAttribute("employee", submission.getEmployee());

        return "trainer/practical-task-review";
    }


    // =========================================================
    // APPROVE PRACTICAL TASK
    // =========================================================

    @PostMapping("/practical-tasks/review/{submissionId}/approve")
    public String approvePracticalTask(
            @PathVariable Long submissionId,
            @RequestParam(name = "feedback", required = false)
            String feedback,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        practicalTaskService.approveTask(
                submissionId,
                trainer,
                feedback
        );

        return "redirect:/trainer/practical-tasks?approved=true";
    }


    // =========================================================
    // REQUEST IMPROVEMENT
    // =========================================================

    @PostMapping("/practical-tasks/review/{submissionId}/improve")
    public String requestImprovement(
            @PathVariable Long submissionId,
            @RequestParam(name = "feedback") String feedback,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        if (feedback == null || feedback.trim().isEmpty()) {
            return "redirect:/trainer/practical-tasks/review/"
                    + submissionId
                    + "?error=feedback-required";
        }

        practicalTaskService.requestImprovement(
                submissionId,
                trainer,
                feedback
        );

        return "redirect:/trainer/practical-tasks?improvementRequested=true";
    }


    // =========================================================
    // VIEW PRACTICAL TASK
    // URL: /trainer/practical-tasks/{taskId}
    // =========================================================

    @GetMapping("/practical-tasks/{taskId}")
    public String viewPracticalTask(
            @PathVariable Long taskId,
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        PracticalTask task =
                practicalTaskService.getTrainerTask(
                        taskId,
                        trainer
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Practical task not found or access denied."
                        )
                );

        PracticalTaskSubmission submission =
                practicalTaskService.getTrainerSubmissionForTask(
                        taskId,
                        trainer
                ).orElse(null);

        addTrainerDetails(model, trainer);

        model.addAttribute("trainer", trainer);
        model.addAttribute("task", task);
        model.addAttribute("employee", task.getEmployee());
        model.addAttribute("learningPathItem", task.getLearningPathItem());
        model.addAttribute("trainingModule", task.getTrainingModule());
        model.addAttribute("submission", submission);
        model.addAttribute("hasSubmission", submission != null);

        return "trainer/practical-task-details";
    }


    // =========================================================
    // TRAINER PROGRESS
    // URL: /trainer/progress
    // =========================================================

    @GetMapping("/progress")
    public String progress(
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        addTrainerDetails(model, trainer);

        List<User> loadedEmployees = getAssignedEmployees(trainer);

        final List<User> employees = loadedEmployees == null
                ? Collections.emptyList()
                : loadedEmployees;

        List<PracticalTask> loadedTasks =
                practicalTaskService.getTrainerTasks(trainer);

        final List<PracticalTask> tasks = loadedTasks == null
                ? Collections.emptyList()
                : loadedTasks;

        long activeTrainees = employees.stream()
                .filter(employee -> tasks.stream().anyMatch(task ->
                        task.getEmployee() != null
                                && task.getEmployee().getId() != null
                                && employee.getId() != null
                                && task.getEmployee().getId()
                                        .equals(employee.getId())
                                && (task.getStatus()
                                        == PracticalTask.TaskStatus.IN_PROGRESS
                                    || task.getStatus()
                                        == PracticalTask.TaskStatus.SUBMITTED
                                    || task.getStatus()
                                        == PracticalTask.TaskStatus.NEEDS_IMPROVEMENT)
                ))
                .count();

        long completedTrainees = employees.stream()
                .filter(employee -> tasks.stream().anyMatch(task ->
                        task.getEmployee() != null
                                && task.getEmployee().getId() != null
                                && employee.getId() != null
                                && task.getEmployee().getId()
                                        .equals(employee.getId())
                                && task.getStatus()
                                        == PracticalTask.TaskStatus.COMPLETED
                ))
                .count();

        double averageProgress = employees.isEmpty()
                ? 0.0
                : employees.stream()
                        .mapToInt(employee -> {
                            List<PracticalTask> employeeTasks =
                                    tasks.stream()
                                            .filter(task ->
                                                    task.getEmployee() != null
                                                            && task.getEmployee().getId() != null
                                                            && employee.getId() != null
                                                            && task.getEmployee().getId()
                                                                    .equals(employee.getId())
                                            )
                                            .toList();

                            if (employeeTasks.isEmpty()) {
                                return 0;
                            }

                            return (int) Math.round(
                                    employeeTasks.stream()
                                            .mapToInt(task -> {
                                                if (task.getStatus()
                                                        == PracticalTask.TaskStatus.COMPLETED) {
                                                    return 100;
                                                }

                                                Integer progress =
                                                        task.getProgressPercentage();

                                                return progress == null
                                                        ? 0
                                                        : Math.max(
                                                                0,
                                                                Math.min(100, progress)
                                                        );
                                            })
                                            .average()
                                            .orElse(0.0)
                            );
                        })
                        .average()
                        .orElse(0.0);

        model.addAttribute("employees", employees);
        model.addAttribute("trainees", employees);
        model.addAttribute("totalTrainees", employees.size());
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("activeTrainees", activeTrainees);
        model.addAttribute("completedTrainees", completedTrainees);
        model.addAttribute("averageProgress", Math.round(averageProgress));

        return "trainer/progress";
    }


    // =========================================================
    // REPORTS
    // URL: /trainer/reports
    // =========================================================

    @GetMapping("/reports")
    public String reports(
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        addTrainerDetails(model, trainer);

        List<User> employees = getAssignedEmployees(trainer);

        model.addAttribute("employees", employees);
        model.addAttribute("trainees", employees);
        model.addAttribute("totalTrainees", employees.size());
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("totalReports", 0);

        model.addAttribute(
                "completedAssessments",
                assessmentService.getCompletedAssessments()
        );

        model.addAttribute("averageProgress", 0);

        return "trainer/reports";
    }


    // =========================================================
    // TRAINER PROFILE
    // URL: /trainer/profile
    // =========================================================

    @GetMapping("/profile")
    public String profile(
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        addTrainerDetails(model, trainer);

        model.addAttribute("trainer", trainer);

        return "trainer/profile";
    }


    // =========================================================
    // TRAINER SETTINGS
    // URL: /trainer/settings
    // =========================================================

    @GetMapping("/settings")
    public String settings(
            Model model,
            Authentication authentication) {

        User trainer = getLoggedInTrainer(authentication);

        addTrainerDetails(model, trainer);

        model.addAttribute("trainer", trainer);

        return "trainer/settings";
    }


    // =========================================================
    // GET ASSIGNED EMPLOYEES
    // =========================================================

    private List<User> getAssignedEmployees(User trainer) {

        if (trainer == null) {
            return Collections.emptyList();
        }

        List<User> employees =
                userRepository.findByTrainerAndRoleOrderByFullNameAsc(
                        trainer,
                        User.Role.EMPLOYEE
                );

        return employees == null
                ? Collections.emptyList()
                : employees;
    }


    // =========================================================
    // GET LOGGED-IN TRAINER
    // =========================================================

    private User getLoggedInTrainer(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "Trainer authentication required"
            );
        }

        String email = authentication.getName();

        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException(
                    "Trainer email not found"
            );
        }

        User trainer =
                userRepository.findByEmail(email.trim())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Trainer not found: " + email
                                )
                        );

        if (trainer.getRole() != User.Role.TRAINER) {
            throw new RuntimeException(
                    "Access denied: User is not a trainer"
            );
        }

        if (!trainer.isEnabled()) {
            throw new RuntimeException(
                    "Trainer account is disabled"
            );
        }

        return trainer;
    }


    // =========================================================
    // ADD TRAINER DETAILS TO MODEL
    // =========================================================

    private void addTrainerDetails(
            Model model,
            User trainer) {

        String userName = "Trainer";
        String userInitial = "T";

        if (trainer != null) {

            String fullName = trainer.getFullName();

            if (fullName != null && !fullName.trim().isEmpty()) {

                fullName = fullName.trim();

                userName = fullName;

                userInitial = fullName
                        .substring(0, 1)
                        .toUpperCase();
            }
        }

        model.addAttribute("userName", userName);
        model.addAttribute("userInitial", userInitial);

        if (trainer != null) {
            model.addAttribute("trainer", trainer);
        }
    }
}
