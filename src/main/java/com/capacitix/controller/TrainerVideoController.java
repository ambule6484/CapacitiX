
package com.capacitix.controller;

import com.capacitix.entity.EmployeeVideoAssignment;
import com.capacitix.entity.TrainingVideo;
import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.EmployeeVideoAssignmentService;
import com.capacitix.service.TrainingVideoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Slf4j
@Controller
@RequestMapping("/trainer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TRAINER')")
public class TrainerVideoController {

    private final TrainingVideoService trainingVideoService;
    private final EmployeeVideoAssignmentService employeeVideoAssignmentService;
    private final UserRepository userRepository;

    // =========================================================
    // LEARNING RESOURCE LIBRARY
    // URL: /trainer/learning-resources
    // =========================================================

    @GetMapping("/learning-resources")
    public String learningResources(
            Authentication authentication,
            Model model) {

        User trainer = getLoggedInUser(authentication);

        try {

            // Load resources uploaded by this trainer.
            List<TrainingVideo> loadedResources =
                    trainingVideoService.getTrainerVideos(trainer);

            List<TrainingVideo> resources =
                    loadedResources == null
                            ? Collections.emptyList()
                            : loadedResources;

            // Convert entities into Thymeleaf resource views.
            List<ResourceView> libraryResources =
                    resources.stream()
                            .map(this::toResourceView)
                            .toList();

            model.addAttribute("libraryResources", libraryResources);

            // Load assigned employees.
            List<User> loadedEmployees =
                    userRepository
                            .findByTrainerAndRoleAndEnabledTrueOrderByFullNameAsc(
                                    trainer,
                                    User.Role.EMPLOYEE
                            );

            List<User> employees =
                    loadedEmployees == null
                            ? Collections.emptyList()
                            : loadedEmployees;

            model.addAttribute("employees", employees);

            // Resource statistics.
            long totalResources = resources.size();

            long videoCount = resources.stream()
                    .filter(this::isVideo)
                    .count();

            long imageCount = resources.stream()
                    .filter(this::isImage)
                    .count();

            long documentCount = resources.stream()
                    .filter(this::isDocument)
                    .count();

            long activeCount = resources.stream()
                    .filter(TrainingVideo::isActive)
                    .count();

            model.addAttribute(
                    "stats",
                    new ResourceStats(
                            totalResources,
                            videoCount,
                            imageCount,
                            documentCount,
                            activeCount
                    )
            );

            // Trainer information.
            model.addAttribute("trainer", trainer);

            model.addAttribute(
                    "userName",
                    trainer.getFullName() != null
                            && !trainer.getFullName().isBlank()
                            ? trainer.getFullName()
                            : "Trainer"
            );

            model.addAttribute(
                    "userInitial",
                    getInitial(trainer.getFullName())
            );

            // Upload configuration.
            model.addAttribute(
                    "maxFileSizeBytes",
                    500L * 1024L * 1024L
            );

            model.addAttribute(
                    "maxFileSizeLabel",
                    "500 MB"
            );

            // Notifications.
            model.addAttribute("notifications", List.of());

            log.info(
                    "Learning resources loaded successfully for trainer: {}. Total resources: {}",
                    trainer.getEmail(),
                    totalResources
            );

            return "trainer/learning-resources";

        } catch (Exception e) {

            log.error(
                    "Error loading learning resources for trainer: {}",
                    trainer.getEmail(),
                    e
            );

            model.addAttribute(
                    "error",
                    "Unable to load learning resources. Please check the application logs."
            );

            // Do not silently redirect to the dashboard.
            throw new IllegalStateException(
                    "Unable to load trainer learning resources.",
                    e
            );
        }
    }

    // =========================================================
    // CONVERT ENTITY TO THYMELEAF RESOURCE VIEW
    // =========================================================

    private ResourceView toResourceView(TrainingVideo resource) {

        if (resource == null) {
            throw new IllegalArgumentException(
                    "Invalid learning resource found."
            );
        }

        List<EmployeeVideoAssignment> assignments =
                employeeVideoAssignmentService
                        .getTrainerAssignmentsForResource(resource);

        List<String> assignedTo =
                assignments == null
                        ? Collections.emptyList()
                        : assignments.stream()
                                .map(EmployeeVideoAssignment::getEmployee)
                                .filter(employee -> employee != null)
                                .map(employee -> {

                                    String name = employee.getFullName();

                                    if (name == null || name.isBlank()) {
                                        name = "Employee";
                                    }

                                    if (employee.getEmployeeId() != null
                                            && !employee.getEmployeeId().isBlank()) {

                                        return name + " • "
                                                + employee.getEmployeeId();
                                    }

                                    return name;
                                })
                                .toList();

        String type =
                resource.getResourceType() != null
                        ? resource.getResourceType().name()
                        : "UNKNOWN";

        return new ResourceView(
                resource.getId(),
                resource.getTitle(),
                type,
                getTypeLabel(type),
                getTypeIcon(type),
                resource.getDescription(),
                resource.getOriginalFileName(),
                formatFileSize(resource.getFileSize()),
                resource.getUploadedAt(),
                assignedTo,
                resource.isActive(),
                isPreviewable(resource),
                buildSearchText(resource)
        );
    }

    // =========================================================
    // RESOURCE TYPE LABEL
    // =========================================================

    private String getTypeLabel(String type) {

        return switch (type) {
            case "VIDEO" -> "Video";
            case "IMAGE" -> "Image";
            case "PDF" -> "PDF";
            case "PPT" -> "PowerPoint";
            case "WORD" -> "Word";
            default -> "Resource";
        };
    }

    // =========================================================
    // RESOURCE ICON
    // =========================================================

    private String getTypeIcon(String type) {

        return switch (type) {
            case "VIDEO" -> "▶";
            case "IMAGE" -> "🖼";
            case "PDF" -> "📄";
            case "PPT" -> "📊";
            case "WORD" -> "📝";
            default -> "▣";
        };
    }

    // =========================================================
    // PREVIEW SUPPORT
    // =========================================================

    private boolean isPreviewable(TrainingVideo resource) {

        if (resource == null || resource.getResourceType() == null) {
            return false;
        }

        return switch (resource.getResourceType().name()) {
            case "VIDEO", "IMAGE", "PDF" -> true;
            case "PPT", "WORD" -> false;
            default -> false;
        };
    }

    // =========================================================
    // SEARCH TEXT
    // =========================================================

    private String buildSearchText(TrainingVideo resource) {

        String title = resource.getTitle() != null
                ? resource.getTitle() : "";

        String description = resource.getDescription() != null
                ? resource.getDescription() : "";

        String fileName = resource.getOriginalFileName() != null
                ? resource.getOriginalFileName() : "";

        String type = resource.getResourceType() != null
                ? resource.getResourceType().name() : "";

        return (title + " " + description + " "
                + fileName + " " + type)
                .toLowerCase(Locale.ROOT);
    }

    // =========================================================
    // FILE SIZE
    // =========================================================

    private String formatFileSize(Long bytes) {

        if (bytes == null || bytes <= 0) {
            return "0 B";
        }

        if (bytes < 1024) {
            return bytes + " B";
        }

        double kb = bytes / 1024.0;

        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }

        double mb = kb / 1024.0;

        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }

        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    // =========================================================
    // UPLOAD LEARNING RESOURCE
    // URL: POST /trainer/learning-resources/upload
    // =========================================================

    @PostMapping("/learning-resources/upload")
    public String uploadResource(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false)
            String description,
            @RequestParam(value = "resourceType", required = false)
            String resourceType,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {

            User trainer = getLoggedInUser(authentication);

            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException(
                        "Please select a file to upload."
                );
            }

            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException(
                        "Resource title is required."
                );
            }

            long maxFileSize = 500L * 1024L * 1024L;

            if (file.getSize() > maxFileSize) {
                throw new IllegalArgumentException(
                        "File size cannot exceed 500 MB."
                );
            }

            trainingVideoService.uploadResource(
                    title.trim(),
                    description,
                    resourceType,
                    file,
                    trainer
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Learning resource uploaded successfully."
            );

        } catch (Exception e) {

            log.error("Learning resource upload failed.", e);

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Unable to upload learning resource."
            );
        }

        return "redirect:/trainer/learning-resources";
    }

    // =========================================================
    // VIEW RESOURCE
    // =========================================================

    @GetMapping("/learning-resources/{id}/view")
    public ResponseEntity<Resource> viewResource(
            @PathVariable Long id,
            Authentication authentication) {

        User trainer = getLoggedInUser(authentication);

        TrainingVideo resource =
                trainingVideoService.getVideoById(id);

        verifyOwnership(resource, trainer);

        if (!resource.isActive()) {
            throw new IllegalArgumentException(
                    "This learning resource is no longer available."
            );
        }

        try {

            Resource fileResource =
                    trainingVideoService.loadResourceAsResource(resource);

            MediaType mediaType = resolveMediaType(
                    resource.getContentType(),
                    resource.getOriginalFileName()
            );

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" +
                                    sanitizeFileName(resource.getOriginalFileName()) +
                                    "\""
                    )
                    .body(fileResource);

        } catch (IOException e) {

            log.error("Unable to view resource ID: {}", id, e);

            throw new IllegalArgumentException(
                    "Unable to open learning resource.",
                    e
            );
        }
    }

    // =========================================================
    // DOWNLOAD RESOURCE
    // =========================================================

    @GetMapping("/learning-resources/{id}/download")
    public ResponseEntity<Resource> downloadResource(
            @PathVariable Long id,
            Authentication authentication) {

        User trainer = getLoggedInUser(authentication);

        TrainingVideo resource =
                trainingVideoService.getVideoById(id);

        verifyOwnership(resource, trainer);

        if (!resource.isActive()) {
            throw new IllegalArgumentException(
                    "This learning resource is no longer available."
            );
        }

        try {

            Resource fileResource =
                    trainingVideoService.loadResourceAsResource(resource);

            MediaType mediaType = resolveMediaType(
                    resource.getContentType(),
                    resource.getOriginalFileName()
            );

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" +
                                    sanitizeFileName(resource.getOriginalFileName()) +
                                    "\""
                    )
                    .body(fileResource);

        } catch (IOException e) {

            log.error("Unable to download resource ID: {}", id, e);

            throw new IllegalArgumentException(
                    "Unable to download learning resource.",
                    e
            );
        }
    }

    // =========================================================
    // ASSIGN RESOURCE TO EMPLOYEE
    // =========================================================

    @PostMapping("/learning-resources/{id}/assign")
    public String assignResource(
            @PathVariable Long id,
            @RequestParam("employeeId") Long employeeId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {

            User trainer = getLoggedInUser(authentication);

            TrainingVideo resource =
                    trainingVideoService.getVideoById(id);

            verifyOwnership(resource, trainer);

            if (!resource.isActive()) {
                throw new IllegalArgumentException(
                        "This learning resource is no longer available."
                );
            }

            User employee =
                    userRepository.findByIdAndTrainerAndRole(
                                    employeeId,
                                    trainer,
                                    User.Role.EMPLOYEE
                            )
                            .filter(User::isEnabled)
                            .orElseThrow(() ->
                                    new SecurityException(
                                            "This employee is not assigned to you."
                                    )
                            );

            employeeVideoAssignmentService.assignVideoToEmployee(
                    id,
                    employee,
                    trainer
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    resource.getTitle() + " assigned to "
                            + employee.getFullName() + " successfully."
            );

        } catch (Exception e) {

            log.error("Resource assignment failed for resource ID: {}", id, e);

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Unable to assign learning resource."
            );
        }

        return "redirect:/trainer/learning-resources";
    }

    // =========================================================
    // DELETE / DEACTIVATE RESOURCE
    // =========================================================

    @PostMapping("/learning-resources/{id}/delete")
    public String deleteResource(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {

            User trainer = getLoggedInUser(authentication);

            trainingVideoService.deactivateVideo(id, trainer);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Learning resource removed successfully."
            );

        } catch (Exception e) {

            log.error("Resource deletion failed for ID: {}", id, e);

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Unable to remove learning resource."
            );
        }

        return "redirect:/trainer/learning-resources";
    }

    // =========================================================
    // OLD VIDEO URL
    // =========================================================

    @GetMapping("/videos")
    public String oldVideosUrl() {
        return "redirect:/trainer/learning-resources";
    }

    // =========================================================
    // SECURITY - RESOURCE OWNERSHIP
    // =========================================================

    private void verifyOwnership(
            TrainingVideo resource,
            User trainer) {

        if (resource == null) {
            throw new IllegalArgumentException(
                    "Learning resource not found."
            );
        }

        if (resource.getTrainer() == null
                || trainer == null
                || trainer.getId() == null
                || resource.getTrainer().getId() == null
                || !resource.getTrainer().getId().equals(trainer.getId())) {

            throw new SecurityException(
                    "You are not authorized to access this learning resource."
            );
        }
    }

    // =========================================================
    // LOGGED-IN TRAINER
    // =========================================================

    private User getLoggedInUser(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {

            throw new SecurityException(
                    "Authentication required."
            );
        }

        String email = authentication.getName();

        if (email == null || email.isBlank()) {
            throw new SecurityException(
                    "Trainer email not found."
            );
        }

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Logged-in trainer not found."
                        )
                );

        if (user.getRole() != User.Role.TRAINER) {
            throw new SecurityException(
                    "Only trainers can manage learning resources."
            );
        }

        if (!user.isEnabled()) {
            throw new SecurityException(
                    "Trainer account is disabled."
            );
        }

        return user;
    }

    // =========================================================
    // MIME TYPE
    // =========================================================

    private MediaType resolveMediaType(
            String contentType,
            String fileName) {

        if (contentType != null && !contentType.isBlank()) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                // Detect using file extension.
            }
        }

        if (fileName != null) {

            String lowerName = fileName.toLowerCase(Locale.ROOT);

            if (lowerName.endsWith(".mp4"))
                return MediaType.valueOf("video/mp4");

            if (lowerName.endsWith(".webm"))
                return MediaType.valueOf("video/webm");

            if (lowerName.endsWith(".mov"))
                return MediaType.valueOf("video/quicktime");

            if (lowerName.endsWith(".avi"))
                return MediaType.valueOf("video/x-msvideo");

            if (lowerName.endsWith(".mkv"))
                return MediaType.valueOf("video/x-matroska");

            if (lowerName.endsWith(".jpg")
                    || lowerName.endsWith(".jpeg"))
                return MediaType.IMAGE_JPEG;

            if (lowerName.endsWith(".png"))
                return MediaType.IMAGE_PNG;

            if (lowerName.endsWith(".gif"))
                return MediaType.IMAGE_GIF;

            if (lowerName.endsWith(".webp"))
                return MediaType.valueOf("image/webp");

            if (lowerName.endsWith(".pdf"))
                return MediaType.APPLICATION_PDF;

            if (lowerName.endsWith(".ppt"))
                return MediaType.valueOf(
                        "application/vnd.ms-powerpoint"
                );

            if (lowerName.endsWith(".pptx"))
                return MediaType.valueOf(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                );

            if (lowerName.endsWith(".doc"))
                return MediaType.valueOf("application/msword");

            if (lowerName.endsWith(".docx"))
                return MediaType.valueOf(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                );
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    // =========================================================
    // RESOURCE TYPE HELPERS
    // =========================================================

    private boolean isVideo(TrainingVideo resource) {

        return resource != null
                && resource.getResourceType() != null
                && "VIDEO".equals(resource.getResourceType().name());
    }

    private boolean isImage(TrainingVideo resource) {

        return resource != null
                && resource.getResourceType() != null
                && "IMAGE".equals(resource.getResourceType().name());
    }

    private boolean isDocument(TrainingVideo resource) {

        if (resource == null || resource.getResourceType() == null) {
            return false;
        }

        String type = resource.getResourceType().name();

        return "PDF".equals(type)
                || "PPT".equals(type)
                || "WORD".equals(type)
                || "DOCUMENT".equals(type);
    }

    // =========================================================
    // FILENAME SECURITY
    // =========================================================

    private String sanitizeFileName(String fileName) {

        if (fileName == null || fileName.isBlank()) {
            return "learning-resource";
        }

        return fileName
                .replace("\"", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("/", "_")
                .replace("\\", "_");
    }

    // =========================================================
    // USER INITIAL
    // =========================================================

    private String getInitial(String fullName) {

        if (fullName == null || fullName.isBlank()) {
            return "T";
        }

        return fullName.trim()
                .substring(0, 1)
                .toUpperCase(Locale.ROOT);
    }

    // =========================================================
    // THYMELEAF RESOURCE VIEW
    // =========================================================

    public record ResourceView(
            Long id,
            String title,
            String type,
            String typeLabel,
            String icon,
            String description,
            String originalFileName,
            String fileSizeLabel,
            java.time.LocalDateTime uploadedAt,
            List<String> assignedTo,
            boolean active,
            boolean previewable,
            String searchText
    ) {
    }

    // =========================================================
    // RESOURCE STATISTICS
    // =========================================================

    public record ResourceStats(
            long total,
            long videos,
            long images,
            long documents,
            long active
    ) {
    }
}
