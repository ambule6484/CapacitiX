
package com.capacitix.controller;

import com.capacitix.entity.LearningPath;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.User;

import com.capacitix.repository.UserRepository;

import com.capacitix.service.LearningPathService;
import com.capacitix.service.PracticalTaskService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/employee/practical-tasks")
@PreAuthorize("hasRole('EMPLOYEE')")
public class PracticalTaskController {

    private final UserRepository userRepository;
    private final LearningPathService learningPathService;
    private final PracticalTaskService practicalTaskService;


    // =========================================================
    // PRACTICAL TASK DASHBOARD
    // =========================================================

    @GetMapping
    @Transactional
    public String practicalTasks(
            Authentication authentication,
            Model model
    ) {

        User employee = getAuthenticatedEmployee(authentication);

        LearningPath learningPath =
                getEmployeeLearningPath(employee);

        initializeLearningPath(learningPath);


        // =====================================================
        // GENERATE MISSING PRACTICAL TASKS
        // =====================================================

        List<PracticalTask> generatedTasks =
                Collections.emptyList();

        if (learningPath != null
                && learningPath.getItems() != null
                && !learningPath.getItems().isEmpty()) {

            generatedTasks =
                    practicalTaskService.generateTasksForLearningPath(
                            employee,
                            learningPath
                    );
        }


        // =====================================================
        // LOAD EMPLOYEE TASKS
        // =====================================================

        List<PracticalTask> practicalTasks =
                practicalTaskService.getEmployeeTasks(employee);

        if (practicalTasks == null) {
            practicalTasks = Collections.emptyList();
        }


        // =====================================================
        // TASK COUNTS
        // =====================================================

        long totalTasks =
                practicalTaskService.countTasks(employee);

        long completedTasks =
                practicalTaskService.countTasksByStatus(
                        employee,
                        PracticalTask.TaskStatus.COMPLETED
                );

        long inProgressTasks =
                practicalTaskService.countTasksByStatus(
                        employee,
                        PracticalTask.TaskStatus.IN_PROGRESS
                );

        long pendingTasks =
                practicalTaskService.countTasksByStatus(
                        employee,
                        PracticalTask.TaskStatus.PENDING
                );

        long submittedTasks =
                practicalTaskService.countTasksByStatus(
                        employee,
                        PracticalTask.TaskStatus.SUBMITTED
                );

        long needsImprovementTasks =
                practicalTaskService.countTasksByStatus(
                        employee,
                        PracticalTask.TaskStatus.NEEDS_IMPROVEMENT
                );


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute("employee", employee);
        model.addAttribute("learningPath", learningPath);

        model.addAttribute("practicalTasks", practicalTasks);
        model.addAttribute("tasks", practicalTasks);

        model.addAttribute("generatedTasks", generatedTasks);

        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("inProgressTasks", inProgressTasks);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("submittedTasks", submittedTasks);
        model.addAttribute("needsImprovementTasks", needsImprovementTasks);

        model.addAttribute("hasLearningPath", learningPath != null);
        model.addAttribute("hasTasks", !practicalTasks.isEmpty());

        return "employee/practical-tasks";
    }


    // =========================================================
    // VIEW SINGLE PRACTICAL TASK
    // =========================================================

    @GetMapping("/{taskId}")
    public String viewTask(
            @PathVariable Long taskId,
            Authentication authentication,
            Model model
    ) {

        User employee = getAuthenticatedEmployee(authentication);

        PracticalTask task =
                practicalTaskService.getEmployeeTask(taskId, employee)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Practical task not found or access denied."
                                )
                        );

        model.addAttribute("employee", employee);
        model.addAttribute("task", task);

        return "employee/practical-task-details";
    }


    // =========================================================
    // START TASK
    // =========================================================

    @PostMapping("/{taskId}/start")
    public String startTask(
            @PathVariable Long taskId,
            Authentication authentication
    ) {

        User employee = getAuthenticatedEmployee(authentication);

        practicalTaskService.startTask(taskId, employee);

        return "redirect:/employee/practical-tasks/" + taskId;
    }


    // =========================================================
    // UPDATE TASK PROGRESS
    // =========================================================

    @PostMapping("/{taskId}/progress")
    public String updateProgress(
            @PathVariable Long taskId,
            @RequestParam Integer progressPercentage,
            Authentication authentication
    ) {

        User employee = getAuthenticatedEmployee(authentication);

        practicalTaskService.updateProgress(
                taskId,
                employee,
                progressPercentage
        );

        return "redirect:/employee/practical-tasks/" + taskId;
    }


    // =========================================================
    // SUBMIT PRACTICAL TASK
    // =========================================================

    @PostMapping("/{taskId}/submit")
    public String submitTask(
            @PathVariable Long taskId,
            @RequestParam(
                    name = "solutionDescription",
                    required = false
            ) String solutionDescription,
            @RequestParam(
                    name = "submissionUrl",
                    required = false
            ) String submissionUrl,
            Authentication authentication
    ) {

        User employee = getAuthenticatedEmployee(authentication);

        practicalTaskService.submitTask(
                taskId,
                employee,
                solutionDescription,
                submissionUrl
        );

        return "redirect:/employee/practical-tasks/"
                + taskId
                + "?submitted=true";
    }


    // =========================================================
    // PREPARE FOR RESUBMISSION
    // =========================================================

    @PostMapping("/{taskId}/resubmit")
    public String prepareForResubmission(
            @PathVariable Long taskId,
            Authentication authentication
    ) {

        User employee = getAuthenticatedEmployee(authentication);

        practicalTaskService.prepareForResubmission(
                taskId,
                employee
        );

        return "redirect:/employee/practical-tasks/"
                + taskId
                + "?resubmission=true";
    }


    // =========================================================
    // TASKS BY SKILL
    // =========================================================

    @GetMapping("/skill/{skillName}")
    @Transactional
    public String tasksBySkill(
            @PathVariable String skillName,
            Authentication authentication,
            Model model
    ) {

        User employee = getAuthenticatedEmployee(authentication);

        List<PracticalTask> practicalTasks =
                practicalTaskService.getEmployeeTasksBySkill(
                        employee,
                        skillName
                );

        if (practicalTasks == null) {
            practicalTasks = Collections.emptyList();
        }

        LearningPath learningPath =
                getEmployeeLearningPath(employee);

        initializeLearningPath(learningPath);


        // =====================================================
        // COUNTS
        // =====================================================

        long totalTasks = practicalTasks.size();

        long completedTasks =
                practicalTasks.stream()
                        .filter(task ->
                                task.getStatus()
                                        == PracticalTask.TaskStatus.COMPLETED
                        )
                        .count();

        long inProgressTasks =
                practicalTasks.stream()
                        .filter(task ->
                                task.getStatus()
                                        == PracticalTask.TaskStatus.IN_PROGRESS
                        )
                        .count();

        long pendingTasks =
                practicalTasks.stream()
                        .filter(task ->
                                task.getStatus()
                                        == PracticalTask.TaskStatus.PENDING
                        )
                        .count();

        long submittedTasks =
                practicalTasks.stream()
                        .filter(task ->
                                task.getStatus()
                                        == PracticalTask.TaskStatus.SUBMITTED
                        )
                        .count();

        long needsImprovementTasks =
                practicalTasks.stream()
                        .filter(task ->
                                task.getStatus()
                                        == PracticalTask.TaskStatus.NEEDS_IMPROVEMENT
                        )
                        .count();


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute("employee", employee);
        model.addAttribute("learningPath", learningPath);

        model.addAttribute("practicalTasks", practicalTasks);
        model.addAttribute("tasks", practicalTasks);

        model.addAttribute("skillName", skillName);

        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("inProgressTasks", inProgressTasks);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("submittedTasks", submittedTasks);
        model.addAttribute("needsImprovementTasks", needsImprovementTasks);

        model.addAttribute("hasLearningPath", learningPath != null);
        model.addAttribute("hasTasks", !practicalTasks.isEmpty());

        return "employee/practical-tasks";
    }


    // =========================================================
    // INITIALIZE LEARNING PATH ITEMS
    // =========================================================

    private void initializeLearningPath(LearningPath learningPath) {

        if (learningPath == null) {
            return;
        }

        if (learningPath.getItems() != null) {
            learningPath.getItems().size();
        }
    }


    // =========================================================
    // GET AUTHENTICATED EMPLOYEE
    // =========================================================

    private User getAuthenticatedEmployee(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {

            throw new IllegalArgumentException(
                    "Employee authentication is required."
            );
        }

        String email = authentication.getName();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated employee email is unavailable."
            );
        }

        User employee =
                userRepository.findByEmail(email.trim())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Authenticated employee was not found."
                                )
                        );

        if (employee.getRole() != User.Role.EMPLOYEE) {
            throw new IllegalArgumentException(
                    "Access denied: authenticated user is not an employee."
            );
        }

        if (!employee.isEnabled()) {
            throw new IllegalArgumentException(
                    "Employee account is disabled."
            );
        }

        return employee;
    }


    // =========================================================
    // GET EMPLOYEE LEARNING PATH
    // =========================================================

    private LearningPath getEmployeeLearningPath(User employee) {

        return learningPathService
                .getActiveLearningPath(employee)
                .orElseGet(() ->
                        learningPathService
                                .getLatestLearningPath(employee)
                                .orElse(null)
                );
    }
}
