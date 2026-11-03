package com.capacitix.controller;

import com.capacitix.entity.EmployeeTrainingProgress;
import com.capacitix.entity.EmployeeVideoAssignment;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.TrainingModule;
import com.capacitix.entity.TrainingVideo;
import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.EmployeeTrainingService;
import com.capacitix.service.EmployeeVideoAssignmentService;
import com.capacitix.service.LearningPathService;

import lombok.RequiredArgsConstructor;

import org.hibernate.Hibernate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
    @Transactional(readOnly = true)
    public String trainingHome(
            Authentication authentication,
            Model model) {

        User employee = getLoggedInEmployee(authentication);

        LearningPath learningPath =
                employeeTrainingService.getActiveLearningPath(employee)
                        .orElseGet(() ->
                                employeeTrainingService
                                        .getLatestLearningPath(employee)
                                        .orElse(null));

        List<LearningPathItem> trainingItems =
                learningPath != null
                        ? learningPathService.getLearningPathItems(learningPath)
                        : List.of();

        initializeTrainingItems(trainingItems);

        List<EmployeeTrainingProgress> progressList =
                employeeTrainingService.getEmployeeProgress(employee);

        syncItemProgress(trainingItems, progressList, employee);

        int overallProgress =
                employeeTrainingService.getOverallProgress(employee);

        long completedModules =
                employeeTrainingService.getCompletedModuleCount(employee);

        long totalModules =
                employeeTrainingService.getTotalModuleCount(employee);

        List<EmployeeVideoAssignment> assignedResources =
                loadAssignedResources(employee);

        addTrainingHomeAttributes(
                model,
                employee,
                learningPath,
                trainingItems,
                progressList,
                overallProgress,
                completedModules,
                totalModules,
                assignedResources
        );

        return "employee/training";
    }

    // =========================================================
    // TRAINING DETAILS
    // =========================================================

    @GetMapping("/details")
    @Transactional(readOnly = true)
    public String trainingDetails(
            Authentication authentication,
            Model model) {

        User employee = getLoggedInEmployee(authentication);

        LearningPath learningPath =
                employeeTrainingService.getActiveLearningPath(employee)
                        .orElseGet(() ->
                                employeeTrainingService
                                        .getLatestLearningPath(employee)
                                        .orElse(null));

        List<EmployeeVideoAssignment> assignedResources =
                loadAssignedResources(employee);

        model.addAttribute("employee", employee);
        model.addAttribute("assignedResources", assignedResources);
        model.addAttribute(
                "hasAssignedResources",
                !assignedResources.isEmpty()
        );

        if (learningPath == null) {

            model.addAttribute("learningPath", null);
            model.addAttribute("trainingItems", List.of());
            model.addAttribute("progressList", List.of());
            model.addAttribute("overallProgress", 0);
            model.addAttribute("completedModules", 0);
            model.addAttribute("totalModules", 0);
            model.addAttribute("hasLearningPath", false);
            model.addAttribute("hasTrainingModules", false);

            return "employee/training-details";
        }

        List<LearningPathItem> items =
                learningPathService.getLearningPathItems(learningPath);

        initializeTrainingItems(items);

        List<EmployeeTrainingProgress> progressList =
                employeeTrainingService.getEmployeeProgress(employee);

        syncItemProgress(items, progressList, employee);

        int overallProgress =
                employeeTrainingService.getOverallProgress(employee);

        long completedModules =
                employeeTrainingService.getCompletedModuleCount(employee);

        long totalModules =
                employeeTrainingService.getTotalModuleCount(employee);

        model.addAttribute("learningPath", learningPath);
        model.addAttribute("trainingItems", items);
        model.addAttribute("progressList", progressList);
        model.addAttribute("overallProgress", overallProgress);
        model.addAttribute("completedModules", completedModules);
        model.addAttribute("totalModules", totalModules);
        model.addAttribute("hasLearningPath", true);
        model.addAttribute("hasTrainingModules", !items.isEmpty());

        return "employee/training-details";
    }

    // =========================================================
    // OPEN TRAINING MODULE
    // =========================================================

    @GetMapping("/module/{itemId}")
    @Transactional
    public String openTrainingModule(
            @PathVariable Long itemId,
            Authentication authentication,
            Model model) {

        User employee = getLoggedInEmployee(authentication);

        LearningPathItem item =
                getEmployeeLearningPathItem(employee, itemId);

        initializeTrainingItem(item);

        EmployeeTrainingProgress progress =
                employeeTrainingService.startTraining(employee, item);

        List<EmployeeVideoAssignment> assignedResources =
                loadAssignedResources(employee);

        model.addAttribute("employee", employee);
        model.addAttribute("trainingItem", item);
        model.addAttribute("trainingModule", item.getTrainingModule());
        model.addAttribute("progress", progress);
        model.addAttribute("learningPath", item.getLearningPath());
        model.addAttribute("assignedResources", assignedResources);
        model.addAttribute(
                "hasAssignedResources",
                !assignedResources.isEmpty()
        );

        return "employee/training-module";
    }

    // =========================================================
    // SECURE TRAINING MODULE RESOURCE
    // =========================================================

    @GetMapping("/module/{itemId}/video")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> streamTrainingVideo(
            @PathVariable Long itemId,
            Authentication authentication) {

        User employee = getLoggedInEmployee(authentication);

        LearningPathItem item =
                getEmployeeLearningPathItem(employee, itemId);

        initializeTrainingItem(item);

        TrainingModule trainingModule = item.getTrainingModule();

        if (trainingModule == null) {
            return ResponseEntity.notFound().build();
        }

        String videoPath = trainingModule.getVideoPath();

        if (videoPath == null || videoPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        return serveFile(
                videoPath,
                trainingModule.getVideoContentType(),
                trainingModule.getVideoFileName(),
                true
        );
    }

    // =========================================================
    // MANUAL START TRAINING
    // =========================================================

    @PostMapping("/module/{itemId}/start")
    @Transactional
    public String startTraining(
            @PathVariable Long itemId,
            Authentication authentication) {

        User employee = getLoggedInEmployee(authentication);

        LearningPathItem item =
                getEmployeeLearningPathItem(employee, itemId);

        employeeTrainingService.startTraining(employee, item);

        return "redirect:/employee/training/module/" + itemId;
    }

    // =========================================================
    // UPDATE PROGRESS
    // =========================================================

    @PostMapping("/module/{itemId}/progress")
    @Transactional
    public String updateProgress(
            @PathVariable Long itemId,
            @RequestParam Integer progressPercentage,
            Authentication authentication) {

        User employee = getLoggedInEmployee(authentication);

        LearningPathItem item =
                getEmployeeLearningPathItem(employee, itemId);

        int progress = progressPercentage == null
                ? 0
                : Math.max(0, Math.min(100, progressPercentage));

        employeeTrainingService.updateProgress(employee, item, progress);

        return "redirect:/employee/training/module/" + itemId;
    }

    // =========================================================
    // ADD TIME SPENT
    // =========================================================

    @PostMapping("/module/{itemId}/time")
    @Transactional
    public String addTimeSpent(
            @PathVariable Long itemId,
            @RequestParam Integer minutes,
            Authentication authentication) {

        User employee = getLoggedInEmployee(authentication);

        LearningPathItem item =
                getEmployeeLearningPathItem(employee, itemId);

        int safeMinutes = minutes == null ? 0 : Math.max(0, minutes);

        if (safeMinutes > 0) {
            employeeTrainingService.addTimeSpent(
                    employee,
                    item,
                    safeMinutes
            );
        }

        return "redirect:/employee/training/module/" + itemId;
    }

    // =========================================================
    // COMPLETE TRAINING
    // =========================================================

    @PostMapping("/module/{itemId}/complete")
    @Transactional
    public String completeTraining(
            @PathVariable Long itemId,
            Authentication authentication) {

        User employee = getLoggedInEmployee(authentication);

        LearningPathItem item =
                getEmployeeLearningPathItem(employee, itemId);

        employeeTrainingService.completeTraining(employee, item);

        return "redirect:/employee/training";
    }

    // =========================================================
    // UPDATE ASSESSMENT SCORE
    // =========================================================

    @PostMapping("/module/{itemId}/assessment-score")
    @Transactional
    public String updateAssessmentScore(
            @PathVariable Long itemId,
            @RequestParam Double score,
            Authentication authentication) {

        User employee = getLoggedInEmployee(authentication);

        LearningPathItem item =
                getEmployeeLearningPathItem(employee, itemId);

        double safeScore = score == null
                ? 0.0
                : Math.max(0.0, Math.min(100.0, score));

        employeeTrainingService.updateAssessmentScore(
                employee,
                item,
                safeScore
        );

        return "redirect:/employee/training/module/" + itemId;
    }

    // =========================================================
    // FEEDBACK
    // =========================================================

    @PostMapping("/module/{itemId}/feedback")
    @Transactional
    public String updateFeedback(
            @PathVariable Long itemId,
            @RequestParam String feedback,
            Authentication authentication) {

        User employee = getLoggedInEmployee(authentication);

        LearningPathItem item =
                getEmployeeLearningPathItem(employee, itemId);

        employeeTrainingService.updateFeedback(
                employee,
                item,
                feedback
        );

        return "redirect:/employee/training/module/" + itemId;
    }

    // =========================================================
    // LOAD TRAINER-ASSIGNED RESOURCES
    // =========================================================

    private List<EmployeeVideoAssignment> loadAssignedResources(
            User employee) {

        List<EmployeeVideoAssignment> assignments =
                employeeVideoAssignmentService.getEmployeeVideos(employee);

        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }

        return assignments.stream()
                .filter(assignment -> assignment != null)
                .filter(assignment -> {
                    TrainingVideo video = assignment.getVideo();

                    if (video == null) {
                        return false;
                    }

                    Hibernate.initialize(video);

                    video.getTitle();
                    video.getDescription();
                    video.getOriginalFileName();
                    video.getResourceType();
                    video.getContentType();
                    video.getFilePath();

                    return video.isActive();
                })
                .toList();
    }

    // =========================================================
    // SERVE FILE
    // =========================================================

    private ResponseEntity<Resource> serveFile(
            String filePath,
            String contentType,
            String fileName,
            boolean inline) {

        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {

            Path path = Paths.get(filePath)
                    .toAbsolutePath()
                    .normalize();

            Resource resource = new FileSystemResource(path);

            if (!resource.exists()
                    || !resource.isReadable()
                    || !Files.isRegularFile(path)) {
                return ResponseEntity.notFound().build();
            }

            String resolvedContentType = contentType;

            if (resolvedContentType == null
                    || resolvedContentType.isBlank()) {
                resolvedContentType = Files.probeContentType(path);
            }

            if (resolvedContentType == null
                    || resolvedContentType.isBlank()) {
                resolvedContentType =
                        MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            MediaType mediaType;

            try {
                mediaType = MediaType.parseMediaType(resolvedContentType);
            } catch (Exception ex) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String safeFileName = sanitizeFileName(
                    fileName != null && !fileName.isBlank()
                            ? fileName
                            : path.getFileName().toString()
            );

            String disposition = inline ? "inline" : "attachment";

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(Files.size(path))
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename=\""
                                    + safeFileName + "\""
                    )
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);

        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================
    // ADD TRAINING HOME MODEL ATTRIBUTES
    // =========================================================

    private void addTrainingHomeAttributes(
            Model model,
            User employee,
            LearningPath learningPath,
            List<LearningPathItem> trainingItems,
            List<EmployeeTrainingProgress> progressList,
            int overallProgress,
            long completedModules,
            long totalModules,
            List<EmployeeVideoAssignment> assignedResources) {

        model.addAttribute("employee", employee);
        model.addAttribute("learningPath", learningPath);
        model.addAttribute("trainingItems", trainingItems);
        model.addAttribute("progressList", progressList);
        model.addAttribute("overallProgress", overallProgress);
        model.addAttribute("completedModules", completedModules);
        model.addAttribute("totalModules", totalModules);
        model.addAttribute("hasLearningPath", learningPath != null);
        model.addAttribute("hasTrainingModules", !trainingItems.isEmpty());
        model.addAttribute("assignedResources", assignedResources);
        model.addAttribute(
                "hasAssignedResources",
                !assignedResources.isEmpty()
        );
    }

    // =========================================================
    // INITIALIZE TRAINING MODULE RELATIONSHIPS
    // =========================================================

    private void initializeTrainingItems(List<LearningPathItem> items) {

        if (items == null || items.isEmpty()) {
            return;
        }

        for (LearningPathItem item : items) {
            initializeTrainingItem(item);
        }
    }

    private void initializeTrainingItem(LearningPathItem item) {

        if (item == null) {
            return;
        }

        TrainingModule module = item.getTrainingModule();

        if (module != null) {
            Hibernate.initialize(module);
            module.getTitle();
        }

        LearningPath path = item.getLearningPath();

        if (path != null) {
            Hibernate.initialize(path);
        }
    }

    // =========================================================
    // SYNCHRONIZE ITEM PROGRESS
    // =========================================================

    private void syncItemProgress(
            List<LearningPathItem> items,
            List<EmployeeTrainingProgress> progressList,
            User employee) {

        if (items == null || items.isEmpty()
                || progressList == null || progressList.isEmpty()) {
            return;
        }

        for (LearningPathItem item : items) {

            if (item == null || item.getId() == null) {
                continue;
            }

            for (EmployeeTrainingProgress progress : progressList) {

                if (progress == null
                        || progress.getLearningPathItem() == null
                        || progress.getLearningPathItem().getId() == null) {
                    continue;
                }

                if (!item.getId().equals(
                        progress.getLearningPathItem().getId())) {
                    continue;
                }

                if (progress.getEmployee() != null
                        && employee != null
                        && progress.getEmployee().getId() != null
                        && employee.getId() != null
                        && !employee.getId().equals(
                                progress.getEmployee().getId())) {
                    continue;
                }

                Integer progressPercentage =
                        progress.getProgressPercentage();

                if (progressPercentage == null) {
                    progressPercentage = 0;
                }

                int safeProgress =
                        Math.max(0, Math.min(100, progressPercentage));

                item.setProgressPercentage(safeProgress);

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

    private User getLoggedInEmployee(Authentication authentication) {

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new IllegalStateException(
                    "Authenticated employee was not found."
            );
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Employee account was not found."
                        ));
    }

    // =========================================================
    // GET EMPLOYEE LEARNING PATH ITEM
    // =========================================================

    private LearningPathItem getEmployeeLearningPathItem(
            User employee,
            Long itemId) {

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
                learningPathService.getEmployeeLearningPaths(employee);

        for (LearningPath path : paths) {

            List<LearningPathItem> items =
                    learningPathService.getLearningPathItems(path);

            for (LearningPathItem item : items) {

                if (item != null
                        && itemId.equals(item.getId())) {
                    return item;
                }
            }
        }

        throw new IllegalArgumentException(
                "Training module does not belong to the logged-in employee."
        );
    }

    // =========================================================
    // SANITIZE FILE NAME
    // =========================================================

    private String sanitizeFileName(String fileName) {

        if (fileName == null || fileName.isBlank()) {
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