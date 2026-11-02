package com.capacitix.controller;

import com.capacitix.entity.EmployeeTrainingProgress;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.PracticalTask;
import com.capacitix.entity.User;
import com.capacitix.repository.EmployeeTrainingProgressRepository;
import com.capacitix.repository.LearningPathRepository;
import com.capacitix.repository.PracticalTaskRepository;
import com.capacitix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/employee")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeCertificateController {

    private final UserRepository userRepository;

    private final LearningPathRepository learningPathRepository;

    private final EmployeeTrainingProgressRepository
            employeeTrainingProgressRepository;

    private final PracticalTaskRepository practicalTaskRepository;


    // =========================================================
    // EMPLOYEE CERTIFICATES PAGE
    // =========================================================

    @GetMapping("/certificates")
    public String certificates(
            Model model,
            org.springframework.security.core.Authentication authentication) {

        // =====================================================
        // GET LOGGED-IN EMPLOYEE
        // =====================================================

        String email = authentication.getName();

        User employee =
                userRepository.findByEmail(email)
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

        String fullName =
                safeText(
                        employee.getFullName(),
                        "Employee"
                );

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "userName",
                fullName
        );

        model.addAttribute(
                "employeeName",
                fullName
        );

        model.addAttribute(
                "userInitial",
                getInitial(fullName)
        );


        // =====================================================
        // GET ALL LEARNING PATHS
        // =====================================================

        List<LearningPath> allLearningPaths;

        try {

            allLearningPaths =
                    learningPathRepository.findAll();

        } catch (Exception e) {

            allLearningPaths =
                    new ArrayList<>();

            System.err.println(
                    "Certificate learning path error: "
                            + e.getMessage()
            );
        }


        // =====================================================
        // FILTER EMPLOYEE LEARNING PATHS
        // =====================================================

        List<LearningPath> employeeLearningPaths =
                allLearningPaths.stream()
                        .filter(path ->
                                path != null
                                        && path.getEmployee() != null
                                        && path.getEmployee().getId() != null
                                        && employee.getId() != null
                                        && path.getEmployee()
                                                .getId()
                                                .equals(employee.getId())
                        )
                        .sorted(
                                Comparator.comparing(
                                        LearningPath::getCreatedAt,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                        )
                        .toList();


        // =====================================================
        // GET EMPLOYEE TRAINING PROGRESS
        // =====================================================

        List<EmployeeTrainingProgress> progressRecords;

        try {

            progressRecords =
                    employeeTrainingProgressRepository
                            .findByEmployee(employee);

        } catch (Exception e) {

            progressRecords =
                    new ArrayList<>();

            System.err.println(
                    "Certificate training progress error: "
                            + e.getMessage()
            );
        }


        // =====================================================
        // GET EMPLOYEE PRACTICAL TASKS
        // =====================================================

        List<PracticalTask> practicalTasks;

        try {

            practicalTasks =
                    practicalTaskRepository
                            .findByEmployeeOrderByCreatedAtDesc(
                                    employee
                            );

        } catch (Exception e) {

            practicalTasks =
                    new ArrayList<>();

            System.err.println(
                    "Certificate practical task error: "
                            + e.getMessage()
            );
        }


        // =====================================================
        // BUILD EARNED CERTIFICATES
        // =====================================================

        List<CertificateView> certificates =
                new ArrayList<>();

        for (LearningPath learningPath : employeeLearningPaths) {

            if (learningPath == null) {
                continue;
            }


            // -------------------------------------------------
            // CHECK LEARNING PATH COMPLETION
            // -------------------------------------------------

            if (!isLearningPathCompleted(
                    learningPath,
                    progressRecords
            )) {

                continue;
            }


            // -------------------------------------------------
            // GET LEARNING PATH ITEMS
            // -------------------------------------------------

            List<LearningPathItem> items =
                    learningPath.getItems();

            if (items == null || items.isEmpty()) {
                continue;
            }


            // -------------------------------------------------
            // GET PRACTICAL TASKS FOR THIS PATH
            // -------------------------------------------------

            List<PracticalTask> pathTasks =
                    practicalTasks.stream()
                            .filter(task ->
                                    belongsToLearningPath(
                                            task,
                                            items
                                    )
                            )
                            .toList();


            // -------------------------------------------------
            // REQUIRE PRACTICAL TASK
            // -------------------------------------------------

            if (pathTasks.isEmpty()) {
                continue;
            }


            // -------------------------------------------------
            // ALL PRACTICAL TASKS MUST BE COMPLETED
            // -------------------------------------------------

            boolean allTasksCompleted =
                    pathTasks.stream()
                            .allMatch(
                                    this::isPracticalTaskCompleted
                            );

            if (!allTasksCompleted) {
                continue;
            }


            // -------------------------------------------------
            // CERTIFICATE TITLE
            // -------------------------------------------------

            String title =
                    safeText(
                            learningPath.getTitle(),
                            "Personalized Learning Path"
                    );


            // -------------------------------------------------
            // CERTIFICATE SKILLS
            // -------------------------------------------------

            String skills =
                    buildSkills(
                            learningPath,
                            pathTasks
                    );


            // -------------------------------------------------
            // COMPLETION DATE
            // -------------------------------------------------

            LocalDateTime completionDate =
                    findCompletionDate(
                            learningPath,
                            progressRecords,
                            pathTasks
                    );


            // -------------------------------------------------
            // CERTIFICATE ID
            // -------------------------------------------------

            String certificateId =
                    generateCertificateId(
                            employee,
                            learningPath
                    );


            // -------------------------------------------------
            // ADD CERTIFICATE
            // -------------------------------------------------

            certificates.add(
                    new CertificateView(
                            certificateId,
                            title,
                            skills,
                            formatDate(completionDate),
                            100,
                            "EARNED"
                    )
            );
        }


        // =====================================================
        // CERTIFICATE STATISTICS
        // =====================================================

        int totalCertificates =
                certificates.size();


        int skillsCertified =
                countUniqueSkills(
                        certificates
                );


        String latestCertificate =
                totalCertificates > 0
                        ? certificates.get(0).title()
                        : "No certificates yet";


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "certificates",
                certificates
        );

        model.addAttribute(
                "totalCertificates",
                totalCertificates
        );

        model.addAttribute(
                "skillsCertified",
                skillsCertified
        );

        model.addAttribute(
                "latestCertificate",
                latestCertificate
        );

        model.addAttribute(
                "notificationCount",
                0
        );


        // =====================================================
        // RETURN PAGE
        // =====================================================

        return "employee/certificates";
    }


    // =========================================================
    // CHECK LEARNING PATH COMPLETION
    // =========================================================

    private boolean isLearningPathCompleted(
            LearningPath learningPath,
            List<EmployeeTrainingProgress> progressRecords) {

        if (learningPath == null) {
            return false;
        }


        // =====================================================
        // PATH PROGRESS MUST BE 100%
        // =====================================================

        Integer pathProgress =
                learningPath.getProgressPercentage();

        if (pathProgress == null || pathProgress < 100) {
            return false;
        }


        // =====================================================
        // PATH STATUS MUST BE COMPLETED
        // =====================================================

        if (learningPath.getStatus()
                != LearningPath.LearningPathStatus.COMPLETED) {

            return false;
        }


        // =====================================================
        // GET ITEMS
        // =====================================================

        List<LearningPathItem> items =
                learningPath.getItems();

        if (items == null || items.isEmpty()) {
            return false;
        }


        // =====================================================
        // EVERY TRAINING ITEM MUST BE COMPLETED
        // =====================================================

        for (LearningPathItem item : items) {

            if (item == null) {
                continue;
            }

            boolean completed = false;


            // -------------------------------------------------
            // CHECK EMPLOYEE TRAINING PROGRESS
            // -------------------------------------------------

            if (progressRecords != null) {

                for (
                        EmployeeTrainingProgress progress
                        : progressRecords
                ) {

                    if (progress == null
                            || progress.getLearningPathItem() == null) {

                        continue;
                    }


                    LearningPathItem progressItem =
                            progress.getLearningPathItem();


                    if (progressItem.getId() == null
                            || item.getId() == null) {

                        continue;
                    }


                    if (!progressItem.getId()
                            .equals(item.getId())) {

                        continue;
                    }


                    if (progress.getStatus()
                            == EmployeeTrainingProgress
                            .TrainingStatus.COMPLETED
                            &&
                            getSafeProgress(
                                    progress.getProgressPercentage()
                            ) >= 100) {

                        completed = true;
                    }


                    break;
                }
            }


            // -------------------------------------------------
            // FALLBACK TO LEARNING PATH ITEM
            // -------------------------------------------------

            if (!completed) {

                try {

                    Integer itemProgress =
                            item.getProgressPercentage();

                    if (itemProgress != null
                            && itemProgress >= 100) {

                        Object status =
                                item.getStatus();

                        if (status != null
                                && "COMPLETED"
                                .equalsIgnoreCase(
                                        status.toString()
                                )) {

                            completed = true;
                        }
                    }

                } catch (Exception ignored) {
                    // Ignore item status fallback errors
                }
            }


            // -------------------------------------------------
            // ITEM NOT COMPLETED
            // -------------------------------------------------

            if (!completed) {
                return false;
            }
        }


        return true;
    }


    // =========================================================
    // CHECK PRACTICAL TASK BELONGS TO LEARNING PATH
    // =========================================================

    private boolean belongsToLearningPath(
            PracticalTask task,
            List<LearningPathItem> items) {

        if (task == null
                || task.getLearningPathItem() == null) {

            return false;
        }


        LearningPathItem taskItem =
                task.getLearningPathItem();


        return belongsToLearningPath(
                taskItem,
                items
        );
    }


    // =========================================================
    // CHECK LEARNING PATH ITEM BELONGS TO LEARNING PATH
    // =========================================================
    //
    // THIS METHOD FIXES THE ERROR:
    //
    // belongsToLearningPath(
    //     progress.getLearningPathItem(),
    //     items
    // )
    //
    // =========================================================

    private boolean belongsToLearningPath(
            LearningPathItem item,
            List<LearningPathItem> items) {

        if (item == null
                || item.getId() == null
                || items == null
                || items.isEmpty()) {

            return false;
        }


        Long itemId =
                item.getId();


        return items.stream()
                .filter(existingItem ->
                        existingItem != null
                                && existingItem.getId() != null
                )
                .anyMatch(existingItem ->
                        existingItem.getId()
                                .equals(itemId)
                );
    }


    // =========================================================
    // CHECK PRACTICAL TASK COMPLETION
    // =========================================================

    private boolean isPracticalTaskCompleted(
            PracticalTask task) {

        if (task == null) {
            return false;
        }


        // -----------------------------------------------------
        // STATUS
        // -----------------------------------------------------

        if (task.getStatus()
                == PracticalTask.TaskStatus.COMPLETED) {

            return true;
        }


        // -----------------------------------------------------
        // PROGRESS FALLBACK
        // -----------------------------------------------------

        Integer progress =
                task.getProgressPercentage();

        return progress != null
                && progress >= 100;
    }


    // =========================================================
    // BUILD CERTIFICATE SKILLS
    // =========================================================

    private String buildSkills(
            LearningPath learningPath,
            List<PracticalTask> tasks) {

        Set<String> skills =
                new LinkedHashSet<>();


        // =====================================================
        // LEARNING PATH SKILL GAPS
        // =====================================================

        String skillGaps =
                learningPath.getSkillGaps();

        if (skillGaps != null
                && !skillGaps.isBlank()) {

            String[] values =
                    skillGaps.split(
                            "[,\\n;]+"
                    );

            for (String value : values) {

                String skill =
                        cleanSkill(value);

                if (!skill.isBlank()) {
                    skills.add(skill);
                }
            }
        }


        // =====================================================
        // PRACTICAL TASK SKILLS
        // =====================================================

        if (tasks != null) {

            for (PracticalTask task : tasks) {

                if (task == null) {
                    continue;
                }

                String skill =
                        cleanSkill(
                                task.getSkillName()
                        );

                if (!skill.isBlank()) {
                    skills.add(skill);
                }
            }
        }


        // =====================================================
        // FALLBACK
        // =====================================================

        if (skills.isEmpty()) {
            return "Personalized Skills";
        }


        return String.join(
                ", ",
                skills
        );
    }


    // =========================================================
    // FIND COMPLETION DATE
    // =========================================================

    private LocalDateTime findCompletionDate(
            LearningPath learningPath,
            List<EmployeeTrainingProgress> progressRecords,
            List<PracticalTask> tasks) {

        LocalDateTime latest = null;


        // =====================================================
        // TRAINING COMPLETION
        // =====================================================

        if (progressRecords != null) {

            List<LearningPathItem> items =
                    learningPath.getItems();

            if (items != null) {

                for (
                        EmployeeTrainingProgress progress
                        : progressRecords
                ) {

                    if (progress == null
                            || progress.getLearningPathItem() == null) {

                        continue;
                    }


                    // FIXED:
                    // Compare LearningPathItem with LearningPathItem
                    if (!belongsToLearningPath(
                            progress.getLearningPathItem(),
                            items
                    )) {

                        continue;
                    }


                    // Only completed training should affect
                    // certificate completion date.
                    if (progress.getStatus()
                            != EmployeeTrainingProgress
                            .TrainingStatus.COMPLETED) {

                        continue;
                    }


                    LocalDateTime completedAt =
                            progress.getCompletedAt();

                    if (completedAt != null
                            && (
                            latest == null
                                    || completedAt.isAfter(latest)
                    )) {

                        latest = completedAt;
                    }
                }
            }
        }


        // =====================================================
        // PRACTICAL TASK COMPLETION
        // =====================================================

        if (tasks != null) {

            for (PracticalTask task : tasks) {

                if (task == null) {
                    continue;
                }


                LocalDateTime completedAt =
                        task.getCompletedAt();

                if (completedAt != null
                        && (
                        latest == null
                                || completedAt.isAfter(latest)
                )) {

                    latest = completedAt;
                }
            }
        }


        // =====================================================
        // LEARNING PATH UPDATED DATE FALLBACK
        // =====================================================

        if (latest == null) {

            latest =
                    learningPath.getUpdatedAt();
        }


        // =====================================================
        // LEARNING PATH CREATED DATE FALLBACK
        // =====================================================

        if (latest == null) {

            latest =
                    learningPath.getCreatedAt();
        }


        return latest;
    }


    // =========================================================
    // GENERATE CERTIFICATE ID
    // =========================================================

    private String generateCertificateId(
            User employee,
            LearningPath learningPath) {

        Long employeeId =
                employee.getId();

        Long learningPathId =
                learningPath.getId();


        String employeePart =
                employeeId != null
                        ? employeeId.toString()
                        : "0";

        String pathPart =
                learningPathId != null
                        ? learningPathId.toString()
                        : "0";


        return "CAP-"
                + employeePart
                + "-LP-"
                + pathPart;
    }


    // =========================================================
    // COUNT UNIQUE SKILLS
    // =========================================================

    private int countUniqueSkills(
            List<CertificateView> certificates) {

        Set<String> skills =
                new LinkedHashSet<>();


        if (certificates == null) {
            return 0;
        }


        for (CertificateView certificate : certificates) {

            if (certificate == null
                    || certificate.skills() == null) {

                continue;
            }


            String[] values =
                    certificate.skills()
                            .split(",");


            for (String value : values) {

                String skill =
                        cleanSkill(value);

                if (!skill.isBlank()) {

                    skills.add(
                            skill.toLowerCase()
                    );
                }
            }
        }


        return skills.size();
    }


    // =========================================================
    // SAFE PROGRESS
    // =========================================================

    private int getSafeProgress(
            Integer progress) {

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
    // CLEAN SKILL
    // =========================================================

    private String cleanSkill(
            String value) {

        if (value == null) {
            return "";
        }


        return value
                .replace("**", "")
                .replace("-", "")
                .trim();
    }


    // =========================================================
    // FORMAT DATE
    // =========================================================

    private String formatDate(
            LocalDateTime dateTime) {

        if (dateTime == null) {
            return "Not available";
        }


        return dateTime.format(
                DateTimeFormatter.ofPattern(
                        "dd MMM yyyy"
                )
        );
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


        return value.trim();
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
    // CERTIFICATE VIEW MODEL
    // =========================================================
    //
    // IMPORTANT:
    // This is NOT a JPA entity.
    // No Certificate table is required.
    //
    // It is only data prepared for Thymeleaf.
    // =========================================================

    public record CertificateView(

            String certificateId,

            String title,

            String skills,

            String completionDate,

            int progress,

            String status

    ) {
    }
}