package com.capacitix.controller;

import com.capacitix.entity.EmployeeTrainingProgress;
import com.capacitix.entity.EmployeeVideoAssignment;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.TrainingModule;
import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.EmployeeTrainingService;
import com.capacitix.service.EmployeeVideoAssignmentService;
import com.capacitix.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/employee/training")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeTrainingController {

    private final UserRepository userRepository;

    private final LearningPathService learningPathService;

    private final EmployeeTrainingService employeeTrainingService;

    private final EmployeeVideoAssignmentService employeeVideoAssignmentService;


    // =========================================================
    // TRAINING HOME
    // =========================================================

    @GetMapping
    public String trainingHome(
            Authentication authentication,
            Model model
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        // =====================================================
        // GET CURRENT LEARNING PATH
        // =====================================================

        LearningPath learningPath =
                employeeTrainingService
                        .getActiveLearningPath(employee)
                        .orElseGet(
                                () -> employeeTrainingService
                                        .getLatestLearningPath(employee)
                                        .orElse(null)
                        );


        // =====================================================
        // GET TRAINING ITEMS
        // =====================================================

        List<LearningPathItem> trainingItems =
                learningPath != null
                        ? learningPathService
                                .getLearningPathItems(
                                        learningPath
                                )
                        : List.of();


        // =====================================================
        // GET EMPLOYEE PROGRESS
        // =====================================================

        List<EmployeeTrainingProgress> progressList =
                employeeTrainingService
                        .getEmployeeProgress(employee);


        // =====================================================
        // SYNCHRONIZE DISPLAYED ITEM PROGRESS
        // =====================================================

        syncItemProgress(
                trainingItems,
                progressList,
                employee
        );


        // =====================================================
        // OVERALL TRAINING PROGRESS
        // =====================================================

        int overallProgress =
                employeeTrainingService
                        .getOverallProgress(employee);


        // =====================================================
        // COMPLETED MODULES
        // =====================================================

        long completedModules =
                employeeTrainingService
                        .getCompletedModuleCount(employee);


        // =====================================================
        // TOTAL MODULES
        // =====================================================

        long totalModules =
                employeeTrainingService
                        .getTotalModuleCount(employee);


        // =====================================================
        // TRAINER-ASSIGNED RESOURCES
        // =====================================================

        List<EmployeeVideoAssignment> assignedResources =
                employeeVideoAssignmentService
                        .getEmployeeVideos(employee);


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "learningPath",
                learningPath
        );

        model.addAttribute(
                "trainingItems",
                trainingItems
        );

        model.addAttribute(
                "progressList",
                progressList
        );

        model.addAttribute(
                "overallProgress",
                overallProgress
        );

        model.addAttribute(
                "completedModules",
                completedModules
        );

        model.addAttribute(
                "totalModules",
                totalModules
        );

        model.addAttribute(
                "hasLearningPath",
                learningPath != null
        );

        model.addAttribute(
                "hasTrainingModules",
                !trainingItems.isEmpty()
        );

        model.addAttribute(
                "assignedResources",
                assignedResources
        );

        model.addAttribute(
                "hasAssignedResources",
                assignedResources != null
                        && !assignedResources.isEmpty()
        );


        return "employee/training";
    }


    // =========================================================
    // TRAINING DETAILS
    // =========================================================

    @GetMapping("/details")
    public String trainingDetails(
            Authentication authentication,
            Model model
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        // =====================================================
        // GET CURRENT LEARNING PATH
        // =====================================================

        LearningPath learningPath =
                employeeTrainingService
                        .getActiveLearningPath(employee)
                        .orElseGet(
                                () -> employeeTrainingService
                                        .getLatestLearningPath(employee)
                                        .orElse(null)
                        );


        // =====================================================
        // LOAD ASSIGNED RESOURCES
        // =====================================================

        List<EmployeeVideoAssignment> assignedResources =
                employeeVideoAssignmentService
                        .getEmployeeVideos(employee);


        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "assignedResources",
                assignedResources
        );

        model.addAttribute(
                "hasAssignedResources",
                assignedResources != null
                        && !assignedResources.isEmpty()
        );


        // =====================================================
        // NO LEARNING PATH
        // =====================================================

        if (learningPath == null) {

            model.addAttribute(
                    "trainingItems",
                    List.of()
            );

            model.addAttribute(
                    "progressList",
                    List.of()
            );

            model.addAttribute(
                    "overallProgress",
                    0
            );

            model.addAttribute(
                    "completedModules",
                    0
            );

            model.addAttribute(
                    "totalModules",
                    0
            );

            model.addAttribute(
                    "hasLearningPath",
                    false
            );

            model.addAttribute(
                    "hasTrainingModules",
                    false
            );

            return "employee/training-details";
        }


        // =====================================================
        // GET LEARNING PATH ITEMS
        // =====================================================

        List<LearningPathItem> items =
                learningPathService
                        .getLearningPathItems(
                                learningPath
                        );


        // =====================================================
        // GET EMPLOYEE PROGRESS
        // =====================================================

        List<EmployeeTrainingProgress> progressList =
                employeeTrainingService
                        .getEmployeeProgress(employee);


        // =====================================================
        // SYNCHRONIZE DISPLAYED ITEMS
        // =====================================================

        syncItemProgress(
                items,
                progressList,
                employee
        );


        // =====================================================
        // OVERALL PROGRESS
        // =====================================================

        int overallProgress =
                employeeTrainingService
                        .getOverallProgress(employee);


        // =====================================================
        // MODULE COUNTS
        // =====================================================

        long completedModules =
                employeeTrainingService
                        .getCompletedModuleCount(employee);

        long totalModules =
                employeeTrainingService
                        .getTotalModuleCount(employee);


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "learningPath",
                learningPath
        );

        model.addAttribute(
                "trainingItems",
                items
        );

        model.addAttribute(
                "progressList",
                progressList
        );

        model.addAttribute(
                "overallProgress",
                overallProgress
        );

        model.addAttribute(
                "completedModules",
                completedModules
        );

        model.addAttribute(
                "totalModules",
                totalModules
        );

        model.addAttribute(
                "hasLearningPath",
                true
        );

        model.addAttribute(
                "hasTrainingModules",
                !items.isEmpty()
        );


        return "employee/training-details";
    }


    // =========================================================
    // OPEN TRAINING MODULE
    // =========================================================
    //
    // IMPORTANT:
    //
    // When an employee opens an AI-generated module,
    // the module is automatically registered as started.
    //
    // First access gives 5% progress.
    //
    // This makes the Overall Progress value change
    // immediately when the employee accesses training.
    //
    // =========================================================

    @GetMapping("/module/{itemId}")
    public String openTrainingModule(
            @PathVariable Long itemId,
            Authentication authentication,
            Model model
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        // =====================================================
        // SECURELY FIND EMPLOYEE'S TRAINING ITEM
        // =====================================================

        LearningPathItem item =
                getEmployeeLearningPathItem(
                        employee,
                        itemId
                );


        // =====================================================
        // AUTOMATICALLY START TRAINING
        // =====================================================
        //
        // This is the important change.
        //
        // Opening the module counts as first access.
        //
        // The service will:
        //
        // started       = true
        // startedAt     = current time
        // lastAccessed  = current time
        // progress      = 5% if this is first access
        // status        = IN_PROGRESS
        //
        // If the module was already at 50%, 80% or 100%,
        // its existing progress is preserved.
        //
        // =====================================================

        EmployeeTrainingProgress progress =
                employeeTrainingService.startTraining(
                        employee,
                        item
                );


        // =====================================================
        // TRAINER ASSIGNED RESOURCES
        // =====================================================

        List<EmployeeVideoAssignment> assignedResources =
                employeeVideoAssignmentService
                        .getEmployeeVideos(employee);


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "trainingItem",
                item
        );

        model.addAttribute(
                "trainingModule",
                item.getTrainingModule()
        );

        model.addAttribute(
                "progress",
                progress
        );

        model.addAttribute(
                "learningPath",
                item.getLearningPath()
        );

        model.addAttribute(
                "assignedResources",
                assignedResources
        );

        model.addAttribute(
                "hasAssignedResources",
                assignedResources != null
                        && !assignedResources.isEmpty()
        );


        return "employee/training-module";
    }


    // =========================================================
    // SECURE TRAINING VIDEO
    // =========================================================

    @GetMapping("/module/{itemId}/video")
    @ResponseBody
    public ResponseEntity<Resource> streamTrainingVideo(
            @PathVariable Long itemId,
            Authentication authentication
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        LearningPathItem item =
                getEmployeeLearningPathItem(
                        employee,
                        itemId
                );


        TrainingModule trainingModule =
                item.getTrainingModule();


        if (trainingModule == null) {
            return ResponseEntity.notFound().build();
        }


        String videoPath =
                trainingModule.getVideoPath();


        if (videoPath == null
                || videoPath.isBlank()) {

            return ResponseEntity.notFound().build();
        }


        try {

            Path path =
                    Paths.get(videoPath)
                            .toAbsolutePath()
                            .normalize();


            Resource resource =
                    new FileSystemResource(path);


            if (!resource.exists()
                    || !resource.isReadable()) {

                return ResponseEntity.notFound().build();
            }


            String contentType =
                    trainingModule.getVideoContentType();


            if (contentType == null
                    || contentType.isBlank()) {

                contentType =
                        Files.probeContentType(path);
            }


            if (contentType == null
                    || contentType.isBlank()) {

                contentType =
                        "video/mp4";
            }


            MediaType mediaType;

            try {

                mediaType =
                        MediaType.parseMediaType(
                                contentType
                        );

            } catch (Exception exception) {

                mediaType =
                        MediaType.APPLICATION_OCTET_STREAM;
            }


            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" +
                                    sanitizeFileName(
                                            trainingModule
                                                    .getVideoFileName()
                                    ) +
                                    "\""
                    )
                    .header(
                            HttpHeaders.ACCEPT_RANGES,
                            "bytes"
                    )
                    .body(resource);


        } catch (Exception exception) {

            return ResponseEntity
                    .internalServerError()
                    .build();
        }
    }


    // =========================================================
    // MANUAL START TRAINING
    // =========================================================

    @PostMapping("/module/{itemId}/start")
    public String startTraining(
            @PathVariable Long itemId,
            Authentication authentication
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        LearningPathItem item =
                getEmployeeLearningPathItem(
                        employee,
                        itemId
                );


        employeeTrainingService.startTraining(
                employee,
                item
        );


        return "redirect:/employee/training/module/"
                + itemId;
    }


    // =========================================================
    // UPDATE PROGRESS
    // =========================================================

    @PostMapping("/module/{itemId}/progress")
    public String updateProgress(
            @PathVariable Long itemId,
            @RequestParam Integer progressPercentage,
            Authentication authentication
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        LearningPathItem item =
                getEmployeeLearningPathItem(
                        employee,
                        itemId
                );


        int progress =
                progressPercentage == null
                        ? 0
                        : Math.max(
                                0,
                                Math.min(
                                        100,
                                        progressPercentage
                                )
                        );


        employeeTrainingService.updateProgress(
                employee,
                item,
                progress
        );


        return "redirect:/employee/training/module/"
                + itemId;
    }


    // =========================================================
    // ADD TIME SPENT
    // =========================================================

    @PostMapping("/module/{itemId}/time")
    public String addTimeSpent(
            @PathVariable Long itemId,
            @RequestParam Integer minutes,
            Authentication authentication
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        LearningPathItem item =
                getEmployeeLearningPathItem(
                        employee,
                        itemId
                );


        int safeMinutes =
                minutes == null
                        ? 0
                        : Math.max(
                                0,
                                minutes
                        );


        if (safeMinutes > 0) {

            employeeTrainingService.addTimeSpent(
                    employee,
                    item,
                    safeMinutes
            );
        }


        return "redirect:/employee/training/module/"
                + itemId;
    }


    // =========================================================
    // COMPLETE TRAINING
    // =========================================================

    @PostMapping("/module/{itemId}/complete")
    public String completeTraining(
            @PathVariable Long itemId,
            Authentication authentication
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        LearningPathItem item =
                getEmployeeLearningPathItem(
                        employee,
                        itemId
                );


        employeeTrainingService.completeTraining(
                employee,
                item
        );


        return "redirect:/employee/training";
    }


    // =========================================================
    // UPDATE ASSESSMENT SCORE
    // =========================================================

    @PostMapping("/module/{itemId}/assessment-score")
    public String updateAssessmentScore(
            @PathVariable Long itemId,
            @RequestParam Double score,
            Authentication authentication
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        LearningPathItem item =
                getEmployeeLearningPathItem(
                        employee,
                        itemId
                );


        double safeScore =
                score == null
                        ? 0.0
                        : Math.max(
                                0.0,
                                Math.min(
                                        100.0,
                                        score
                                )
                        );


        employeeTrainingService.updateAssessmentScore(
                employee,
                item,
                safeScore
        );


        return "redirect:/employee/training/module/"
                + itemId;
    }


    // =========================================================
    // FEEDBACK
    // =========================================================

    @PostMapping("/module/{itemId}/feedback")
    public String updateFeedback(
            @PathVariable Long itemId,
            @RequestParam String feedback,
            Authentication authentication
    ) {

        User employee =
                getLoggedInEmployee(authentication);


        LearningPathItem item =
                getEmployeeLearningPathItem(
                        employee,
                        itemId
                );


        employeeTrainingService.updateFeedback(
                employee,
                item,
                feedback
        );


        return "redirect:/employee/training/module/"
                + itemId;
    }


    // =========================================================
    // SYNCHRONIZE ITEM PROGRESS
    // =========================================================

    private void syncItemProgress(
            List<LearningPathItem> items,
            List<EmployeeTrainingProgress> progressList,
            User employee
    ) {

        if (items == null
                || items.isEmpty()
                || progressList == null
                || progressList.isEmpty()) {

            return;
        }


        for (LearningPathItem item : items) {

            if (item == null
                    || item.getId() == null) {

                continue;
            }


            for (EmployeeTrainingProgress progress :
                    progressList) {

                if (progress == null
                        || progress.getLearningPathItem() == null
                        || progress.getLearningPathItem().getId() == null) {

                    continue;
                }


                if (!item.getId().equals(
                        progress
                                .getLearningPathItem()
                                .getId()
                )) {

                    continue;
                }


                // =================================================
                // EMPLOYEE OWNERSHIP CHECK
                // =================================================

                if (progress.getEmployee() != null
                        && employee != null
                        && progress.getEmployee().getId() != null
                        && employee.getId() != null
                        && !employee.getId().equals(
                                progress.getEmployee().getId()
                        )) {

                    continue;
                }


                // =================================================
                // COPY REAL EMPLOYEE PROGRESS
                // =================================================

                Integer progressPercentage =
                        progress.getProgressPercentage();


                if (progressPercentage == null) {
                    progressPercentage = 0;
                }


                int safeProgress =
                        Math.max(
                                0,
                                Math.min(
                                        100,
                                        progressPercentage
                                )
                        );


                item.setProgressPercentage(
                        safeProgress
                );


                // =================================================
                // SYNCHRONIZE DISPLAY STATUS
                // =================================================

                if (safeProgress >= 100) {

                    item.setStatus(
                            LearningPathItem.ItemStatus.COMPLETED
                    );

                } else if (safeProgress > 0) {

                    item.setStatus(
                            LearningPathItem.ItemStatus.IN_PROGRESS
                    );

                } else {

                    item.setStatus(
                            LearningPathItem.ItemStatus.NOT_STARTED
                    );
                }


                break;
            }
        }
    }


    // =========================================================
    // GET LOGGED-IN EMPLOYEE
    // =========================================================

    private User getLoggedInEmployee(
            Authentication authentication
    ) {

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new IllegalStateException(
                    "Authenticated employee was not found."
            );
        }


        return userRepository
                .findByEmail(
                        authentication.getName()
                )
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Employee account was not found."
                        )
                );
    }


    // =========================================================
    // GET EMPLOYEE LEARNING PATH ITEM
    // =========================================================

    private LearningPathItem getEmployeeLearningPathItem(
            User employee,
            Long itemId
    ) {

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee is required."
            );
        }


        if (itemId == null) {

            throw new IllegalArgumentException(
                    "Training item ID is required."
            );
        }


        List<LearningPath> paths =
                learningPathService
                        .getEmployeeLearningPaths(
                                employee
                        );


        for (LearningPath path : paths) {

            List<LearningPathItem> items =
                    learningPathService
                            .getLearningPathItems(
                                    path
                            );


            for (LearningPathItem item : items) {

                if (item != null
                        && itemId.equals(
                                item.getId()
                        )) {

                    return item;
                }
            }
        }


        throw new IllegalArgumentException(
                "Training module does not belong "
                        + "to the logged-in employee."
        );
    }


    // =========================================================
    // SANITIZE FILE NAME
    // =========================================================

    private String sanitizeFileName(
            String fileName
    ) {

        if (fileName == null
                || fileName.isBlank()) {

            return "training-resource";
        }


        return Paths.get(fileName)
                .getFileName()
                .toString()
                .replace("\"", "")
                .replace("\r", "")
                .replace("\n", "");
    }
}