package com.capacitix.controller;

import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.User;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.EmployeeTrainingService;
import com.capacitix.service.PracticalTaskService;
import com.capacitix.service.SkillAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/employee")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeProgressController {

    private final UserRepository userRepository;

    private final AssessmentAssignmentRepository assessmentAssignmentRepository;

    private final EmployeeTrainingService employeeTrainingService;

    private final SkillAnalysisService skillAnalysisService;

    private final PracticalTaskService practicalTaskService;


    // =========================================================
    // EMPLOYEE PROGRESS PAGE
    // =========================================================

    @GetMapping("/progress")
    public String progress(
            Model model,
            Authentication authentication
    ) {

        // =====================================================
        // GET LOGGED-IN EMPLOYEE
        // =====================================================

        String email = authentication.getName();

        User employee = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Logged-in employee not found."
                        )
                );


        // =====================================================
        // SECURITY CHECK
        // =====================================================

        if (employee.getRole() != User.Role.EMPLOYEE) {

            throw new RuntimeException(
                    "Invalid employee account."
            );
        }


        // =====================================================
        // EMPLOYEE INFORMATION
        // =====================================================

        String fullName = employee.getFullName();

        model.addAttribute("employee", employee);
        model.addAttribute("userName", fullName);
        model.addAttribute("userInitial", getInitial(fullName));


        // =====================================================
        // LEARNING / TRAINING PROGRESS
        // =====================================================
        //
        // IMPORTANT:
        //
        // This value comes from EmployeeTrainingService.
        //
        // EmployeeTrainingServiceImpl.getOverallProgress()
        // must calculate the actual persisted employee
        // training progress.
        //
        // The Thymeleaf page receives this as:
        //
        // learningProgress
        //
        // =====================================================

        int learningProgress =
                clampPercentage(
                        employeeTrainingService
                                .getOverallProgress(employee)
                );

        long completedTrainings =
                employeeTrainingService
                        .getCompletedModuleCount(employee);


        // =====================================================
        // PRACTICAL TASK PROGRESS
        // =====================================================

        List<PracticalTask> practicalTasks =
                practicalTaskService
                        .getEmployeeTasks(employee);

        if (practicalTasks == null) {
            practicalTasks = List.of();
        }

        int practicalTaskProgress =
                calculatePracticalTaskProgress(
                        practicalTasks
                );

        long totalTasks = practicalTasks.size();

        long tasksCompleted =
                practicalTasks.stream()
                        .filter(task ->
                                task != null
                                        && task.getStatus()
                                        == PracticalTask.TaskStatus.COMPLETED
                        )
                        .count();

        long tasksInProgress =
                practicalTasks.stream()
                        .filter(task ->
                                task != null
                                        && task.getStatus()
                                        == PracticalTask.TaskStatus.IN_PROGRESS
                        )
                        .count();

        long tasksPending =
                practicalTasks.stream()
                        .filter(task ->
                                task != null
                                        && task.getStatus()
                                        == PracticalTask.TaskStatus.PENDING
                        )
                        .count();


        // =====================================================
        // COMPLETED ASSESSMENTS
        // =====================================================

        List<AssessmentAssignment> completedAssignments =
                assessmentAssignmentRepository
                        .findByEmployeeAndStatusOrderByAssignedAtDesc(
                                employee,
                                "COMPLETED"
                        );

        if (completedAssignments == null) {
            completedAssignments = List.of();
        }

        int assessmentsCompleted =
                completedAssignments.size();


        // =====================================================
        // SKILL PROGRESS
        // =====================================================

        List<SkillProgressView> skillProgress =
                buildSkillProgress(
                        completedAssignments
                );


        // =====================================================
        // ASSESSMENT BASELINE
        // =====================================================

        int assessmentProgress = 0;

        if (!completedAssignments.isEmpty()) {

            AssessmentAssignment latestAssignment =
                    completedAssignments.get(0);

            SkillAnalysisService.SkillAnalysisResult analysis =
                    skillAnalysisService.analyzeAssignment(
                            latestAssignment
                    );

            if (analysis != null) {

                assessmentProgress =
                        clampPercentage(
                                analysis.percentage()
                        );
            }
        }


        // =====================================================
        // OVERALL PROGRESS
        // =====================================================

        int averageProgress;

        boolean hasTrainingProgress =
                learningProgress > 0;

        boolean hasPracticalTasks =
                !practicalTasks.isEmpty();


        if (hasTrainingProgress && hasPracticalTasks) {

            averageProgress =
                    (learningProgress
                            + practicalTaskProgress) / 2;

        } else if (hasTrainingProgress) {

            averageProgress =
                    learningProgress;

        } else if (hasPracticalTasks) {

            averageProgress =
                    practicalTaskProgress;

        } else {

            averageProgress =
                    assessmentProgress;
        }


        averageProgress =
                clampPercentage(
                        averageProgress
                );


        // =====================================================
        // CERTIFICATES
        // =====================================================

        long certificatesEarned = 0;


        // =====================================================
        // ADD MAIN VALUES TO THYMELEAF
        // =====================================================

        model.addAttribute(
                "averageProgress",
                averageProgress
        );

        model.addAttribute(
                "learningProgress",
                learningProgress
        );

        model.addAttribute(
                "practicalTaskProgress",
                practicalTaskProgress
        );

        model.addAttribute(
                "tasksCompleted",
                tasksCompleted
        );

        model.addAttribute(
                "totalTasks",
                totalTasks
        );

        model.addAttribute(
                "tasksInProgress",
                tasksInProgress
        );

        model.addAttribute(
                "tasksPending",
                tasksPending
        );

        model.addAttribute(
                "assessmentsCompleted",
                assessmentsCompleted
        );

        model.addAttribute(
                "certificatesEarned",
                certificatesEarned
        );

        model.addAttribute(
                "skillProgress",
                skillProgress
        );


        // =====================================================
        // ADDITIONAL VALUES
        // =====================================================

        model.addAttribute(
                "completedTrainings",
                completedTrainings
        );

        model.addAttribute(
                "skillCount",
                skillProgress.size()
        );


        long skillGapCount =
                skillProgress.stream()
                        .filter(skill ->
                                skill.progress() < 60
                        )
                        .count();

        model.addAttribute(
                "skillGapCount",
                skillGapCount
        );


        model.addAttribute(
                "assessmentProgress",
                assessmentProgress
        );


        // =====================================================
        // RETURN PAGE
        // =====================================================

        return "employee/progress";
    }


    // =========================================================
    // CALCULATE PRACTICAL TASK PROGRESS
    // =========================================================

    private int calculatePracticalTaskProgress(
            List<PracticalTask> practicalTasks
    ) {

        if (practicalTasks == null
                || practicalTasks.isEmpty()) {

            return 0;
        }


        int totalProgress = 0;
        int validTasks = 0;


        for (PracticalTask task : practicalTasks) {

            if (task == null) {
                continue;
            }

            Integer progress =
                    task.getProgressPercentage();

            if (progress == null) {
                progress = 0;
            }

            totalProgress +=
                    clampPercentage(progress);

            validTasks++;
        }


        if (validTasks == 0) {
            return 0;
        }


        return clampPercentage(
                Math.round(
                        (float) totalProgress
                                / validTasks
                )
        );
    }


    // =========================================================
    // BUILD SKILL PROGRESS
    // =========================================================

    private List<SkillProgressView> buildSkillProgress(
            List<AssessmentAssignment> completedAssignments
    ) {

        List<SkillProgressView> result =
                new ArrayList<>();


        if (completedAssignments == null
                || completedAssignments.isEmpty()) {

            return result;
        }


        AssessmentAssignment latestAssignment =
                completedAssignments.get(0);


        SkillAnalysisService.SkillAnalysisResult analysis =
                skillAnalysisService.analyzeAssignment(
                        latestAssignment
                );


        if (analysis == null
                || analysis.skills() == null) {

            return result;
        }


        for (SkillAnalysisService.SkillResult skill :
                analysis.skills()) {

            if (skill == null) {
                continue;
            }


            result.add(
                    new SkillProgressView(
                            skill.competencyName(),
                            skill.category(),
                            clampPercentage(
                                    skill.percentage()
                            ),
                            skill.currentLevel(),
                            skill.requiredLevel(),
                            skill.gap()
                    )
            );
        }


        // =====================================================
        // STRONGEST SKILLS FIRST
        // =====================================================

        result.sort(
                Comparator.comparingInt(
                        SkillProgressView::progress
                ).reversed()
        );


        return result;
    }


    // =========================================================
    // CLAMP PERCENTAGE
    // =========================================================

    private int clampPercentage(
            Integer value
    ) {

        if (value == null) {
            return 0;
        }

        return Math.max(
                0,
                Math.min(
                        100,
                        value
                )
        );
    }


    // =========================================================
    // INITIAL
    // =========================================================

    private String getInitial(
            String fullName
    ) {

        if (fullName == null
                || fullName.trim().isEmpty()) {

            return "E";
        }


        return String.valueOf(
                fullName
                        .trim()
                        .charAt(0)
        ).toUpperCase();
    }


    // =========================================================
    // THYMELEAF SKILL VIEW
    // =========================================================

    public record SkillProgressView(

            String skill,

            String category,

            int progress,

            String currentLevel,

            String requiredLevel,

            String gap

    ) {
    }
}