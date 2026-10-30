
package com.capacitix.controller;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.Competency;
import com.capacitix.entity.User;
import com.capacitix.repository.CompetencyRepository;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.AIQuestionGeneratorService;
import com.capacitix.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin/assessments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAssessmentController {

    private final AssessmentService assessmentService;
    private final CompetencyRepository competencyRepository;
    private final AIQuestionGeneratorService aiQuestionGeneratorService;
    private final UserRepository userRepository;


    // =========================================================
    // ASSESSMENTS
    // GET /admin/assessments
    // =========================================================

    @GetMapping
    public String assessments(Model model) {

        model.addAttribute(
                "assessments",
                assessmentService.getAllAssessments()
        );

        addAssessmentStats(model);
        addAdminUser(model);

        return "admin/assessments";
    }


    // =========================================================
    // CREATE ASSESSMENT PAGE
    // GET /admin/assessments/create
    // =========================================================

    @GetMapping("/create")
    public String createAssessmentPage(Model model) {

        model.addAttribute(
                "competencies",
                competencyRepository.findAll()
        );

        addAdminUser(model);

        return "admin/assessment-create";
    }


    // =========================================================
    // GENERATE AI ASSESSMENT
    // POST /admin/assessments/generate
    // =========================================================

    @PostMapping("/generate")
    public String generateAssessment(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String targetRole,
            @RequestParam(defaultValue = "10") int questionCount,
            @RequestParam(
                    value = "competencyIds",
                    required = false
            )
            List<Long> competencyIds,
            Model model
    ) {

        try {

            if (name == null || name.trim().isEmpty()) {

                throw new RuntimeException(
                        "Assessment name is required."
                );
            }

            name = name.trim();


            if (questionCount <= 0) {

                throw new RuntimeException(
                        "Question count must be greater than 0."
                );
            }


            if (questionCount > 50) {

                throw new RuntimeException(
                        "Question count cannot be greater than 50."
                );
            }


            if (competencyIds == null
                    || competencyIds.isEmpty()) {

                throw new RuntimeException(
                        "Please select at least one competency."
                );
            }


            List<Competency> competencies =
                    competencyRepository.findAllById(
                            competencyIds
                    );


            if (competencies == null
                    || competencies.isEmpty()) {

                throw new RuntimeException(
                        "Selected competencies were not found."
                );
            }


            if (competencies.size()
                    != competencyIds.size()) {

                throw new RuntimeException(
                        "One or more selected competencies are invalid."
                );
            }


            Assessment assessment =
                    assessmentService.createGeneratedAssessment(
                            name,
                            description,
                            "SKILL",
                            targetRole,
                            questionCount
                    );


            aiQuestionGeneratorService.generateQuestions(
                    assessment,
                    competencies
            );


            return "redirect:/admin/assessments?success=created";


        } catch (Exception e) {

            String errorMessage =
                    e.getMessage();


            if (errorMessage == null
                    || errorMessage.isBlank()) {

                errorMessage =
                        "Unable to generate assessment.";
            }


            model.addAttribute(
                    "errorMessage",
                    errorMessage
            );

            model.addAttribute(
                    "name",
                    name
            );

            model.addAttribute(
                    "description",
                    description
            );

            model.addAttribute(
                    "targetRole",
                    targetRole
            );

            model.addAttribute(
                    "questionCount",
                    questionCount
            );

            model.addAttribute(
                    "selectedCompetencyIds",
                    competencyIds
            );

            model.addAttribute(
                    "competencies",
                    competencyRepository.findAll()
            );

            addAdminUser(model);

            return "admin/assessment-create";
        }
    }


    // =========================================================
    // VIEW ASSESSMENT
    // GET /admin/assessments/{id}
    // =========================================================

    @GetMapping("/{id}")
    public String viewAssessment(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {

            Assessment assessment =
                    assessmentService.getAssessment(id);


            if (assessment == null) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "Assessment not found."
                );

                return "redirect:/admin/assessments";
            }


            // =================================================
            // LOAD ALL ENABLED EMPLOYEES
            // =================================================
            //
            // Admin can reassign an assessment to ANY enabled
            // employee, regardless of their trainer.
            //

            List<User> employees =
                    userRepository
                            .findByRoleOrderByFullNameAsc(
                                    User.Role.EMPLOYEE
                            );


            if (employees == null) {

                employees =
                        Collections.emptyList();
            }


            employees =
                    employees.stream()
                            .filter(User::isEnabled)
                            .toList();


            // =================================================
            // MODEL
            // =================================================

            model.addAttribute(
                    "assessment",
                    assessment
            );

            model.addAttribute(
                    "employees",
                    employees
            );


            addAdminUser(model);


            return "admin/assessment-details";


        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    getErrorMessage(
                            e,
                            "Unable to load assessment."
                    )
            );

            return "redirect:/admin/assessments";
        }
    }


    // =========================================================
    // REASSIGN ASSESSMENT
    //
    // POST:
    // /admin/assessments/{id}/reassign
    //
    // Admin selects another employee.
    //
    // SAME assessment is reused.
    // SAME questions are reused.
    //
    // Old assignment:
    //      CANCELLED
    //
    // New assignment:
    //      ASSIGNED
    //
    // New employee starts with:
    //      score = 0
    //      startedAt = null
    //      completedAt = null
    // =========================================================

    @PostMapping("/{id}/reassign")
    public String reassignAssessment(
            @PathVariable Long id,
            @RequestParam(
                    value = "employeeId",
                    required = false
            )
            Long employeeId,
            RedirectAttributes redirectAttributes) {

        try {

            // =================================================
            // VALIDATE EMPLOYEE
            // =================================================

            if (employeeId == null) {

                throw new RuntimeException(
                        "Please select an employee."
                );
            }


            // =================================================
            // REASSIGN
            // =================================================

            assessmentService.reassignAssessment(
                    id,
                    employeeId
            );


            // =================================================
            // SUCCESS MESSAGE
            // =================================================

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Assessment successfully reassigned."
            );


        } catch (Exception e) {

            // =================================================
            // ERROR MESSAGE
            // =================================================

            redirectAttributes.addFlashAttribute(
                    "error",
                    getErrorMessage(
                            e,
                            "Unable to reassign assessment."
                    )
            );
        }


        // =====================================================
        // RETURN TO SAME ASSESSMENT DETAILS PAGE
        // =====================================================

        return "redirect:/admin/assessments/" + id;
    }


    // =========================================================
    // ACTIVATE
    // POST /admin/assessments/{id}/activate
    // =========================================================

    @PostMapping("/{id}/activate")
    public String activateAssessment(
            @PathVariable Long id) {

        assessmentService.updateStatus(
                id,
                "ACTIVE"
        );

        return "redirect:/admin/assessments";
    }


    // =========================================================
    // COMPLETE
    // POST /admin/assessments/{id}/complete
    // =========================================================

    @PostMapping("/{id}/complete")
    public String completeAssessment(
            @PathVariable Long id) {

        assessmentService.updateStatus(
                id,
                "COMPLETED"
        );

        return "redirect:/admin/assessments";
    }


    // =========================================================
    // DELETE
    // POST /admin/assessments/{id}/delete
    // =========================================================

    @PostMapping("/{id}/delete")
    public String deleteAssessment(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {

            assessmentService.deleteAssessment(id);


            redirectAttributes.addFlashAttribute(
                    "success",
                    "Assessment deleted successfully."
            );


        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    getErrorMessage(
                            e,
                            "Unable to delete assessment."
                    )
            );
        }


        return "redirect:/admin/assessments";
    }


    // =========================================================
    // ADMIN USER
    // =========================================================

    private void addAdminUser(Model model) {

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );
    }


    // =========================================================
    // ASSESSMENT STATISTICS
    // =========================================================

    private void addAssessmentStats(Model model) {

        model.addAttribute(
                "totalAssessments",
                assessmentService.getTotalAssessments()
        );


        model.addAttribute(
                "activeAssessments",
                assessmentService.getActiveAssessments()
        );


        model.addAttribute(
                "completedAssessments",
                assessmentService.getCompletedAssessments()
        );


        model.addAttribute(
                "pendingAssessments",
                assessmentService.getPendingAssessments()
        );


        model.addAttribute(
                "generatedAssessments",
                assessmentService.getGeneratedAssessments()
        );


        model.addAttribute(
                "assignedAssessments",
                assessmentService.getAssignedAssessments()
        );
    }


    // =========================================================
    // ERROR MESSAGE
    // =========================================================

    private String getErrorMessage(
            Exception exception,
            String defaultMessage) {

        if (exception == null) {

            return defaultMessage;
        }


        String message =
                exception.getMessage();


        if (message == null
                || message.isBlank()) {

            return defaultMessage;
        }


        return message;
    }
}