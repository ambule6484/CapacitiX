
package com.capacitix.controller;

import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.EmployeeTrainingProgress;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.User;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.LearningPathRepository;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/employee")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeDashboardController {

    private final UserRepository userRepository;

    private final AssessmentAssignmentRepository assessmentAssignmentRepository;

    private final LearningPathRepository learningPathRepository;

    private final SkillAnalysisService skillAnalysisService;

    private final EmployeeTrainingService employeeTrainingService;

    /*
     * PracticalTaskService is used for REAL practical-task
     * completion count.
     *
     * IMPORTANT:
     *
     * Training module completion and practical-task completion
     * are two different things.
     */
    private final PracticalTaskService practicalTaskService;


    // =========================================================
    // EMPLOYEE DASHBOARD
    // =========================================================

    @GetMapping("/dashboard")
    public String dashboard(
            Model model,
            Authentication authentication) {

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

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "userName",
                fullName
        );

        model.addAttribute(
                "userInitial",
                getInitial(fullName)
        );


        // =====================================================
        // DEFAULT VALUES
        // =====================================================

        int competencyScore = 0;

        int activeSkillGaps = 0;

        int learningProgress = 0;

        /*
         * IMPORTANT:
         *
         * This is the number of TRAINER-APPROVED practical tasks.
         *
         * It is NOT the number of completed training modules.
         */
        int tasksCompleted = 0;

        /*
         * Number of completed training modules.
         */
        int completedTrainings = 0;

        int pendingAssessments = 0;

        int scoreChange = 0;


        List<CompetencyView> competencies =
                new ArrayList<>();

        List<SkillGapView> skillGaps =
                new ArrayList<>();

        List<LearningItemView> learningItems =
                new ArrayList<>();

        NextStepView nextStep = null;

        String aiInsight = null;

        String error = null;


        // =====================================================
        // GET EMPLOYEE ASSESSMENTS
        // =====================================================

        List<AssessmentAssignment> assignments;

        try {

            assignments =
                    assessmentAssignmentRepository
                            .findByEmployeeOrderByAssignedAtDesc(
                                    employee
                            );

        } catch (Exception e) {

            assignments =
                    Collections.emptyList();

            error =
                    "Unable to load your assessment information.";

            System.err.println(
                    "Employee dashboard assessment error: "
                            + e.getMessage()
            );
        }


        // =====================================================
        // PENDING ASSESSMENTS
        // =====================================================

        if (assignments != null) {

            pendingAssessments =
                    (int) assignments.stream()
                            .filter(a -> !isCompleted(a))
                            .count();
        }


        // =====================================================
        // FIND LATEST COMPLETED ASSESSMENT
        // =====================================================

        AssessmentAssignment completedAssignment = null;

        if (assignments != null) {

            completedAssignment =
                    assignments.stream()
                            .filter(this::isCompleted)
                            .findFirst()
                            .orElse(null);
        }


        // =====================================================
        // SKILL ANALYSIS
        // =====================================================

        if (completedAssignment != null) {

            try {

                SkillAnalysisService.SkillAnalysisResult analysis =
                        skillAnalysisService.analyzeAssignment(
                                completedAssignment
                        );


                // =================================================
                // COMPETENCIES
                // =================================================

                if (analysis != null
                        && analysis.skills() != null) {

                    for (
                            SkillAnalysisService.SkillResult skill
                            : analysis.skills()
                    ) {

                        if (skill == null) {
                            continue;
                        }

                        String name =
                                safeText(
                                        skill.competencyName(),
                                        "Unknown Skill"
                                );

                        int score =
                                clamp(
                                        skill.percentage()
                                );

                        competencies.add(
                                new CompetencyView(
                                        name,
                                        score
                                )
                        );
                    }
                }


                // =================================================
                // OVERALL COMPETENCY SCORE
                // =================================================

                if (analysis != null) {

                    competencyScore =
                            clamp(
                                    analysis.percentage()
                            );
                }


                // =================================================
                // FALLBACK TO COMPETENCY AVERAGE
                // =================================================

                if (competencyScore == 0
                        && !competencies.isEmpty()) {

                    int total = 0;

                    for (
                            CompetencyView competency
                            : competencies
                    ) {

                        total += competency.score();
                    }

                    competencyScore =
                            Math.round(
                                    (float) total
                                            / competencies.size()
                            );
                }


                // =================================================
                // SKILL GAPS
                // =================================================

                if (analysis != null
                        && analysis.skillGaps() != null) {

                    for (
                            SkillAnalysisService.SkillResult gap
                            : analysis.skillGaps()
                    ) {

                        if (gap == null) {
                            continue;
                        }

                        String name =
                                safeText(
                                        gap.competencyName(),
                                        "Unknown Skill"
                                );

                        String currentLevel =
                                safeText(
                                        gap.currentLevel(),
                                        "BEGINNER"
                                );

                        String priority =
                                normalizePriority(
                                        gap.gap()
                                );

                        /*
                         * Progress is initially 0.
                         *
                         * It is populated later from the
                         * employee's actual training progress.
                         */
                        skillGaps.add(
                                new SkillGapView(
                                        name,
                                        formatLevel(currentLevel),
                                        priority,
                                        0
                                )
                        );
                    }
                }


                // =================================================
                // ACTIVE SKILL GAPS
                // =================================================

                activeSkillGaps =
                        skillGaps.size();


                // =================================================
                // AI INSIGHT
                // =================================================

                aiInsight =
                        buildAIInsight(
                                skillGaps,
                                competencyScore
                        );


                // =================================================
                // NEXT STEP
                // =================================================

                nextStep =
                        buildNextStep(
                                skillGaps
                        );

            } catch (Exception e) {

                error =
                        "Your assessment was found, but skill analysis could not be loaded.";

                System.err.println(
                        "Employee dashboard skill analysis error: "
                                + e.getMessage()
                );

                e.printStackTrace();
            }
        }


        // =====================================================
        // LOAD LATEST PERSONALIZED LEARNING PATH
        // =====================================================

        LearningPath learningPath = null;

        try {

            learningPath =
                    learningPathRepository
                            .findTopByEmployeeOrderByCreatedAtDesc(
                                    employee
                            )
                            .orElse(null);

        } catch (Exception e) {

            System.err.println(
                    "Employee dashboard learning path lookup error: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }


        // =====================================================
        // LOAD REAL PRACTICAL TASK COMPLETION
        // =====================================================

        /*
         * IMPORTANT BUSINESS RULE:
         *
         * Practical task is NOT completed when:
         *
         * PENDING
         * IN_PROGRESS
         * SUBMITTED
         * NEEDS_IMPROVEMENT
         *
         * Only:
         *
         * COMPLETED
         *
         * means trainer-approved completion.
         *
         * PracticalTask.markCompleted() sets:
         *
         * status = COMPLETED
         * progress = 100
         *
         * Therefore the dashboard uses the COMPLETED status.
         */

        try {

            tasksCompleted =
                    (int) practicalTaskService.countTasksByStatus(
                            employee,
                            PracticalTask.TaskStatus.COMPLETED
                    );

        } catch (Exception e) {

            /*
             * Do not break the entire employee dashboard if
             * practical-task counting fails.
             */
            tasksCompleted = 0;

            System.err.println(
                    "Employee dashboard practical task count error: "
                            + e.getMessage()
            );
        }


        // =====================================================
        // LOAD REAL TRAINING PROGRESS
        // =====================================================

        if (learningPath != null) {

            try {

                learningProgress =
                        clamp(
                                employeeTrainingService
                                        .getOverallProgress(
                                                employee
                                        )
                        );

            } catch (Exception e) {

                System.err.println(
                        "Employee dashboard training progress error: "
                                + e.getMessage()
                );

                try {

                    Number pathProgress =
                            learningPath.getProgressPercentage();

                    if (pathProgress != null) {

                        learningProgress =
                                clamp(
                                        pathProgress.doubleValue()
                                );
                    }

                } catch (Exception ignored) {

                    learningProgress = 0;
                }
            }


            // =================================================
            // MAP REAL PROGRESS TO SKILL GAPS
            // =================================================

            try {

                skillGaps =
                        attachTrainingProgressToSkillGaps(
                                employee,
                                skillGaps
                        );

            } catch (Exception e) {

                System.err.println(
                        "Employee dashboard skill gap progress error: "
                                + e.getMessage()
                );

                e.printStackTrace();
            }


            // =================================================
            // MODULE COUNT
            // =================================================

            int moduleCount = 0;

            try {

                moduleCount =
                        employeeTrainingService
                                .getTotalModuleCount(
                                        employee
                                );

            } catch (Exception e) {

                moduleCount =
                        getLearningModuleCount(
                                learningPath
                        );
            }


            // =================================================
            // COMPLETED TRAINING COUNT
            // =================================================

            try {

                completedTrainings =
                        employeeTrainingService
                                .getCompletedModuleCount(
                                        employee
                                );

            } catch (Exception e) {

                completedTrainings =
                        getCompletedTrainingCount(
                                learningPath
                        );
            }


            // =================================================
            // IMPORTANT:
            //
            // DO NOT DO:
            //
            // tasksCompleted = completedTrainings;
            //
            // They represent two different concepts.
            //
            // tasksCompleted was already loaded from:
            //
            // PracticalTask.TaskStatus.COMPLETED
            //
            // above.
            // =================================================


            // =================================================
            // STATUS
            // =================================================

            String status =
                    getLearningPathStatus(
                            learningPath
                    );


            // =================================================
            // TITLE
            // =================================================

            String title =
                    safeText(
                            learningPath.getTitle(),
                            "Personalized Learning Path"
                    );


            // =================================================
            // DESCRIPTION
            // =================================================

            String description =
                    safeText(
                            learningPath.getDescription(),
                            "AI-generated learning path based on your skill gaps."
                    );


            // =================================================
            // LEARNING ITEM
            // =================================================

            learningItems.add(
                    new LearningItemView(
                            title,
                            "Course",
                            moduleCount,
                            status,
                            description,
                            "/employee/learning-path"
                    )
            );


            // =================================================
            // NEXT STEP
            // =================================================

            if (nextStep == null) {

                nextStep =
                        new NextStepView(
                                title,
                                description,
                                "/employee/learning-path"
                        );
            }
        }


        // =====================================================
        // FINAL MODEL
        // =====================================================

        model.addAttribute(
                "competencyScore",
                competencyScore
        );

        model.addAttribute(
                "scoreChange",
                scoreChange
        );

        model.addAttribute(
                "activeSkillGaps",
                activeSkillGaps
        );

        model.addAttribute(
                "learningProgress",
                learningProgress
        );

        /*
         * REAL PRACTICAL TASK COMPLETION
         */
        model.addAttribute(
                "tasksCompleted",
                tasksCompleted
        );

        model.addAttribute(
                "competencies",
                competencies
        );

        model.addAttribute(
                "skillGaps",
                skillGaps
        );

        model.addAttribute(
                "learningItems",
                learningItems
        );

        model.addAttribute(
                "nextStep",
                nextStep
        );

        model.addAttribute(
                "aiInsight",
                aiInsight
        );

        /*
         * REAL TRAINING MODULE COMPLETION
         */
        model.addAttribute(
                "completedTrainings",
                completedTrainings
        );

        model.addAttribute(
                "pendingAssessments",
                pendingAssessments
        );

        /*
         * Keep completedTasks synchronized with the
         * real practical-task count.
         */
        model.addAttribute(
                "completedTasks",
                tasksCompleted
        );

        model.addAttribute(
                "skillCount",
                competencies.size()
        );

        model.addAttribute(
                "skillGapCount",
                activeSkillGaps
        );

        model.addAttribute(
                "learningPathProgress",
                learningProgress
        );

        model.addAttribute(
                "overallProgress",
                learningProgress
        );


        // =====================================================
        // NOTIFICATIONS
        // =====================================================

        model.addAttribute(
                "notificationCount",
                0
        );


        // =====================================================
        // ERROR
        // =====================================================

        if (error != null) {

            model.addAttribute(
                    "error",
                    error
            );
        }


        // =====================================================
        // RETURN DASHBOARD
        // =====================================================

        return "employee/dashboard";
    }


    // =========================================================
    // ATTACH REAL TRAINING PROGRESS TO SKILL GAPS
    // =========================================================

    private List<SkillGapView> attachTrainingProgressToSkillGaps(
            User employee,
            List<SkillGapView> skillGaps) {

        if (employee == null
                || skillGaps == null
                || skillGaps.isEmpty()) {

            return skillGaps;
        }


        // =====================================================
        // GET EMPLOYEE LEARNING PATH ITEMS
        // =====================================================

        List<LearningPathItem> trainingItems =
                employeeTrainingService
                        .getEmployeeTrainingItems(
                                employee
                        );

        if (trainingItems == null
                || trainingItems.isEmpty()) {

            return skillGaps;
        }


        // =====================================================
        // GET EMPLOYEE TRAINING PROGRESS
        // =====================================================

        List<EmployeeTrainingProgress> progressRecords =
                employeeTrainingService
                        .getEmployeeProgress(
                                employee
                        );

        if (progressRecords == null) {

            progressRecords =
                    Collections.emptyList();
        }


        // =====================================================
        // BUILD ITEM ID → PROGRESS MAP
        // =====================================================

        Map<Long, Integer> progressByItemId =
                new HashMap<>();

        for (
                EmployeeTrainingProgress progress
                : progressRecords
        ) {

            if (progress == null
                    || progress.getLearningPathItem() == null
                    || progress.getLearningPathItem().getId() == null) {

                continue;
            }

            Long itemId =
                    progress.getLearningPathItem().getId();

            int percentage =
                    getProgressPercentage(progress);

            Integer existing =
                    progressByItemId.get(itemId);

            if (existing == null
                    || percentage > existing) {

                progressByItemId.put(
                        itemId,
                        percentage
                );
            }
        }


        // =====================================================
        // BUILD SKILL → PROGRESS MAP
        // =====================================================

        Map<String, Integer> progressBySkill =
                new HashMap<>();

        for (
                LearningPathItem item
                : trainingItems
        ) {

            if (item == null) {
                continue;
            }


            // -------------------------------------------------
            // GET ITEM PROGRESS
            // -------------------------------------------------

            int percentage = 0;

            if (item.getId() != null) {

                percentage =
                        progressByItemId.getOrDefault(
                                item.getId(),
                                getItemStoredProgress(item)
                        );
            }

            percentage =
                    clamp(
                            percentage
                    );


            // -------------------------------------------------
            // TARGET SKILL
            // -------------------------------------------------

            String targetSkill =
                    normalizeSkillName(
                            item.getTargetSkill()
                    );

            if (!targetSkill.isBlank()) {

                Integer existing =
                        progressBySkill.get(
                                targetSkill
                        );

                if (existing == null
                        || percentage > existing) {

                    progressBySkill.put(
                            targetSkill,
                            percentage
                    );
                }
            }


            // -------------------------------------------------
            // FALLBACK TO TRAINING MODULE SKILL
            // -------------------------------------------------

            if (item.getTrainingModule() != null) {

                String moduleSkill =
                        normalizeSkillName(
                                item.getTrainingModule()
                                        .getSkillName()
                        );

                if (!moduleSkill.isBlank()) {

                    Integer existing =
                            progressBySkill.get(
                                    moduleSkill
                            );

                    if (existing == null
                            || percentage > existing) {

                        progressBySkill.put(
                                moduleSkill,
                                percentage
                        );
                    }
                }
            }
        }


        // =====================================================
        // UPDATE SKILL GAP VIEWS
        // =====================================================

        List<SkillGapView> updatedSkillGaps =
                new ArrayList<>();

        for (
                SkillGapView gap
                : skillGaps
        ) {

            if (gap == null) {
                continue;
            }

            String normalizedGapSkill =
                    normalizeSkillName(
                            gap.name()
                    );

            int progress =
                    findMatchingSkillProgress(
                            normalizedGapSkill,
                            progressBySkill
                    );

            updatedSkillGaps.add(
                    new SkillGapView(
                            gap.name(),
                            gap.currentLevel(),
                            gap.priority(),
                            progress
                    )
            );
        }


        return updatedSkillGaps;
    }


    // =========================================================
    // FIND MATCHING SKILL PROGRESS
    // =========================================================

    private int findMatchingSkillProgress(
            String skillName,
            Map<String, Integer> progressBySkill) {

        if (skillName == null
                || skillName.isBlank()
                || progressBySkill == null
                || progressBySkill.isEmpty()) {

            return 0;
        }


        // =====================================================
        // EXACT MATCH
        // =====================================================

        Integer exact =
                progressBySkill.get(
                        skillName
                );

        if (exact != null) {

            return clamp(exact);
        }


        // =====================================================
        // FLEXIBLE MATCH
        // =====================================================

        for (
                Map.Entry<String, Integer> entry
                : progressBySkill.entrySet()
        ) {

            String trainingSkill =
                    entry.getKey();

            if (trainingSkill == null
                    || trainingSkill.isBlank()) {

                continue;
            }

            if (skillName.equals(trainingSkill)
                    || skillName.contains(trainingSkill)
                    || trainingSkill.contains(skillName)) {

                return clamp(
                        entry.getValue()
                );
            }
        }


        return 0;
    }


    // =========================================================
    // NORMALIZE SKILL NAME
    // =========================================================

    private String normalizeSkillName(
            String value) {

        if (value == null
                || value.isBlank()) {

            return "";
        }

        return value
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }


    // =========================================================
    // GET PROGRESS FROM EMPLOYEE TRAINING PROGRESS
    // =========================================================

    private int getProgressPercentage(
            EmployeeTrainingProgress progress) {

        if (progress == null) {
            return 0;
        }

        Integer percentage =
                progress.getProgressPercentage();

        if (percentage != null) {

            return clamp(
                    percentage.doubleValue()
            );
        }

        if (progress.getStatus()
                == EmployeeTrainingProgress.TrainingStatus.COMPLETED) {

            return 100;
        }

        return 0;
    }


    // =========================================================
    // GET STORED LEARNING PATH ITEM PROGRESS
    // =========================================================

    private int getItemStoredProgress(
            LearningPathItem item) {

        if (item == null) {
            return 0;
        }

        Integer percentage =
                item.getProgressPercentage();

        if (percentage == null) {
            return 0;
        }

        return clamp(
                percentage.doubleValue()
        );
    }


    // =========================================================
    // CHECK COMPLETED ASSESSMENT
    // =========================================================

    private boolean isCompleted(
            AssessmentAssignment assignment) {

        if (assignment == null) {
            return false;
        }

        try {

            return assignment.isCompleted();

        } catch (Exception ignored) {

            return false;
        }
    }


    // =========================================================
    // BUILD AI INSIGHT
    // =========================================================

    private String buildAIInsight(
            List<SkillGapView> skillGaps,
            int competencyScore) {

        if (skillGaps == null
                || skillGaps.isEmpty()) {

            if (competencyScore > 0) {

                return
                        "Your current competency score is "
                                + competencyScore
                                + "%. Keep building your skills through your personalized learning path.";
            }

            return null;
        }

        SkillGapView highestPriority =
                skillGaps.stream()
                        .filter(g ->
                                "HIGH".equalsIgnoreCase(
                                        g.priority()
                                )
                        )
                        .findFirst()
                        .orElse(
                                skillGaps.get(0)
                        );

        return
                "Your highest priority skill gap is "
                        + highestPriority.name()
                        + ". Focus on this competency first to improve your overall performance.";
    }


    // =========================================================
    // BUILD NEXT STEP
    // =========================================================

    private NextStepView buildNextStep(
            List<SkillGapView> skillGaps) {

        if (skillGaps == null
                || skillGaps.isEmpty()) {

            return null;
        }

        SkillGapView gap =
                skillGaps.stream()
                        .filter(g ->
                                "HIGH".equalsIgnoreCase(
                                        g.priority()
                                )
                        )
                        .findFirst()
                        .orElse(
                                skillGaps.get(0)
                        );

        String title =
                "Improve " + gap.name();

        String description =
                "Focus on your "
                        + gap.name()
                        + " competency through the recommended learning modules in your personalized learning path.";

        return new NextStepView(
                title,
                description,
                "/employee/learning-path"
        );
    }


    // =========================================================
    // LEARNING MODULE COUNT FALLBACK
    // =========================================================

    private int getLearningModuleCount(
            LearningPath learningPath) {

        if (learningPath == null) {
            return 0;
        }

        try {

            if (learningPath.getItems() != null) {

                return learningPath
                        .getItems()
                        .size();
            }

        } catch (Exception ignored) {
        }

        return 0;
    }


    // =========================================================
    // COMPLETED TRAINING COUNT FALLBACK
    // =========================================================

    private int getCompletedTrainingCount(
            LearningPath learningPath) {

        if (learningPath == null
                || learningPath.getItems() == null) {

            return 0;
        }

        int completed = 0;

        for (var item : learningPath.getItems()) {

            if (item == null) {
                continue;
            }

            try {

                Object status =
                        item.getStatus();

                if (status != null
                        && "COMPLETED".equalsIgnoreCase(
                                status.toString()
                        )) {

                    completed++;
                }

            } catch (Exception ignored) {
            }
        }

        return completed;
    }


    // =========================================================
    // LEARNING PATH STATUS
    // =========================================================

    private String getLearningPathStatus(
            LearningPath learningPath) {

        if (learningPath == null) {
            return "Pending";
        }

        try {

            Object status =
                    learningPath.getStatus();

            if (status != null) {

                String value =
                        status.toString().trim();

                if (!value.isEmpty()) {

                    if ("COMPLETED".equalsIgnoreCase(value)) {
                        return "Completed";
                    }

                    if ("PENDING".equalsIgnoreCase(value)) {
                        return "Pending";
                    }

                    if ("GENERATED".equalsIgnoreCase(value)) {
                        return "Generated";
                    }

                    if ("IN_PROGRESS".equalsIgnoreCase(value)) {
                        return "In Progress";
                    }

                    return value;
                }
            }

        } catch (Exception ignored) {
        }

        return "In Progress";
    }


    // =========================================================
    // PRIORITY
    // =========================================================

    private String normalizePriority(
            String priority) {

        if (priority == null
                || priority.isBlank()) {

            return "MEDIUM";
        }

        String value =
                priority.trim().toUpperCase();

        return switch (value) {

            case "HIGH" -> "HIGH";

            case "MEDIUM" -> "MEDIUM";

            case "LOW" -> "LOW";

            default -> "MEDIUM";
        };
    }


    // =========================================================
    // FORMAT LEVEL
    // =========================================================

    private String formatLevel(
            String level) {

        if (level == null
                || level.isBlank()) {

            return "Beginner";
        }

        String value =
                level.trim().toLowerCase();

        return switch (value) {

            case "beginner" ->
                    "Beginner";

            case "intermediate" ->
                    "Intermediate";

            case "advanced" ->
                    "Advanced";

            default ->
                    capitalize(value);
        };
    }


    // =========================================================
    // CAPITALIZE
    // =========================================================

    private String capitalize(
            String value) {

        if (value == null
                || value.isBlank()) {

            return "Beginner";
        }

        return Character.toUpperCase(
                value.charAt(0)
        ) + value.substring(1);
    }


    // =========================================================
    // SAFE TEXT
    // =========================================================

    private String safeText(
            String value,
            String fallback) {

        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        return value;
    }


    // =========================================================
    // CLAMP
    // =========================================================

    private int clamp(
            double value) {

        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            return 0;
        }

        return (int) Math.round(
                Math.max(
                        0,
                        Math.min(
                                100,
                                value
                        )
                )
        );
    }


    // =========================================================
    // INITIAL
    // =========================================================

    private String getInitial(
            String fullName) {

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
    // DASHBOARD VIEW MODELS
    // =========================================================

    public record CompetencyView(
            String name,
            int score
    ) {
    }


    public record SkillGapView(
            String name,
            String currentLevel,
            String priority,
            int progress
    ) {
    }


    public record LearningItemView(
            String title,
            String type,
            Integer modules,
            String status,
            String description,
            String link
    ) {
    }


    public record NextStepView(
            String title,
            String description,
            String link
    ) {
    }
}