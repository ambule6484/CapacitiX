package com.capacitix.controller;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.AssessmentResponse;
import com.capacitix.entity.User;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.AssessmentQuestionRepository;
import com.capacitix.repository.AssessmentRepository;
import com.capacitix.repository.AssessmentResponseRepository;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.SkillAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/employee/assessments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeAssessmentController {

    private final AssessmentAssignmentRepository assignmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentResponseRepository responseRepository;
    private final AssessmentRepository assessmentRepository;
    private final UserRepository userRepository;
    private final SkillAnalysisService skillAnalysisService;


    // =========================================================
    // EMPLOYEE ASSESSMENT LIST
    // =========================================================

    @GetMapping
    public String assessments(Model model) {

        User employee = getLoggedInEmployee();

        List<AssessmentAssignment> assignments =
                assignmentRepository.findByEmployeeOrderByAssignedAtDesc(
                        employee
                );

        long totalAssessments = assignments.size();

        long completedAssessments =
                assignments.stream()
                        .filter(AssessmentAssignment::isCompleted)
                        .count();

        long pendingAssessments =
                assignments.stream()
                        .filter(assignment ->
                                assignment != null
                                        && !assignment.isCompleted()
                                        && !assignment.isCancelled()
                        )
                        .count();

        String userName = getUserName(employee);
        String userInitial = getUserInitial(employee);

        model.addAttribute("assignments", assignments);
        model.addAttribute("employee", employee);

        model.addAttribute(
                "totalAssessments",
                totalAssessments
        );

        model.addAttribute(
                "completedAssessments",
                completedAssessments
        );

        model.addAttribute(
                "pendingAssessments",
                pendingAssessments
        );

        model.addAttribute(
                "userName",
                userName
        );

        model.addAttribute(
                "userInitial",
                userInitial
        );

        return "employee/assessments";
    }


    // =========================================================
    // START / CONTINUE ASSESSMENT
    // =========================================================

    @GetMapping("/{assignmentId}/start")
    public String startAssessment(
            @PathVariable Long assignmentId,
            Model model
    ) {

        User employee = getLoggedInEmployee();

        AssessmentAssignment assignment =
                getEmployeeAssignment(
                        assignmentId,
                        employee
                );

        Assessment assessment = assignment.getAssessment();

        if (assessment == null) {
            throw new RuntimeException(
                    "No assessment is linked with this assignment."
            );
        }


        // =====================================================
        // COMPLETED
        // =====================================================

        if (assignment.isCompleted()) {

            return "redirect:/employee/assessments/"
                    + assignmentId
                    + "/result";
        }


        // =====================================================
        // CANCELLED
        // =====================================================

        if (assignment.isCancelled()) {

            throw new RuntimeException(
                    "This assessment assignment has been cancelled."
            );
        }


        // =====================================================
        // LOAD QUESTIONS
        // =====================================================

        List<AssessmentQuestion> questions =
                questionRepository
                        .findByAssessmentAndActiveTrueOrderByQuestionOrderAsc(
                                assessment
                        );

        if (questions == null || questions.isEmpty()) {

            throw new RuntimeException(
                    "No active questions are available for this assessment."
            );
        }


        // =====================================================
        // START TIME
        // =====================================================
        //
        // startedAt is written ONLY the first time.
        //
        // Therefore:
        //
        // First visit:
        //     startedAt = NOW
        //
        // Refresh:
        //     startedAt remains unchanged
        //
        // Continue:
        //     same timer continues
        //

        if (assignment.getStartedAt() == null) {

            assignment.setStartedAt(
                    LocalDateTime.now()
            );

            assignment.setStatus(
                    "IN_PROGRESS"
            );

            assignmentRepository.save(assignment);
        }
        else if (!assignment.isInProgress()) {

            assignment.setStatus(
                    "IN_PROGRESS"
            );

            assignmentRepository.save(assignment);
        }


        // =====================================================
        // TIME LIMIT
        // =====================================================
        //
        // CURRENT RULE:
        //
        // 1 question = 1 minute
        //
        // 5 questions  = 5 minutes
        // 10 questions = 10 minutes
        // 20 questions = 20 minutes
        //

        int questionCount = questions.size();

        long totalTimeSeconds =
                questionCount * 60L;


        // =====================================================
        // SERVER-SIDE REMAINING TIME
        // =====================================================

        LocalDateTime startedAt =
                assignment.getStartedAt();

        long elapsedSeconds =
                Duration.between(
                        startedAt,
                        LocalDateTime.now()
                ).getSeconds();

        long remainingTimeSeconds =
                Math.max(
                        0,
                        totalTimeSeconds - elapsedSeconds
                );


        // =====================================================
        // TIME ALREADY EXPIRED
        // =====================================================
        //
        // If employee opens/refreshes after the time has expired,
        // do NOT allow them to continue.
        //

        if (remainingTimeSeconds <= 0) {

            completeTimedOutAssessment(
                    assignment,
                    assessment,
                    questions
            );

            return "redirect:/employee/assessments/"
                    + assignmentId
                    + "/result";
        }


        // =====================================================
        // TIMER DATA
        // =====================================================

        model.addAttribute(
                "timeLimitSeconds",
                totalTimeSeconds
        );

        model.addAttribute(
                "remainingTimeSeconds",
                remainingTimeSeconds
        );

        model.addAttribute(
                "questionCount",
                questionCount
        );


        // =====================================================
        // MODEL DATA
        // =====================================================

        model.addAttribute(
                "assignment",
                assignment
        );

        model.addAttribute(
                "assessment",
                assessment
        );

        model.addAttribute(
                "questions",
                questions
        );

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "userName",
                getUserName(employee)
        );

        model.addAttribute(
                "userInitial",
                getUserInitial(employee)
        );


        return "employee/take-assessment";
    }


    // =========================================================
    // SUBMIT ASSESSMENT
    // =========================================================

    @PostMapping("/{assignmentId}/submit")
    @Transactional
    public String submitAssessment(
            @PathVariable Long assignmentId,
            @RequestParam Map<String, String> answers,
            @RequestParam(
                    name = "timeExpired",
                    required = false,
                    defaultValue = "false"
            ) boolean timeExpired
    ) {

        User employee = getLoggedInEmployee();

        AssessmentAssignment assignment =
                getEmployeeAssignment(
                        assignmentId,
                        employee
                );

        Assessment assessment = assignment.getAssessment();

        if (assessment == null) {

            throw new RuntimeException(
                    "No assessment is linked with this assignment."
            );
        }


        // =====================================================
        // ALREADY COMPLETED
        // =====================================================

        if (assignment.isCompleted()) {

            return "redirect:/employee/assessments/"
                    + assignmentId
                    + "/result";
        }


        // =====================================================
        // CANCELLED
        // =====================================================

        if (assignment.isCancelled()) {

            throw new RuntimeException(
                    "This assessment assignment has been cancelled."
            );
        }


        // =====================================================
        // QUESTIONS
        // =====================================================

        List<AssessmentQuestion> questions =
                questionRepository
                        .findByAssessmentAndActiveTrueOrderByQuestionOrderAsc(
                                assessment
                        );

        if (questions == null || questions.isEmpty()) {

            throw new RuntimeException(
                    "No active questions were found for this assessment."
            );
        }


        // =====================================================
        // SERVER-SIDE TIME VALIDATION
        // =====================================================
        //
        // NEVER trust the browser timer.
        //
        // Even if the employee changes JavaScript,
        // the server calculates the actual elapsed time.
        //

        if (assignment.getStartedAt() == null) {

            throw new RuntimeException(
                    "Assessment has not been started."
            );
        }

        int questionCount = questions.size();

        long totalTimeSeconds =
                questionCount * 60L;

        long elapsedSeconds =
                Duration.between(
                        assignment.getStartedAt(),
                        LocalDateTime.now()
                ).getSeconds();


        boolean serverTimeExpired =
                elapsedSeconds >= totalTimeSeconds;


        /*
         * If either:
         *
         * 1. Browser says time expired
         * OR
         * 2. Server says time expired
         *
         * then the submission is treated as a timed submission.
         */
        boolean finalTimeExpired =
                timeExpired || serverTimeExpired;


        // =====================================================
        // MANUAL SUBMISSION VALIDATION
        // =====================================================
        //
        // If employee manually submits BEFORE time expires,
        // every question must be answered.
        //
        // If timer expires:
        // unanswered questions are automatically treated as
        // unanswered and receive zero marks.
        //

        if (!finalTimeExpired) {

            for (AssessmentQuestion question : questions) {

                if (question == null
                        || question.getId() == null) {

                    continue;
                }

                String answer =
                        answers.get(
                                "question_" + question.getId()
                        );

                if (answer == null
                        || answer.trim().isEmpty()) {

                    throw new RuntimeException(
                            "Please answer all questions before submitting."
                    );
                }
            }
        }


        // =====================================================
        // PROCESS ANSWERS
        // =====================================================

        int obtainedMarks = 0;
        int maximumMarks = 0;

        for (AssessmentQuestion question : questions) {

            if (question == null
                    || question.getId() == null) {

                continue;
            }

            String answer =
                    answers.get(
                            "question_" + question.getId()
                    );

            if (answer == null) {
                answer = "";
            }

            answer = answer.trim();


            // ================================================
            // FIND EXISTING RESPONSE
            // ================================================

            AssessmentResponse response =
                    responseRepository
                            .findByAssignmentAndQuestion(
                                    assignment,
                                    question
                            )
                            .orElseGet(() ->
                                    AssessmentResponse
                                            .builder()
                                            .assignment(assignment)
                                            .question(question)
                                            .build()
                            );


            // ================================================
            // SAVE ANSWER
            // ================================================

            response.setSelectedAnswer(answer);

            response.setAnsweredAt(
                    LocalDateTime.now()
            );

            response.evaluateAnswer();

            responseRepository.save(response);


            // ================================================
            // MARKS
            // ================================================

            int awarded =
                    response.getMarksAwarded() != null
                            ? response.getMarksAwarded()
                            : 0;

            int max =
                    response.getMaxMarks() != null
                            ? response.getMaxMarks()
                            : 0;

            obtainedMarks += awarded;
            maximumMarks += max;
        }


        // =====================================================
        // SAFETY FALLBACK
        // =====================================================

        if (maximumMarks <= 0) {

            maximumMarks =
                    questions.size();
        }


        // =====================================================
        // UPDATE ASSIGNMENT
        // =====================================================

        assignment.setScore(
                obtainedMarks
        );

        assignment.setMaxScore(
                maximumMarks
        );

        assignment.setStatus(
                "COMPLETED"
        );

        assignment.setCompletedAt(
                LocalDateTime.now()
        );

        assignmentRepository.save(assignment);


        // =====================================================
        // UPDATE ORIGINAL ASSESSMENT
        // =====================================================

        assessment.setScore(
                obtainedMarks
        );

        assessment.setMaxScore(
                maximumMarks
        );

        assessment.setStatus(
                "COMPLETED"
        );

        assessment.setCompletedAt(
                LocalDateTime.now()
        );

        assessmentRepository.save(assessment);


        // =====================================================
        // RESULT
        // =====================================================

        return "redirect:/employee/assessments/"
                + assignmentId
                + "/result";
    }


    // =========================================================
    // AUTO COMPLETE TIMED-OUT ASSESSMENT
    // =========================================================
    //
    // Used when employee opens/refeshes the page after the
    // allowed time has already expired.
    //

    @Transactional
    protected void completeTimedOutAssessment(
            AssessmentAssignment assignment,
            Assessment assessment,
            List<AssessmentQuestion> questions
    ) {

        if (assignment.isCompleted()) {
            return;
        }


        int obtainedMarks = 0;
        int maximumMarks = 0;


        for (AssessmentQuestion question : questions) {

            if (question == null
                    || question.getId() == null) {

                continue;
            }


            String answer = "";


            AssessmentResponse response =
                    responseRepository
                            .findByAssignmentAndQuestion(
                                    assignment,
                                    question
                            )
                            .orElseGet(() ->
                                    AssessmentResponse
                                            .builder()
                                            .assignment(assignment)
                                            .question(question)
                                            .build()
                            );


            /*
             * Existing answer is preserved.
             *
             * If the employee answered a question before time
             * expired, that answer is evaluated.
             *
             * If there is no answer, it receives zero.
             */

            if (response.getSelectedAnswer() != null) {

                answer =
                        response.getSelectedAnswer().trim();
            }

            response.setSelectedAnswer(answer);

            response.setAnsweredAt(
                    response.getAnsweredAt() != null
                            ? response.getAnsweredAt()
                            : LocalDateTime.now()
            );

            response.evaluateAnswer();

            responseRepository.save(response);


            int awarded =
                    response.getMarksAwarded() != null
                            ? response.getMarksAwarded()
                            : 0;

            int max =
                    response.getMaxMarks() != null
                            ? response.getMaxMarks()
                            : 0;

            obtainedMarks += awarded;
            maximumMarks += max;
        }


        if (maximumMarks <= 0) {

            maximumMarks =
                    questions.size();
        }


        assignment.setScore(
                obtainedMarks
        );

        assignment.setMaxScore(
                maximumMarks
        );

        assignment.setStatus(
                "COMPLETED"
        );

        assignment.setCompletedAt(
                LocalDateTime.now()
        );

        assignmentRepository.save(assignment);


        assessment.setScore(
                obtainedMarks
        );

        assessment.setMaxScore(
                maximumMarks
        );

        assessment.setStatus(
                "COMPLETED"
        );

        assessment.setCompletedAt(
                LocalDateTime.now()
        );

        assessmentRepository.save(assessment);
    }


    // =========================================================
    // VIEW RESULT
    // =========================================================

    @GetMapping("/{assignmentId}/result")
    public String result(
            @PathVariable Long assignmentId,
            Model model
    ) {

        User employee = getLoggedInEmployee();

        AssessmentAssignment assignment =
                getEmployeeAssignment(
                        assignmentId,
                        employee
                );

        if (!assignment.isCompleted()) {

            return "redirect:/employee/assessments/"
                    + assignmentId
                    + "/start";
        }

        SkillAnalysisService.SkillAnalysisResult analysis =
                skillAnalysisService.analyzeAssignment(
                        assignment
                );

        model.addAttribute(
                "assignment",
                assignment
        );

        model.addAttribute(
                "analysis",
                analysis
        );

        model.addAttribute(
                "skills",
                analysis.skills()
        );

        model.addAttribute(
                "skillGaps",
                analysis.skillGaps()
        );

        model.addAttribute(
                "recommendations",
                analysis.recommendations()
        );

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "userName",
                getUserName(employee)
        );

        model.addAttribute(
                "userInitial",
                getUserInitial(employee)
        );

        return "employee/assessment-result";
    }


    // =========================================================
    // REVIEW COMPLETED ASSESSMENT
    // =========================================================

    @GetMapping("/{assignmentId}/review")
    public String reviewAssessment(
            @PathVariable Long assignmentId,
            Model model
    ) {

        User employee = getLoggedInEmployee();

        AssessmentAssignment assignment =
                getEmployeeAssignment(
                        assignmentId,
                        employee
                );

        List<AssessmentResponse> responses =
                responseRepository
                        .findByAssignmentOrderByQuestion_QuestionOrderAsc(
                                assignment
                        );

        model.addAttribute(
                "assignment",
                assignment
        );

        model.addAttribute(
                "responses",
                responses
        );

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "userName",
                getUserName(employee)
        );

        model.addAttribute(
                "userInitial",
                getUserInitial(employee)
        );

        return "employee/assessment-review";
    }


    // =========================================================
    // GET LOGGED-IN EMPLOYEE
    // =========================================================

    private User getLoggedInEmployee() {

        var authentication =
                org.springframework.security.core.context
                        .SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "Employee is not authenticated."
            );
        }

        String email =
                authentication.getName();

        if (email == null
                || email.isBlank()) {

            throw new RuntimeException(
                    "Employee email not found."
            );
        }

        User employee =
                userRepository
                        .findByEmail(email.trim())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Logged-in employee not found."
                                )
                        );

        if (employee.getRole()
                != User.Role.EMPLOYEE) {

            throw new RuntimeException(
                    "The logged-in user is not an employee."
            );
        }

        if (!employee.isEnabled()) {

            throw new RuntimeException(
                    "Employee account is disabled."
            );
        }

        return employee;
    }


    // =========================================================
    // GET EMPLOYEE ASSIGNMENT
    // =========================================================

    private AssessmentAssignment getEmployeeAssignment(
            Long assignmentId,
            User employee
    ) {

        if (assignmentId == null) {

            throw new RuntimeException(
                    "Invalid assessment assignment ID."
            );
        }

        AssessmentAssignment assignment =
                assignmentRepository
                        .findById(assignmentId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Assessment assignment not found."
                                )
                        );

        User assignedEmployee =
                assignment.getEmployee();

        if (assignedEmployee == null) {

            throw new RuntimeException(
                    "This assessment has no employee assigned."
            );
        }

        if (employee.getId() == null
                || assignedEmployee.getId() == null
                || !employee.getId()
                .equals(assignedEmployee.getId())) {

            throw new RuntimeException(
                    "You are not authorized to access this assessment."
            );
        }

        return assignment;
    }


    // =========================================================
    // USER NAME
    // =========================================================

    private String getUserName(User user) {

        if (user == null
                || user.getFullName() == null
                || user.getFullName().isBlank()) {

            return "Employee";
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
            return "E";
        }

        return name
                .substring(0, 1)
                .toUpperCase();
    }
}