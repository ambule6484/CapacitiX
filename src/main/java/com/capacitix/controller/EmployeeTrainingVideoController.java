
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/employee/training")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeTrainingVideoController {

    private final EmployeeVideoAssignmentService employeeVideoAssignmentService;
    private final TrainingVideoService trainingVideoService;
    private final UserRepository userRepository;

    /**
     * View assigned resource.
     *
     * GET /employee/training/resource/{assignmentId}
     */
    @GetMapping("/resource/{assignmentId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> viewResource(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        return serveAssignedResource(
                assignmentId,
                authentication,
                false
        );
    }

    /**
     * Download assigned resource.
     *
     * GET /employee/training/resource/{assignmentId}/download
     */
    @GetMapping("/resource/{assignmentId}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadResource(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        return serveAssignedResource(
                assignmentId,
                authentication,
                true
        );
    }

    /**
     * Common method for viewing and downloading resources.
     */
    private ResponseEntity<Resource> serveAssignedResource(
            Long assignmentId,
            Authentication authentication,
            boolean download) {

        try {

            log.info(
                    "Employee resource {} requested. Assignment ID: {}",
                    download ? "download" : "view",
                    assignmentId
            );

            // 1. Get logged-in employee.
            User employee = getLoggedInEmployee(authentication);

            // 2. Verify assignment belongs to logged-in employee.
            EmployeeVideoAssignment assignment =
                    employeeVideoAssignmentService.getEmployeeAssignment(
                            assignmentId,
                            employee
                    );

            if (assignment == null) {
                log.warn(
                        "Assignment not found. Assignment ID: {}",
                        assignmentId
                );

                return ResponseEntity.notFound().build();
            }

            // 3. Get assigned resource.
            TrainingVideo trainingVideo = assignment.getVideo();

            if (trainingVideo == null) {
                log.warn(
                        "Training resource is missing. Assignment ID: {}",
                        assignmentId
                );

                return ResponseEntity.notFound().build();
            }

            // 4. Check resource status.
            // The transaction keeps the lazy-loaded entity accessible.
            if (!trainingVideo.isActive()) {
                log.warn(
                        "Inactive resource requested. Assignment ID: {}",
                        assignmentId
                );

                return ResponseEntity.notFound().build();
            }

            // 5. Load actual uploaded file.
            Resource resource =
                    trainingVideoService.loadResourceAsResource(trainingVideo);

            if (resource == null
                    || !resource.exists()
                    || !resource.isReadable()) {

                log.error(
                        "Resource file is missing or unreadable. " +
                        "Assignment ID: {}, File Path: {}",
                        assignmentId,
                        trainingVideo.getFilePath()
                );

                return ResponseEntity.notFound().build();
            }

            // 6. Determine content type.
            String contentType = trainingVideo.getContentType();

            if (contentType == null || contentType.isBlank()) {

                try {

                    String filePath = trainingVideo.getFilePath();

                    if (filePath != null && !filePath.isBlank()) {

                        Path path = Paths.get(filePath)
                                .toAbsolutePath()
                                .normalize();

                        contentType = Files.probeContentType(path);
                    }

                } catch (Exception ex) {

                    log.warn(
                            "Could not detect MIME type. Assignment ID: {}",
                            assignmentId,
                            ex
                    );
                }
            }

            if (contentType == null || contentType.isBlank()) {

                contentType = resolveContentType(
                        trainingVideo.getOriginalFileName()
                );
            }

            MediaType mediaType;

            try {

                mediaType = MediaType.parseMediaType(contentType);

            } catch (Exception ex) {

                log.warn(
                        "Invalid MIME type: {}. Using default MIME type.",
                        contentType
                );

                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            // 7. Resolve filename.
            String fileName = trainingVideo.getOriginalFileName();

            if (fileName == null || fileName.isBlank()) {
                fileName = trainingVideo.getStoredFileName();
            }

            fileName = sanitizeFileName(fileName);

            // 8. Set inline or attachment disposition.
            ContentDisposition disposition = download
                    ? ContentDisposition.attachment()
                            .filename(fileName)
                            .build()
                    : ContentDisposition.inline()
                            .filename(fileName)
                            .build();

            // 9. Return file response.
            ResponseEntity<Resource> response = ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(resource.contentLength())
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            disposition.toString()
                    )
                    .header(
                            "X-Content-Type-Options",
                            "nosniff"
                    )
                    .body(resource);

            log.info(
                    "Resource {} successful. Assignment ID: {}, Filename: {}",
                    download ? "download" : "view",
                    assignmentId,
                    fileName
            );

            return response;

        } catch (IllegalArgumentException ex) {

            log.warn(
                    "Invalid employee resource request. Assignment ID: {}",
                    assignmentId,
                    ex
            );

            return ResponseEntity.notFound().build();

        } catch (Exception ex) {

            log.error(
                    "Resource {} failed. Assignment ID: {}",
                    download ? "download" : "view",
                    assignmentId,
                    ex
            );

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).build();
        }
    }

    /**
     * Get currently logged-in employee.
     */
    private User getLoggedInEmployee(Authentication authentication) {

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new IllegalArgumentException(
                    "Employee authentication is required."
            );
        }

        return userRepository.findByEmail(authentication.getName())
                .filter(User::isEnabled)
                .filter(user -> user.getRole() == User.Role.EMPLOYEE)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Logged-in employee not found."
                        )
                );
    }

    /**
     * Sanitize filename before adding it to HTTP headers.
     */
    private String sanitizeFileName(String fileName) {

        if (fileName == null || fileName.isBlank()) {
            return "learning-resource";
        }

        String safeName = fileName.replace("\\", "/");

        safeName = safeName.substring(
                safeName.lastIndexOf('/') + 1
        );

        safeName = safeName
                .replaceAll("[\\r\\n]", "")
                .replaceAll("[^a-zA-Z0-9._() -]", "_")
                .trim();

        return safeName.isBlank()
                ? "learning-resource"
                : safeName;
    }

    /**
     * Resolve MIME type from file extension.
     */
    private String resolveContentType(String fileName) {

        if (fileName == null || fileName.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String lower = fileName.toLowerCase();

        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mkv")) return "video/x-matroska";

        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";

        if (lower.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        }

        if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }

        if (lower.endsWith(".doc")) {
            return "application/msword";
        }

        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
