package com.capacitix.controller;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.User;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.AssessmentQuestionRepository;
import com.capacitix.repository.AssessmentRepository;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/trainer/assessments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TRAINER')")
public class TrainerAssessmentController {

    private final AssessmentService assessmentService;
    private final AssessmentAssignmentRepository assignmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentRepository assessmentRepository;
    private final UserRepository userRepository;


    // =========================================================
    // ASSESSMENT CENTER
    // GET /trainer/assessments
    // =========================================================

    @GetMapping
    @Transactional(readOnly = true)
    public String assessmentCenter(Model model) {

        User trainer = getLoggedInTrainer();

        // =====================================================
        // ALL AI-GENERATED ASSESSMENTS
        // =====================================================
        /*
         * IMPORTANT:
         *
         * Every trainer must be able to see every AI-generated
         * assessment.
         *
         * Therefore we DO NOT filter by:
         *
         * employee == null
         * status == GENERATED
         *
         * An assessment can be:
         *
         * GENERATED
         * ASSIGNED
         * IN_PROGRESS
         * COMPLETED
         * etc.
         *
         * It must still remain visible in the Assessment Center.
         */

        List<Assessment> allAiAssessments =
                assessmentRepository
                        .findByAiGeneratedTrueOrderByCreatedAtDesc();

        if (allAiAssessments == null) {
            allAiAssessments = Collections.emptyList();
        }


        // =====================================================
        // QUESTION COUNTS
        // =====================================================

        Map<Long, Long> questionCounts = new HashMap<>();

        for (Assessment assessment : allAiAssessments) {

            if (assessment == null
                    || assessment.getId() == null) {
                continue;
            }

            long count =
                    questionRepository.countByAssessment(
                            assessment
                    );

            questionCounts.put(
                    assessment.getId(),
                    count
            );
        }


        // =====================================================
        // ONLY ASSESSMENTS WITH QUESTIONS
        // =====================================================

        List<Assessment> visibleAssessments =
                allAiAssessments.stream()
                        .filter(assessment ->
                                assessment != null
                                        && assessment.getId() != null
                                        && questionCounts.getOrDefault(
                                        assessment.getId(),
                                        0L
                                ) > 0
                        )
                        .toList();


        // =====================================================
        // THIS TRAINER'S ASSIGNMENTS
        // =====================================================

        List<AssessmentAssignment> trainerAssignments =
                assignmentRepository
                        .findByTrainerOrderByAssignedAtDesc(
                                trainer
                        );

        if (trainerAssignments == null) {
            trainerAssignments = Collections.emptyList();
        }


        // =====================================================
        // ASSIGNMENT STATISTICS
        // =====================================================

        long totalAssignments =
                trainerAssignments.size();

        long completedCount =
                trainerAssignments.stream()
                        .filter(this::isCompletedAssignment)
                        .count();

        long pendingCount =
                trainerAssignments.stream()
                        .filter(this::isPendingAssignment)
                        .count();

        long cancelledCount =
                trainerAssignments.stream()
                        .filter(this::isCancelledAssignment)
                        .count();

        long assignedCount =
                trainerAssignments.stream()
                        .filter(this::isActiveAssignment)
                        .count();


        // =====================================================
        // AVAILABLE FOR NEW ASSIGNMENT
        // =====================================================
        /*
         * This count is different from visibleAssessments.size().
         *
         * visibleAssessments:
         *     All AI assessments visible to every trainer.
         *
         * availableCount:
         *     Only AI assessments which this trainer can currently
         *     assign.
         */

        long availableCount =
                visibleAssessments.stream()
                        .filter(this::isTrainerAssignableAssessment)
                        .count();


        // =====================================================
        // TOTAL ASSESSMENTS
        // =====================================================
        /*
         * Every AI-generated assessment is visible in the center.
         *
         * Therefore totalAssessments represents the complete
         * AI assessment catalog, not just unassigned assessments.
         */

        long totalAssessments =
                visibleAssessments.size();


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "availableAssessments",
                visibleAssessments
        );

        // Kept for compatibility with existing HTML
        model.addAttribute(
                "assessments",
                visibleAssessments
        );

        model.addAttribute(
                "assignments",
                trainerAssignments
        );

        model.addAttribute(
                "trainerAssignments",
                trainerAssignments
        );

        model.addAttribute(
                "trainer",
                trainer
        );


        // =====================================================
        // STATISTICS
        // =====================================================

        model.addAttribute(
                "totalAssessments",
                totalAssessments
        );

        model.addAttribute(
                "availableCount",
                availableCount
        );

        model.addAttribute(
                "assignedCount",
                assignedCount
        );

        model.addAttribute(
                "completedCount",
                completedCount
        );

        model.addAttribute(
                "pendingCount",
                pendingCount
        );

        model.addAttribute(
                "cancelledCount",
                cancelledCount
        );

        model.addAttribute(
                "totalAssignments",
                totalAssignments
        );


        // =====================================================
        // QUESTION COUNTS
        // =====================================================

        model.addAttribute(
                "questionCounts",
                questionCounts
        );


        // =====================================================
        // TRAINER INFORMATION
        // =====================================================

        model.addAttribute(
                "userName",
                getUserName(trainer)
        );

        model.addAttribute(
                "userInitial",
                getUserInitial(trainer)
        );


        return "trainer/assessments";
    }


    // =========================================================
    // ASSIGNED ASSESSMENTS
    // GET /trainer/assessments/assigned
    // =========================================================

    @GetMapping("/assigned")
    @Transactional(readOnly = true)
    public String assignedAssessments(
            Model model) {

        User trainer = getLoggedInTrainer();

        List<AssessmentAssignment> assignments =
                assignmentRepository
                        .findByTrainerOrderByAssignedAtDesc(
                                trainer
                        );

        if (assignments == null) {
            assignments = Collections.emptyList();
        }


        // =====================================================
        // COUNTS
        // =====================================================

        long totalAssignments =
                assignments.size();

        long completedAssignments =
                assignments.stream()
                        .filter(this::isCompletedAssignment)
                        .count();

        long pendingAssignments =
                assignments.stream()
                        .filter(this::isPendingAssignment)
                        .count();

        long cancelledAssignments =
                assignments.stream()
                        .filter(this::isCancelledAssignment)
                        .count();

        long activeAssignments =
                assignments.stream()
                        .filter(this::isActiveAssignment)
                        .count();


        // =====================================================
        // COMPLETION PERCENTAGE
        // =====================================================

        int completionPercentage =
                totalAssignments == 0
                        ? 0
                        : (int) Math.round(
                        (completedAssignments * 100.0)
                                / totalAssignments
                );


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "assignments",
                assignments
        );

        model.addAttribute(
                "trainer",
                trainer
        );

        model.addAttribute(
                "totalAssignments",
                totalAssignments
        );

        model.addAttribute(
                "completedAssignments",
                completedAssignments
        );

        model.addAttribute(
                "pendingAssignments",
                pendingAssignments
        );

        model.addAttribute(
                "cancelledAssignments",
                cancelledAssignments
        );

        model.addAttribute(
                "activeAssignments",
                activeAssignments
        );

        model.addAttribute(
                "completionPercentage",
                completionPercentage
        );

        model.addAttribute(
                "userName",
                getUserName(trainer)
        );

        model.addAttribute(
                "userInitial",
                getUserInitial(trainer)
        );


        return "trainer/assigned-assessments";
    }


    // =========================================================
    // VIEW AI ASSESSMENT
    // GET /trainer/assessments/{assessmentId}
    // =========================================================

    @GetMapping("/{assessmentId}")
    @Transactional(readOnly = true)
    public String viewAssessment(
            @PathVariable Long assessmentId,
            Model model,
            RedirectAttributes redirectAttributes) {

        User trainer = getLoggedInTrainer();

        Assessment assessment =
                getAssessmentOrThrow(assessmentId);


        // =====================================================
        // TRAINER VISIBILITY CHECK
        // =====================================================
        /*
         * Any trainer can VIEW any AI-generated assessment.
         *
         * It does not matter whether:
         *
         * - it is generated
         * - assigned
         * - in progress
         * - completed
         */

        if (!isTrainerVisibleAssessment(assessment)) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "This assessment is not available in the Trainer Assessment Center."
            );

            return "redirect:/trainer/assessments";
        }


        // =====================================================
        // QUESTION COUNT
        // =====================================================

        long questionCount =
                questionRepository.countByAssessment(
                        assessment
                );

        if (questionCount <= 0) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "This assessment has no generated questions."
            );

            return "redirect:/trainer/assessments";
        }


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "assessment",
                assessment
        );

        model.addAttribute(
                "questionCount",
                questionCount
        );

        model.addAttribute(
                "trainer",
                trainer
        );

        model.addAttribute(
                "userName",
                getUserName(trainer)
        );

        model.addAttribute(
                "userInitial",
                getUserInitial(trainer)
        );


        return "trainer/assessment-view";
    }


    // =========================================================
    // ASSIGNMENT PAGE
    // GET /trainer/assessments/{assessmentId}/assign
    // =========================================================

    @GetMapping("/{assessmentId}/assign")
    @Transactional(readOnly = true)
    public String showAssignPage(
            @PathVariable Long assessmentId,
            Model model,
            RedirectAttributes redirectAttributes) {

        User trainer = getLoggedInTrainer();

        Assessment assessment =
                getAssessmentOrThrow(assessmentId);


        // =====================================================
        // ASSIGNMENT PERMISSION
        // =====================================================
        /*
         * Trainer can assign only an AI-generated assessment
         * which has NOT already been assigned.
         *
         * Admin reassignment is handled separately by Admin.
         */

        if (!isTrainerAssignableAssessment(assessment)) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Only unassigned AI-generated assessments can be assigned by a trainer."
            );

            return "redirect:/trainer/assessments";
        }


        // =====================================================
        // QUESTION CHECK
        // =====================================================

        long questionCount =
                questionRepository.countByAssessment(
                        assessment
                );

        if (questionCount <= 0) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "This assessment has no generated questions."
            );

            return "redirect:/trainer/assessments";
        }


        // =====================================================
        // THIS TRAINER'S EMPLOYEES ONLY
        // =====================================================

        List<User> employees =
                userRepository
                        .findByTrainerAndRoleOrderByFullNameAsc(
                                trainer,
                                User.Role.EMPLOYEE
                        );

        if (employees == null) {
            employees = Collections.emptyList();
        }


        // =====================================================
        // MODEL
        // =====================================================

        model.addAttribute(
                "assessment",
                assessment
        );

        model.addAttribute(
                "employees",
                employees
        );

        model.addAttribute(
                "trainer",
                trainer
        );

        model.addAttribute(
                "questionCount",
                questionCount
        );

        model.addAttribute(
                "userName",
                getUserName(trainer)
        );

        model.addAttribute(
                "userInitial",
                getUserInitial(trainer)
        );


        return "trainer/assign-assessment";
    }


    // =========================================================
    // ASSIGN ASSESSMENT
    // POST /trainer/assessments/{assessmentId}/assign
    // =========================================================

    @PostMapping("/{assessmentId}/assign")
    @Transactional
    public String assignAssessment(
            @PathVariable Long assessmentId,
            @RequestParam(
                    value = "employeeId",
                    required = false
            ) Long employeeId,
            RedirectAttributes redirectAttributes) {

        User trainer = getLoggedInTrainer();

        try {

            // =================================================
            // EMPLOYEE ID
            // =================================================

            if (employeeId == null) {

                throw new RuntimeException(
                        "Please select an employee."
                );
            }


            // =================================================
            // ASSESSMENT
            // =================================================

            Assessment assessment =
                    getAssessmentOrThrow(assessmentId);


            // =================================================
            // TRAINER ASSIGNMENT PERMISSION
            // =================================================

            if (!isTrainerAssignableAssessment(assessment)) {

                throw new RuntimeException(
                        "This assessment is already assigned or is not available for trainer assignment."
                );
            }


            // =================================================
            // QUESTION CHECK
            // =================================================

            long questionCount =
                    questionRepository.countByAssessment(
                            assessment
                    );

            if (questionCount <= 0) {

                throw new RuntimeException(
                        "This assessment has no generated questions."
                );
            }


            // =================================================
            // EMPLOYEE
            // =================================================

            User employee =
                    userRepository
                            .findById(employeeId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Employee not found."
                                    )
                            );


            // =================================================
            // ROLE CHECK
            // =================================================

            if (employee.getRole()
                    != User.Role.EMPLOYEE) {

                throw new RuntimeException(
                        "Selected user is not an employee."
                );
            }


            // =================================================
            // ENABLED CHECK
            // =================================================

            if (!employee.isEnabled()) {

                throw new RuntimeException(
                        "Selected employee account is disabled."
                );
            }


            // =================================================
            // EMPLOYEE → TRAINER CHECK
            // =================================================

            User employeeTrainer =
                    employee.getTrainer();

            if (employeeTrainer == null) {

                throw new RuntimeException(
                        "This employee is not assigned to any trainer."
                );
            }


            if (trainer.getId() == null
                    || employeeTrainer.getId() == null
                    || !trainer.getId().equals(
                    employeeTrainer.getId()
            )) {

                throw new RuntimeException(
                        "You can only assign assessments to your own employees."
                );
            }


            // =================================================
            // DUPLICATE CHECK
            // =================================================

            if (assignmentRepository
                    .existsByAssessmentAndEmployee(
                            assessment,
                            employee
                    )) {

                throw new RuntimeException(
                        "This assessment is already assigned to this employee."
                );
            }


            // =================================================
            // ASSIGN
            // =================================================

            assessmentService.assignAssessment(
                    assessmentId,
                    employeeId
            );


            // =================================================
            // SUCCESS
            // =================================================

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Assessment successfully assigned to "
                            + getUserName(employee)
                            + "."
            );

            return "redirect:/trainer/assessments/assigned";


        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    getErrorMessage(
                            e,
                            "Unable to assign assessment."
                    )
            );

            return "redirect:/trainer/assessments/"
                    + assessmentId
                    + "/assign";
        }
    }


    // =========================================================
    // VIEW SINGLE ASSIGNMENT
    // GET /trainer/assessments/assigned/{assignmentId}
    // =========================================================

    @GetMapping("/assigned/{assignmentId}")
    @Transactional(readOnly = true)
    public String viewAssignment(
            @PathVariable Long assignmentId,
            Model model) {

        User trainer = getLoggedInTrainer();

        AssessmentAssignment assignment =
                assignmentRepository
                        .findById(assignmentId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Assessment assignment not found."
                                )
                        );


        verifyAssignmentBelongsToTrainer(
                assignment,
                trainer
        );


        model.addAttribute(
                "assignment",
                assignment
        );

        model.addAttribute(
                "trainer",
                trainer
        );

        model.addAttribute(
                "userName",
                getUserName(trainer)
        );

        model.addAttribute(
                "userInitial",
                getUserInitial(trainer)
        );


        return "trainer/assignment-view";
    }


    // =========================================================
    // CANCEL ASSIGNMENT
    // POST /trainer/assessments/assigned/{assignmentId}/cancel
    // =========================================================

    @PostMapping("/assigned/{assignmentId}/cancel")
    @Transactional
    public String cancelAssignment(
            @PathVariable Long assignmentId,
            RedirectAttributes redirectAttributes) {

        User trainer = getLoggedInTrainer();

        try {

            AssessmentAssignment assignment =
                    assignmentRepository
                            .findById(assignmentId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Assessment assignment not found."
                                    )
                            );


            // =================================================
            // SECURITY CHECK
            // =================================================

            verifyAssignmentBelongsToTrainer(
                    assignment,
                    trainer
            );


            // =================================================
            // COMPLETED CHECK
            // =================================================

            if (assignment.isCompleted()) {

                throw new RuntimeException(
                        "Completed assessment cannot be cancelled."
                );
            }


            // =================================================
            // ALREADY CANCELLED
            // =================================================

            if ("CANCELLED".equalsIgnoreCase(
                    assignment.getStatus()
            )) {

                throw new RuntimeException(
                        "This assessment is already cancelled."
                );
            }


            // =================================================
            // CANCEL
            // =================================================

            assignment.setStatus("CANCELLED");

            assignmentRepository.save(
                    assignment
            );


            redirectAttributes.addFlashAttribute(
                    "success",
                    "Assessment assignment cancelled successfully."
            );


        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    getErrorMessage(
                            e,
                            "Unable to cancel assessment."
                    )
            );
        }


        return "redirect:/trainer/assessments/assigned";
    }


    // =========================================================
    // CHECK TRAINER VISIBLE ASSESSMENT
    // =========================================================
    /*
     * Every trainer can VIEW every AI-generated assessment.
     *
     * Assignment status does NOT matter here.
     */

    private boolean isTrainerVisibleAssessment(
            Assessment assessment) {

        if (assessment == null) {
            return false;
        }

        if (assessment.getId() == null) {
            return false;
        }

        return assessment.isAiGenerated();
    }


    // =========================================================
    // CHECK TRAINER ASSIGNABLE ASSESSMENT
    // =========================================================
    /*
     * A trainer can CREATE an assignment only when:
     *
     * 1. Assessment is AI generated
     * 2. Assessment has no employee
     * 3. Assessment status is GENERATED
     *
     * Once assigned, only Admin can reassign it.
     */

    private boolean isTrainerAssignableAssessment(
            Assessment assessment) {

        if (!isTrainerVisibleAssessment(assessment)) {
            return false;
        }

        if (assessment.getEmployee() != null) {
            return false;
        }

        String status =
                assessment.getStatus();

        return status != null
                && "GENERATED".equalsIgnoreCase(
                status.trim()
        );
    }


    // =========================================================
    // VERIFY ASSIGNMENT OWNER
    // =========================================================

    private void verifyAssignmentBelongsToTrainer(
            AssessmentAssignment assignment,
            User trainer) {

        if (assignment == null) {

            throw new RuntimeException(
                    "Assessment assignment not found."
            );
        }


        if (assignment.getTrainer() == null) {

            throw new RuntimeException(
                    "This assessment has no assigned trainer."
            );
        }


        if (trainer == null
                || trainer.getId() == null
                || assignment.getTrainer().getId() == null
                || !trainer.getId().equals(
                assignment.getTrainer().getId()
        )) {

            throw new RuntimeException(
                    "You are not authorized to access this assignment."
            );
        }
    }


    // =========================================================
    // GET ASSESSMENT
    // =========================================================

    private Assessment getAssessmentOrThrow(
            Long assessmentId) {

        if (assessmentId == null) {

            throw new RuntimeException(
                    "Assessment ID is required."
            );
        }


        return assessmentRepository
                .findById(assessmentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Assessment not found."
                        )
                );
    }


    // =========================================================
    // GET LOGGED-IN TRAINER
    // =========================================================

    private User getLoggedInTrainer() {

        var authentication =
                org.springframework.security.core.context
                        .SecurityContextHolder
                        .getContext()
                        .getAuthentication();


        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "Trainer is not authenticated."
            );
        }


        String email =
                authentication.getName();


        if (email == null
                || email.isBlank()) {

            throw new RuntimeException(
                    "Trainer email not found."
            );
        }


        User trainer =
                userRepository
                        .findByEmail(email.trim())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Logged-in trainer not found."
                                )
                        );


        if (trainer.getRole()
                != User.Role.TRAINER) {

            throw new RuntimeException(
                    "Access denied: User is not a trainer."
            );
        }


        if (!trainer.isEnabled()) {

            throw new RuntimeException(
                    "Trainer account is disabled."
            );
        }


        return trainer;
    }


    // =========================================================
    // COMPLETED ASSIGNMENT
    // =========================================================

    private boolean isCompletedAssignment(
            AssessmentAssignment assignment) {

        return assignment != null
                && assignment.isCompleted()
                && !"CANCELLED".equalsIgnoreCase(
                assignment.getStatus()
        );
    }


    // =========================================================
    // CANCELLED ASSIGNMENT
    // =========================================================

    private boolean isCancelledAssignment(
            AssessmentAssignment assignment) {

        return assignment != null
                && "CANCELLED".equalsIgnoreCase(
                assignment.getStatus()
        );
    }


    // =========================================================
    // ACTIVE ASSIGNMENT
    // =========================================================

    private boolean isActiveAssignment(
            AssessmentAssignment assignment) {

        return assignment != null
                && !isCancelledAssignment(assignment);
    }


    // =========================================================
    // PENDING ASSIGNMENT
    // =========================================================

    private boolean isPendingAssignment(
            AssessmentAssignment assignment) {

        return assignment != null
                && !assignment.isCompleted()
                && !isCancelledAssignment(assignment);
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


    // =========================================================
    // USER NAME
    // =========================================================

    private String getUserName(User user) {

        if (user == null
                || user.getFullName() == null
                || user.getFullName().isBlank()) {

            return "Trainer";
        }


        return user.getFullName().trim();
    }


    // =========================================================
    // USER INITIAL
    // =========================================================

    private String getUserInitial(User user) {

        String name =
                getUserName(user);


        if (name.isBlank()) {
            return "T";
        }


        return name
                .substring(0, 1)
                .toUpperCase();
    }
}