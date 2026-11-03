package com.capacitix.controller;

import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.EmployeeTrainingProgress;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.User;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.EmployeeTrainingService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/employee")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeCompetencyController {

    private final UserRepository userRepository;

    private final AssessmentAssignmentRepository
            assessmentAssignmentRepository;

    private final SkillAnalysisService
            skillAnalysisService;

    private final EmployeeTrainingService
            employeeTrainingService;


    // =========================================================
    // EMPLOYEE COMPETENCIES
    // =========================================================

    @GetMapping("/competencies")
    public String competencies(
            Authentication authentication,
            Model model
    ) {

        User employee =
                getLoggedInEmployee(authentication);

        addEmployeeDetails(employee, model);

        List<AssessmentAssignment> completedAssignments =
                assessmentAssignmentRepository
                        .findByEmployeeAndStatusOrderByAssignedAtDesc(
                                employee,
                                "COMPLETED"
                        );

        if (completedAssignments == null
                || completedAssignments.isEmpty()) {

            addEmptyCompetencyData(model);

            model.addAttribute(
                    "assessmentCompleted",
                    false
            );

            return "employee/competencies";
        }

        AssessmentAssignment latestAssignment =
                completedAssignments.get(0);

        SkillAnalysisService.SkillAnalysisResult analysis;

        try {

            analysis =
                    skillAnalysisService.analyzeAssignment(
                            latestAssignment
                    );

        } catch (Exception exception) {

            addEmptyCompetencyData(model);

            model.addAttribute(
                    "assessmentCompleted",
                    true
            );

            model.addAttribute(
                    "error",
                    "Unable to calculate your competency information from the completed assessment."
            );

            return "employee/competencies";
        }


        // =====================================================
        // BUILD COMPETENCIES
        // =====================================================

        List<Map<String, Object>> competencies =
                buildCompetenciesFromAssessment(
                        analysis,
                        employee
                );

        int totalCompetencies =
                competencies.size();

        long achievedCompetencies =
                competencies.stream()
                        .filter(c ->
                                getProgress(c) >= 80
                        )
                        .count();

        int totalProgress =
                competencies.stream()
                        .mapToInt(this::getProgress)
                        .sum();

        int averageLevel =
                totalCompetencies > 0
                        ? Math.round(
                                (float) totalProgress
                                        / totalCompetencies
                        )
                        : 0;


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "competencies",
                competencies
        );

        model.addAttribute(
                "totalCompetencies",
                totalCompetencies
        );

        model.addAttribute(
                "achievedCompetencies",
                achievedCompetencies
        );

        model.addAttribute(
                "averageLevel",
                averageLevel + "%"
        );

        model.addAttribute(
                "assessmentCompleted",
                true
        );

        addAssessmentInformation(
                model,
                analysis
        );

        return "employee/competencies";
    }


    // =========================================================
    // BUILD COMPETENCIES FROM ASSESSMENT
    // =========================================================

    private List<Map<String, Object>> buildCompetenciesFromAssessment(
            SkillAnalysisService.SkillAnalysisResult analysis,
            User employee
    ) {

        List<Map<String, Object>> competencies =
                new ArrayList<>();

        if (analysis == null
                || analysis.skills() == null) {

            return competencies;
        }


        // =====================================================
        // LOAD TRAINING PROGRESS ONCE
        // =====================================================

        Map<String, Integer> trainingProgressMap =
                buildTrainingProgressMap(employee);


        // =====================================================
        // BUILD EACH COMPETENCY
        // =====================================================

        for (
                SkillAnalysisService.SkillResult result
                : analysis.skills()
        ) {

            if (result == null) {
                continue;
            }

            String competencyName =
                    result.competencyName();

            if (competencyName == null
                    || competencyName.isBlank()) {

                continue;
            }


            // -------------------------------------------------
            // ASSESSMENT PERCENTAGE
            // -------------------------------------------------

            int assessmentPercentage =
                    safePercentage(
                            result.percentage()
                    );


            // -------------------------------------------------
            // CURRENT LEVEL
            // -------------------------------------------------

            String level =
                    result.currentLevel();

            if (level == null
                    || level.isBlank()) {

                level =
                        convertPercentageToLevel(
                                assessmentPercentage
                        );
            }


            // -------------------------------------------------
            // ACTUAL TRAINING PROGRESS
            // -------------------------------------------------

            int actualProgress =
                    getTrainingProgress(
                            competencyName,
                            trainingProgressMap
                    );


            // -------------------------------------------------
            // STATUS
            // -------------------------------------------------

            String status;

            if (actualProgress >= 80) {

                status = "achieved";

            } else if (actualProgress >= 50) {

                status = "progress";

            } else {

                status = "improvement";
            }


            // -------------------------------------------------
            // CATEGORY
            // -------------------------------------------------

            String category =
                    result.category();

            if (category == null
                    || category.isBlank()) {

                category = "General";
            }


            // -------------------------------------------------
            // MAP
            // -------------------------------------------------

            Map<String, Object> competency =
                    new LinkedHashMap<>();

            competency.put(
                    "name",
                    competencyName.trim()
            );

            competency.put(
                    "category",
                    category
            );

            competency.put(
                    "level",
                    level
            );

            /*
             * IMPORTANT:
             *
             * Progress = ACTUAL TRAINING PROGRESS
             * NOT assessment percentage.
             */
            competency.put(
                    "progress",
                    actualProgress
            );

            competency.put(
                    "status",
                    status
            );

            /*
             * Original assessment score is still
             * available separately.
             */
            competency.put(
                    "assessmentPercentage",
                    assessmentPercentage
            );

            competency.put(
                    "competencyId",
                    result.competencyId()
            );

            competency.put(
                    "requiredLevel",
                    result.requiredLevel()
            );

            competency.put(
                    "gap",
                    result.gap()
            );

            competency.put(
                    "totalQuestions",
                    result.totalQuestions()
            );

            competency.put(
                    "correctAnswers",
                    result.correctAnswers()
            );

            competency.put(
                    "obtainedMarks",
                    result.obtainedMarks()
            );

            competency.put(
                    "maxMarks",
                    result.maxMarks()
            );

            competencies.add(
                    competency
            );
        }


        // =====================================================
        // SORT BY PROGRESS
        // =====================================================

        competencies.sort(
                Comparator.comparingInt(
                        this::getProgress
                ).reversed()
        );

        return competencies;
    }


    // =========================================================
    // EMPLOYEE SKILL GAPS
    // =========================================================

    @GetMapping("/skill-gaps")
    public String skillGaps(
            Authentication authentication,
            Model model
    ) {

        User employee =
                getLoggedInEmployee(authentication);

        addEmployeeDetails(employee, model);

        List<AssessmentAssignment> completedAssignments =
                assessmentAssignmentRepository
                        .findByEmployeeAndStatusOrderByAssignedAtDesc(
                                employee,
                                "COMPLETED"
                        );

        if (completedAssignments == null
                || completedAssignments.isEmpty()) {

            addEmptySkillGapData(model);

            model.addAttribute(
                    "assessmentCompleted",
                    false
            );

            return "employee/skill-gaps";
        }


        AssessmentAssignment latestAssignment =
                completedAssignments.get(0);

        SkillAnalysisService.SkillAnalysisResult analysis;

        try {

            analysis =
                    skillAnalysisService.analyzeAssignment(
                            latestAssignment
                    );

        } catch (Exception exception) {

            addEmptySkillGapData(model);

            model.addAttribute(
                    "assessmentCompleted",
                    true
            );

            model.addAttribute(
                    "error",
                    "Unable to calculate skill gaps from the completed assessment."
            );

            return "employee/skill-gaps";
        }


        // =====================================================
        // BUILD SKILL GAPS
        // =====================================================

        List<Map<String, Object>> skillGaps =
                buildSkillGaps(
                        analysis,
                        employee
                );


        long highGaps =
                countGap(
                        skillGaps,
                        "HIGH"
                );

        long mediumGaps =
                countGap(
                        skillGaps,
                        "MEDIUM"
                );

        long lowGaps =
                countGap(
                        skillGaps,
                        "LOW"
                );


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "skillGaps",
                skillGaps
        );

        model.addAttribute(
                "totalSkillGaps",
                skillGaps.size()
        );

        model.addAttribute(
                "highGaps",
                highGaps
        );

        model.addAttribute(
                "mediumGaps",
                mediumGaps
        );

        model.addAttribute(
                "lowGaps",
                lowGaps
        );

        model.addAttribute(
                "assessmentCompleted",
                true
        );

        addAssessmentInformation(
                model,
                analysis
        );

        return "employee/skill-gaps";
    }


    // =========================================================
    // BUILD SKILL GAPS
    // =========================================================

    private List<Map<String, Object>> buildSkillGaps(
            SkillAnalysisService.SkillAnalysisResult analysis,
            User employee
    ) {

        List<Map<String, Object>> skillGaps =
                new ArrayList<>();

        if (analysis == null
                || analysis.skills() == null) {

            return skillGaps;
        }


        // =====================================================
        // LOAD ACTUAL TRAINING PROGRESS
        // =====================================================

        Map<String, Integer> trainingProgressMap =
                buildTrainingProgressMap(employee);


        // =====================================================
        // PROCESS SKILLS
        // =====================================================

        for (
                SkillAnalysisService.SkillResult result
                : analysis.skills()
        ) {

            if (result == null) {
                continue;
            }


            // -------------------------------------------------
            // ASSESSMENT PERCENTAGE
            // -------------------------------------------------

            int assessmentPercentage =
                    safePercentage(
                            result.percentage()
                    );


            // -------------------------------------------------
            // ONLY SHOW REAL GAPS
            // -------------------------------------------------

            if (assessmentPercentage >= 60) {
                continue;
            }


            String skill =
                    result.competencyName();

            if (skill == null
                    || skill.isBlank()) {

                continue;
            }


            // -------------------------------------------------
            // CATEGORY
            // -------------------------------------------------

            String category =
                    result.category();

            if (category == null
                    || category.isBlank()) {

                category = "General";
            }


            // -------------------------------------------------
            // CURRENT LEVEL
            // -------------------------------------------------

            String currentLevel =
                    result.currentLevel();

            if (currentLevel == null
                    || currentLevel.isBlank()) {

                currentLevel =
                        convertPercentageToLevel(
                                assessmentPercentage
                        );
            }


            // -------------------------------------------------
            // REQUIRED LEVEL
            // -------------------------------------------------

            String requiredLevel =
                    result.requiredLevel();

            if (requiredLevel == null
                    || requiredLevel.isBlank()) {

                requiredLevel = "INTERMEDIATE";
            }


            // -------------------------------------------------
            // GAP
            // -------------------------------------------------

            String gap =
                    result.gap();

            if (gap == null
                    || gap.isBlank()) {

                gap =
                        calculateGapPriority(
                                assessmentPercentage
                        );
            }


            // -------------------------------------------------
            // ACTUAL TRAINING PROGRESS
            // -------------------------------------------------

            int actualProgress =
                    getTrainingProgress(
                            skill,
                            trainingProgressMap
                    );


            // -------------------------------------------------
            // CREATE MAP
            // -------------------------------------------------

            Map<String, Object> skillGap =
                    new LinkedHashMap<>();

            skillGap.put(
                    "skill",
                    skill.trim()
            );

            skillGap.put(
                    "category",
                    category
            );

            skillGap.put(
                    "currentLevel",
                    currentLevel
            );

            skillGap.put(
                    "requiredLevel",
                    requiredLevel
            );

            skillGap.put(
                    "gap",
                    gap
            );

            /*
             * IMPORTANT:
             *
             * progress = ACTUAL TRAINING PROGRESS
             */
            skillGap.put(
                    "progress",
                    actualProgress
            );

            /*
             * Keep assessment score separately.
             */
            skillGap.put(
                    "assessmentPercentage",
                    assessmentPercentage
            );

            skillGap.put(
                    "competencyId",
                    result.competencyId()
            );

            skillGap.put(
                    "totalQuestions",
                    result.totalQuestions()
            );

            skillGap.put(
                    "correctAnswers",
                    result.correctAnswers()
            );

            skillGap.put(
                    "obtainedMarks",
                    result.obtainedMarks()
            );

            skillGap.put(
                    "maxMarks",
                    result.maxMarks()
            );

            skillGaps.add(
                    skillGap
            );
        }


        // =====================================================
        // SORT LOWEST PROGRESS FIRST
        // =====================================================

        skillGaps.sort(
                Comparator.comparingInt(
                        this::getProgress
                )
        );

        return skillGaps;
    }


    // =========================================================
    // BUILD TRAINING PROGRESS MAP
    // =========================================================

    private Map<String, Integer> buildTrainingProgressMap(
            User employee
    ) {

        Map<String, Integer> progressMap =
                new LinkedHashMap<>();

        if (employee == null) {
            return progressMap;
        }


        try {

            // =================================================
            // GET EMPLOYEE TRAINING ITEMS
            // =================================================

            List<LearningPathItem> items =
                    employeeTrainingService
                            .getEmployeeTrainingItems(
                                    employee
                            );

            if (items == null
                    || items.isEmpty()) {

                return progressMap;
            }


            // =================================================
            // GET EMPLOYEE PROGRESS
            // =================================================

            List<EmployeeTrainingProgress> progressRecords =
                    employeeTrainingService
                            .getEmployeeProgress(
                                    employee
                            );

            if (progressRecords == null
                    || progressRecords.isEmpty()) {

                return progressMap;
            }


            // =================================================
            // SKILL -> PROGRESS LIST
            // =================================================

            Map<String, List<Integer>> skillProgress =
                    new LinkedHashMap<>();


            // =================================================
            // PROCESS ITEMS
            // =================================================

            for (LearningPathItem item : items) {

                if (item == null) {
                    continue;
                }


                String targetSkill =
                        item.getTargetSkill();

                if (targetSkill == null
                        || targetSkill.isBlank()) {

                    continue;
                }


                String normalizedSkill =
                        normalizeSkill(
                                targetSkill
                        );

                if (normalizedSkill.isBlank()) {
                    continue;
                }


                int progressPercentage = 0;


                // =================================================
                // FIND PROGRESS RECORD
                // =================================================

                for (
                        EmployeeTrainingProgress progress
                        : progressRecords
                ) {

                    if (progress == null
                            || progress.getLearningPathItem() == null) {

                        continue;
                    }


                    Long progressItemId =
                            progress.getLearningPathItem()
                                    .getId();

                    Long currentItemId =
                            item.getId();

                    if (progressItemId == null
                            || currentItemId == null) {

                        continue;
                    }


                    if (!progressItemId.equals(
                            currentItemId
                    )) {

                        continue;
                    }


                    Integer percentage =
                            progress.getProgressPercentage();

                    if (percentage != null) {

                        progressPercentage =
                                Math.max(
                                        0,
                                        Math.min(
                                                100,
                                                percentage
                                        )
                                );
                    }


                    // =================================================
                    // COMPLETED ALWAYS = 100%
                    // =================================================

                    if (progress.getStatus()
                            == EmployeeTrainingProgress
                            .TrainingStatus.COMPLETED) {

                        progressPercentage = 100;
                    }


                    break;
                }


                // =================================================
                // ADD TO SKILL PROGRESS
                // =================================================

                skillProgress
                        .computeIfAbsent(
                                normalizedSkill,
                                key -> new ArrayList<>()
                        )
                        .add(
                                progressPercentage
                        );
            }


            // =================================================
            // CALCULATE AVERAGE PER SKILL
            // =================================================

            for (
                    Map.Entry<String, List<Integer>> entry
                    : skillProgress.entrySet()
            ) {

                List<Integer> values =
                        entry.getValue();

                if (values == null
                        || values.isEmpty()) {

                    progressMap.put(
                            entry.getKey(),
                            0
                    );

                    continue;
                }


                int total = 0;


                for (Integer value : values) {

                    if (value != null) {

                        total += Math.max(
                                0,
                                Math.min(
                                        100,
                                        value
                                )
                        );
                    }
                }


                int average =
                        Math.round(
                                (float) total
                                        / values.size()
                        );


                progressMap.put(
                        entry.getKey(),
                        Math.max(
                                0,
                                Math.min(
                                        100,
                                        average
                                )
                        )
                );
            }

        } catch (Exception exception) {

            System.err.println(
                    "Unable to load employee training progress: "
                            + exception.getMessage()
            );
        }


        return progressMap;
    }


    // =========================================================
    // GET TRAINING PROGRESS FOR SKILL
    // =========================================================

    private int getTrainingProgress(
            String skill,
            Map<String, Integer> trainingProgressMap
    ) {

        if (skill == null
                || skill.isBlank()
                || trainingProgressMap == null
                || trainingProgressMap.isEmpty()) {

            return 0;
        }


        String normalizedSkill =
                normalizeSkill(skill);


        Integer progress =
                trainingProgressMap.get(
                        normalizedSkill
                );


        if (progress == null) {
            return 0;
        }


        return Math.max(
                0,
                Math.min(
                        100,
                        progress
                )
        );
    }


    // =========================================================
    // NORMALIZE SKILL
    // =========================================================

    private String normalizeSkill(
            String skill
    ) {

        if (skill == null) {
            return "";
        }

        return skill
                .trim()
                .toLowerCase()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }


    // =========================================================
    // EMPLOYEE PROFILE
    // =========================================================

    @GetMapping("/profile")
    public String profile(
            Authentication authentication,
            Model model
    ) {

        User employee =
                getLoggedInEmployee(
                        authentication
                );

        addEmployeeDetails(
                employee,
                model
        );

        model.addAttribute(
                "employee",
                employee
        );

        return "employee/profile";
    }


    // =========================================================
    // EMPLOYEE SETTINGS
    // =========================================================

    @GetMapping("/settings")
    public String settings(
            Authentication authentication,
            Model model
    ) {

        User employee =
                getLoggedInEmployee(
                        authentication
                );

        addEmployeeDetails(
                employee,
                model
        );

        model.addAttribute(
                "employee",
                employee
        );

        return "employee/settings";
    }


    // =========================================================
    // COMMON EMPLOYEE AUTHENTICATION
    // =========================================================

    private User getLoggedInEmployee(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "Employee authentication required"
            );
        }


        String email =
                authentication.getName();


        User employee =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found: "
                                                + email
                                )
                        );


        if (employee.getRole()
                != User.Role.EMPLOYEE) {

            throw new RuntimeException(
                    "Access denied: User is not an employee"
            );
        }


        if (!employee.isEnabled()) {

            throw new RuntimeException(
                    "Employee account is disabled"
            );
        }


        return employee;
    }


    // =========================================================
    // COMMON EMPLOYEE DETAILS
    // =========================================================

    private void addEmployeeDetails(
            User employee,
            Model model
    ) {

        String fullName =
                employee.getFullName();


        model.addAttribute(
                "employeeName",
                fullName
        );

        model.addAttribute(
                "userName",
                fullName
        );

        model.addAttribute(
                "userInitial",
                getInitial(fullName)
        );

        model.addAttribute(
                "employee",
                employee
        );
    }


    // =========================================================
    // INITIAL
    // =========================================================

    private String getInitial(
            String fullName
    ) {

        if (fullName == null
                || fullName.isBlank()) {

            return "E";
        }


        return fullName
                .trim()
                .substring(
                        0,
                        1
                )
                .toUpperCase();
    }


    // =========================================================
    // ASSESSMENT INFORMATION
    // =========================================================

    private void addAssessmentInformation(
            Model model,
            SkillAnalysisService.SkillAnalysisResult analysis
    ) {

        if (analysis == null) {
            return;
        }


        model.addAttribute(
                "assessmentPercentage",
                analysis.percentage()
        );

        model.addAttribute(
                "assessmentObtainedMarks",
                analysis.obtainedMarks()
        );

        model.addAttribute(
                "assessmentMaxMarks",
                analysis.maxMarks()
        );

        model.addAttribute(
                "assessmentCorrectAnswers",
                analysis.correctAnswers()
        );

        model.addAttribute(
                "assessmentTotalQuestions",
                analysis.totalQuestions()
        );
    }


    // =========================================================
    // EMPTY COMPETENCY DATA
    // =========================================================

    private void addEmptyCompetencyData(
            Model model
    ) {

        model.addAttribute(
                "competencies",
                List.of()
        );

        model.addAttribute(
                "totalCompetencies",
                0
        );

        model.addAttribute(
                "achievedCompetencies",
                0
        );

        model.addAttribute(
                "averageLevel",
                "0%"
        );
    }


    // =========================================================
    // EMPTY SKILL GAP DATA
    // =========================================================

    private void addEmptySkillGapData(
            Model model
    ) {

        model.addAttribute(
                "skillGaps",
                List.of()
        );

        model.addAttribute(
                "totalSkillGaps",
                0
        );

        model.addAttribute(
                "highGaps",
                0
        );

        model.addAttribute(
                "mediumGaps",
                0
        );

        model.addAttribute(
                "lowGaps",
                0
        );
    }


    // =========================================================
    // COUNT GAP
    // =========================================================

    private long countGap(
            List<Map<String, Object>> skillGaps,
            String gap
    ) {

        return skillGaps.stream()
                .filter(item ->
                        gap.equalsIgnoreCase(
                                String.valueOf(
                                        item.get("gap")
                                )
                        )
                )
                .count();
    }


    // =========================================================
    // CURRENT LEVEL
    // =========================================================

    private String convertPercentageToLevel(
            int percentage
    ) {

        if (percentage >= 80) {
            return "ADVANCED";
        }

        if (percentage >= 60) {
            return "INTERMEDIATE";
        }

        return "BEGINNER";
    }


    // =========================================================
    // GAP PRIORITY
    // =========================================================

    private String calculateGapPriority(
            int percentage
    ) {

        if (percentage < 40) {
            return "HIGH";
        }

        if (percentage < 60) {
            return "MEDIUM";
        }

        return "LOW";
    }


    // =========================================================
    // SAFE PERCENTAGE
    // =========================================================

    private int safePercentage(
            Integer percentage
    ) {

        if (percentage == null) {
            return 0;
        }


        return Math.max(
                0,
                Math.min(
                        100,
                        percentage
                )
        );
    }


    // =========================================================
    // MAP PROGRESS HELPER
    // =========================================================

    private int getProgress(
            Map<String, Object> data
    ) {

        if (data == null) {
            return 0;
        }


        Object value =
                data.get("progress");


        if (value instanceof Number number) {

            return number.intValue();
        }


        return 0;
    }
}