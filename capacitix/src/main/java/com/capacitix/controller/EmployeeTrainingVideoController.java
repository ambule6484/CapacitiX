
package com.capacitix.controller;

import com.capacitix.entity.EmployeeVideoAssignment;
import com.capacitix.entity.TrainingVideo;
import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.EmployeeVideoAssignmentService;
import com.capacitix.service.TrainingVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Employee controller for viewing trainer-assigned learning resources.
 *
 * IMPORTANT:
 * Employees must NOT use TrainerVideoController.
 *
 * Every resource request is checked against EmployeeVideoAssignment
 * to make sure the logged-in employee actually owns the assignment.
 */
@Controller
@RequestMapping("/employee/training")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeTrainingVideoController {

    private final EmployeeVideoAssignmentService employeeVideoAssignmentService;
    private final TrainingVideoService trainingVideoService;
    private final UserRepository userRepository;

    /**
     * View an assigned learning resource.
     *
     * Supported resources:
     * - Video
     * - Image
     * - PDF
     * - PowerPoint
     * - Word
     *
     * URL:
     * /employee/training/resource/{assignmentId}
     */
    @GetMapping("/resource/{assignmentId}")
    public ResponseEntity<Resource> viewResource(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {

            User employee = getLoggedInEmployee(authentication);

            /*
             * SECURITY CHECK:
             *
             * This method only returns an assignment belonging
             * to the currently logged-in employee.
             */
            EmployeeVideoAssignment assignment =
                    employeeVideoAssignmentService
                            .getEmployeeAssignment(assignmentId, employee);

            if (assignment == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            TrainingVideo trainingVideo = assignment.getVideo();

            if (trainingVideo == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            /*
             * Do not allow inactive/deleted resources to be opened.
             */
            if (!trainingVideo.isActive()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            /*
             * Load the actual uploaded file.
             *
             * TrainingVideoService already validates that the stored
             * path belongs to the learning-resources upload directory.
             */
            Resource resource =
                    trainingVideoService.loadResourceAsResource(trainingVideo);

            if (resource == null
                    || !resource.exists()
                    || !resource.isReadable()) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            /*
             * Determine content type.
             */
            String contentType = trainingVideo.getContentType();

            if (contentType == null || contentType.isBlank()) {

                try {
                    Path path = Paths.get(
                            trainingVideo.getFilePath()
                    ).toAbsolutePath().normalize();

                    contentType = Files.probeContentType(path);

                } catch (Exception ignored) {
                    // Fallback below.
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
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            /*
             * Browser should try to display supported resources inline.
             *
             * Videos -> browser video player
             * Images -> browser image viewer
             * PDFs -> browser PDF viewer
             * PPT/Word -> browser/application handling or download
             */
            String fileName = trainingVideo.getOriginalFileName();

            if (fileName == null || fileName.isBlank()) {
                fileName = trainingVideo.getStoredFileName();
            }

            ContentDisposition disposition =
                    ContentDisposition.inline()
                            .filename(fileName)
                            .build();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(resource.contentLength())
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            disposition.toString()
                    )
                    .header(
                            HttpHeaders.ACCEPT_RANGES,
                            "bytes"
                    )
                    .body(resource);

        } catch (IllegalArgumentException ex) {

            /*
             * Do not expose internal service errors to the employee.
             */
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        } catch (Exception ex) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Optional download endpoint.
     *
     * This is useful for PPT, PPTX, DOC, DOCX and any file that
     * the browser cannot display directly.
     *
     * URL:
     * /employee/training/resource/{assignmentId}/download
     */
    @GetMapping("/resource/{assignmentId}/download")
    public ResponseEntity<Resource> downloadResource(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {

            User employee = getLoggedInEmployee(authentication);

            EmployeeVideoAssignment assignment =
                    employeeVideoAssignmentService
                            .getEmployeeAssignment(assignmentId, employee);

            if (assignment == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            TrainingVideo trainingVideo = assignment.getVideo();

            if (trainingVideo == null || !trainingVideo.isActive()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Resource resource =
                    trainingVideoService.loadResourceAsResource(trainingVideo);

            if (resource == null
                    || !resource.exists()
                    || !resource.isReadable()) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            String contentType = trainingVideo.getContentType();

            if (contentType == null || contentType.isBlank()) {
                contentType = resolveContentType(
                        trainingVideo.getOriginalFileName()
                );
            }

            MediaType mediaType;

            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception ex) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String fileName = trainingVideo.getOriginalFileName();

            if (fileName == null || fileName.isBlank()) {
                fileName = trainingVideo.getStoredFileName();
            }

            ContentDisposition disposition =
                    ContentDisposition.attachment()
                            .filename(fileName)
                            .build();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(resource.contentLength())
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            disposition.toString()
                    )
                    .body(resource);

        } catch (IllegalArgumentException ex) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        } catch (Exception ex) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
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

        return userRepository
                .findByEmail(authentication.getName())
                .filter(User::isEnabled)
                .filter(user -> user.getRole() == User.Role.EMPLOYEE)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Logged-in employee not found."
                        )
                );
    }

    /**
     * Resolve MIME type when the database does not contain one.
     */
    private String resolveContentType(String fileName) {

        if (fileName == null || fileName.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String lower = fileName.toLowerCase();

        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }

        if (lower.endsWith(".webm")) {
            return "video/webm";
        }

        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }

        if (lower.endsWith(".avi")) {
            return "video/x-msvideo";
        }

        if (lower.endsWith(".mkv")) {
            return "video/x-matroska";
        }

        if (lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        if (lower.endsWith(".png")) {
            return "image/png";
        }

        if (lower.endsWith(".gif")) {
            return "image/gif";
        }

        if (lower.endsWith(".webp")) {
            return "image/webp";
        }

        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }

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