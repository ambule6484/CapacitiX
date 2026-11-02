package com.capacitix.controller;

import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.LearningPathPdfService;
import com.capacitix.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/employee")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class LearningPathController {

    private final UserRepository userRepository;

    private final LearningPathService learningPathService;

    private final LearningPathPdfService learningPathPdfService;

    // =========================================================
    // LEARNING PATH PAGE
    // =========================================================

    /**
     * Shows the personalized AI learning path.
     *
     * Flow:
     *
     * Employee
     *     ↓
     * Completed Assessment
     *     ↓
     * Skill Gap Analysis
     *     ↓
     * AI Learning Path
     *     ↓
     * Learning Path Items
     *     ↓
     * Training Modules
     */
    @GetMapping("/learning-path")
    public String learningPath(
            Authentication authentication,
            Model model
    ) {

        User employee = getAuthenticatedEmployee(authentication);

        model.addAttribute(
                "employee",
                employee
        );

        LearningPathResponse learningPath =
                learningPathService.generateLearningPath(employee);

        model.addAttribute(
                "learningPath",
                learningPath
        );

        model.addAttribute(
                "userName",
                employee.getFullName()
        );

        String initial =
                employee.getFullName() != null
                        && !employee.getFullName().isBlank()
                        ? employee.getFullName()
                                .substring(0, 1)
                                .toUpperCase()
                        : "E";

        model.addAttribute(
                "userInitial",
                initial
        );

        return "employee/learning-path";
    }

    // =========================================================
    // DOWNLOAD LEARNING PATH PDF
    // =========================================================

    /**
     * Generates and downloads the employee's personalized
     * AI learning path as a PDF.
     *
     * URL:
     *
     * GET /employee/learning-path/download
     */
    @GetMapping("/learning-path/download")
    public ResponseEntity<ByteArrayResource>
    downloadLearningPathPdf(
            Authentication authentication
    ) {

        // =====================================================
        // GET LOGGED-IN EMPLOYEE
        // =====================================================

        User employee =
                getAuthenticatedEmployee(
                        authentication
                );

        // =====================================================
        // GENERATE / LOAD LEARNING PATH
        // =====================================================

        LearningPathResponse learningPath =
                learningPathService
                        .generateLearningPath(
                                employee
                        );

        // =====================================================
        // VALIDATE LEARNING PATH
        // =====================================================

        if (learningPath == null) {

            throw new IllegalStateException(
                    "Learning path could not be generated."
            );
        }

        // =====================================================
        // GENERATE PDF
        // =====================================================

        byte[] pdfBytes =
                learningPathPdfService
                        .generateLearningPathPdf(
                                employee,
                                learningPath
                        );

        // =====================================================
        // VALIDATE PDF
        // =====================================================

        if (pdfBytes == null
                || pdfBytes.length == 0) {

            throw new IllegalStateException(
                    "Generated learning path PDF is empty."
            );
        }

        // =====================================================
        // PDF RESOURCE
        // =====================================================

        ByteArrayResource resource =
                new ByteArrayResource(
                        pdfBytes
                );

        // =====================================================
        // SAFE EMPLOYEE FILE NAME
        // =====================================================

        String employeeName =
                employee.getFullName() != null
                        && !employee.getFullName().isBlank()
                        ? employee.getFullName()
                                .replaceAll(
                                        "[^a-zA-Z0-9]+",
                                        "_"
                                )
                        : "Employee";

        // =====================================================
        // FILE NAME
        // =====================================================

        String fileName =
                "Capacitix_Learning_Path_"
                        + employeeName
                        + ".pdf";

        // =====================================================
        // RESPONSE
        // =====================================================

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.APPLICATION_PDF
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + fileName
                                + "\""
                )
                .contentLength(
                        pdfBytes.length
                )
                .body(
                        resource
                );
    }

    // =========================================================
    // GET AUTHENTICATED EMPLOYEE
    // =========================================================

    /**
     * Retrieves the employee associated with the currently
     * authenticated account.
     */
    private User getAuthenticatedEmployee(
            Authentication authentication
    ) {

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new IllegalStateException(
                    "Authenticated employee was not found."
            );
        }

        String email =
                authentication.getName();

        return userRepository
                .findByEmail(email)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Logged-in employee not found."
                        )
                );
    }
}