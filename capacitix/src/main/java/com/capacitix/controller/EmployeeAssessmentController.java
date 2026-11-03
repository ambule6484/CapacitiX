
package com.capacitix.controller;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.AssessmentResponse;
import com.capacitix.entity.Competency;
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
    @Transactional(readOnly = true)
    public String assessments(Model model) {

        User employee = getLoggedInEmployee();

        List<AssessmentAssignment> assignments =
                assignmentRepository.findByEmployeeOrderByAssignedAtDesc(
                        employee
                );

        // Initialize assessment information before Thymeleaf renders.
        for (AssessmentAssignment assignment : assignments) {

            if (assignment == null) {
                continue;
            }

            Assessment assessment = assignment.getAssessment();

            if (assessment != null) {
                assessment.getId();
                assessment.getName();
                assessment.getDescription();
                assessment.getType();
                assessment.getStatus();
                assessment.getTargetRole();
                assessment.getQuestionCount();
                assessment.getAiGenerated();
            }
        }

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

        model.addAttribute("assignments", assignments);
        model.addAttribute("employee", employee);

        model.addAttribute("totalAssessments", totalAssessments);
        model.addAttribute("completedAssessments", completedAssessments);
        model.addAttribute("pendingAssessments", pendingAssessments);

        model.addAttribute("userName", getUserName(employee));
        model.addAttribute("userInitial", getUserInitial(employee));

        return "employee/assessments";
    }


    // =========================================================
    // START / CONTINUE ASSESSMENT
    // =========================================================

    @GetMapping("/{assignmentId}/start")
    @Transactional
    public String startAssessment(
            @PathVariable Long assignmentId,
            Model model
    ) {

        User employee = getLoggedInEmployee();

        AssessmentAssignment assignment =
                getEmployeeAssignment(assignmentId, employee);

        Assessment assessment = assignment.getAssessment();

        if (assessment == null) {
            throw new IllegalStateException(
                    "No assessment is linked with this assignment."
            );
        }

        // Initialize assessment properties.
        initializeAssessment(assessment);


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
            throw new IllegalStateException(
                    "This assessment assignment has been cancelled."
            );
        }


        // =====================================================
        // LOAD ACTIVE QUESTIONS
        // =====================================================

        List<AssessmentQuestion> questions =
                questionRepository
                        .findByAssessmentAndActiveTrueOrderByQuestionOrderAsc(
                                assessment
                        );

        if (questions == null || questions.isEmpty()) {
            throw new IllegalStateException(
                    "No active questions are available for this assessment."
            );
        }

        // Initialize lazy competency associations.
        initializeQuestions(questions);


        // =====================================================
        // START TIME
        // =====================================================

        if (assignment.getStartedAt() == null) {

            assignment.setStartedAt(LocalDateTime.now());
            assignment.setStatus("IN_PROGRESS");

            assignmentRepository.save(assignment);

        } else if (!assignment.isInProgress()) {

            assignment.setStatus("IN_PROGRESS");

            assignmentRepository.save(assignment);
        }


        // =====================================================
        // TIME LIMIT
        // =====================================================

        int questionCount = questions.size();

        // One question = one minute.
        long totalTimeSeconds = questionCount * 60L;


        // =====================================================
        // REMAINING TIME
        // =====================================================

        LocalDateTime startedAt = assignment.getStartedAt();

        long elapsedSeconds =
                Duration.between(
                        startedAt,
                        LocalDateTime.now()
                ).getSeconds();

        long remainingTimeSeconds =
                Math.max(0, totalTimeSeconds - elapsedSeconds);


        // =====================================================
        // TIME EXPIRED
        // =====================================================

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

        model.addAttribute("timeLimitSeconds", totalTimeSeconds);
        model.addAttribute("remainingTimeSeconds", remainingTimeSeconds);
        model.addAttribute("questionCount", questionCount);


        // =====================================================
        // MODEL DATA
        // =====================================================

        model.addAttribute("assignment", assignment);
        model.addAttribute("assessment", assessment);
        model.addAttribute("questions", questions);
        model.addAttribute("employee", employee);

        model.addAttribute("userName", getUserName(employee));
        model.addAttribute("userInitial", getUserInitial(employee));

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
                getEmployeeAssignment(assignmentId, employee);

        Assessment assessment = assignment.getAssessment();

        if (assessment == null) {
            throw new IllegalStateException(
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
            throw new IllegalStateException(
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
            throw new IllegalStateException(
                    "No active questions were found for this assessment."
            );
        }

        initializeQuestions(questions);


        // =====================================================
        // SERVER-SIDE TIME VALIDATION
        // =====================================================

        if (assignment.getStartedAt() == null) {
            throw new IllegalStateException(
                    "Assessment has not been started."
            );
        }

        long totalTimeSeconds = questions.size() * 60L;

        long elapsedSeconds =
                Duration.between(
                        assignment.getStartedAt(),
                        LocalDateTime.now()
                ).getSeconds();

        boolean serverTimeExpired =
                elapsedSeconds >= totalTimeSeconds;

        boolean finalTimeExpired =
                timeExpired || serverTimeExpired;


        // =====================================================
        // MANUAL SUBMISSION VALIDATION
        // =====================================================

        if (!finalTimeExpired) {

            for (AssessmentQuestion question : questions) {

                String answer =
                        answers.get("question_" + question.getId());

                if (answer == null || answer.trim().isEmpty()) {
                    throw new IllegalStateException(
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

            String answer =
                    answers.get("question_" + question.getId());

            if (answer == null) {
                answer = "";
            }

            answer = answer.trim();


            AssessmentResponse response =
                    responseRepository
                            .findByAssignmentAndQuestion(
                                    assignment,
                                    question
                            )
                            .orElseGet(() ->
                                    AssessmentResponse.builder()
                                            .assignment(assignment)
                                            .question(question)
                                            .build()
                            );


            response.setSelectedAnswer(answer);
            response.setAnsweredAt(LocalDateTime.now());

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


        // =====================================================
        // MAXIMUM MARKS FALLBACK
        // =====================================================

        if (maximumMarks <= 0) {
            maximumMarks = questions.size();
        }


        // =====================================================
        // UPDATE ASSIGNMENT
        // =====================================================

        assignment.setScore(obtainedMarks);
        assignment.setMaxScore(maximumMarks);
        assignment.setStatus("COMPLETED");
        assignment.setCompletedAt(LocalDateTime.now());

        assignmentRepository.save(assignment);


        // =====================================================
        // UPDATE ORIGINAL ASSESSMENT
        // =====================================================

        assessment.setScore(obtainedMarks);
        assessment.setMaxScore(maximumMarks);
        assessment.setStatus("COMPLETED");
        assessment.setCompletedAt(LocalDateTime.now());

        assessmentRepository.save(assessment);


        return "redirect:/employee/assessments/"
                + assignmentId
                + "/result";
    }


    // =========================================================
    // AUTO COMPLETE TIMED-OUT ASSESSMENT
    // =========================================================

    private void completeTimedOutAssessment(
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

            AssessmentResponse response =
                    responseRepository
                            .findByAssignmentAndQuestion(
                                    assignment,
                                    question
                            )
                            .orElseGet(() ->
                                    AssessmentResponse.builder()
                                            .assignment(assignment)
                                            .question(question)
                                            .build()
                            );


            // Preserve an existing answer.
            String answer =
                    response.getSelectedAnswer() != null
                            ? response.getSelectedAnswer().trim()
                            : "";

            response.setSelectedAnswer(answer);

            if (response.getAnsweredAt() == null) {
                response.setAnsweredAt(LocalDateTime.now());
            }

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
            maximumMarks = questions.size();
        }


        assignment.setScore(obtainedMarks);
        assignment.setMaxScore(maximumMarks);
        assignment.setStatus("COMPLETED");
        assignment.setCompletedAt(LocalDateTime.now());

        assignmentRepository.save(assignment);


        assessment.setScore(obtainedMarks);
        assessment.setMaxScore(maximumMarks);
        assessment.setStatus("COMPLETED");
        assessment.setCompletedAt(LocalDateTime.now());

        assessmentRepository.save(assessment);
    }


    // =========================================================
    // VIEW RESULT
    // =========================================================

    @GetMapping("/{assignmentId}/result")
    @Transactional(readOnly = true)
    public String result(
            @PathVariable Long assignmentId,
            Model model
    ) {

        User employee = getLoggedInEmployee();

        AssessmentAssignment assignment =
                getEmployeeAssignment(assignmentId, employee);

        if (!assignment.isCompleted()) {
            return "redirect:/employee/assessments/"
                    + assignmentId
                    + "/start";
        }

        Assessment assessment = assignment.getAssessment();

        if (assessment != null) {
            initializeAssessment(assessment);
        }


        // =====================================================
        // SKILL ANALYSIS
        // =====================================================

        SkillAnalysisService.SkillAnalysisResult analysis =
                skillAnalysisService.analyzeAssignment(assignment);


        // =====================================================
        // MODEL DATA
        // =====================================================

        model.addAttribute("assignment", assignment);
        model.addAttribute("analysis", analysis);

        model.addAttribute("skills", analysis.skills());
        model.addAttribute("skillGaps", analysis.skillGaps());
        model.addAttribute("recommendations", analysis.recommendations());

        model.addAttribute("employee", employee);

        model.addAttribute("userName", getUserName(employee));
        model.addAttribute("userInitial", getUserInitial(employee));

        return "employee/assessment-result";
    }


    // =========================================================
    // REVIEW COMPLETED ASSESSMENT
    // =========================================================

    @GetMapping("/{assignmentId}/review")
    @Transactional(readOnly = true)
    public String reviewAssessment(
            @PathVariable Long assignmentId,
            Model model
    ) {

        User employee = getLoggedInEmployee();

        AssessmentAssignment assignment =
                getEmployeeAssignment(assignmentId, employee);

        Assessment assessment = assignment.getAssessment();

        if (assessment != null) {
            initializeAssessment(assessment);
        }


        List<AssessmentResponse> responses =
                responseRepository
                        .findByAssignmentOrderByQuestion_QuestionOrderAsc(
                                assignment
                        );


        // Initialize question and competency data used by the review page.
        for (AssessmentResponse response : responses) {

            if (response == null) {
                continue;
            }

            AssessmentQuestion question = response.getQuestion();

            if (question != null) {

                question.getId();
                question.getQuestionText();
                question.getQuestionType();
                question.getOptionA();
                question.getOptionB();
                question.getOptionC();
                question.getOptionD();
                question.getCorrectAnswer();
                question.getExplanation();
                question.getDifficulty();
                question.getMarks();
                question.getQuestionOrder();

                Competency competency = question.getCompetency();

                if (competency != null) {
                    competency.getId();
                    competency.getName();
                }
            }

            response.getSelectedAnswer();
            response.getMarksAwarded();
            response.getMaxMarks();
            response.getAnsweredAt();
            response.getCorrect();
        }


        model.addAttribute("assignment", assignment);
        model.addAttribute("responses", responses);
        model.addAttribute("employee", employee);

        model.addAttribute("userName", getUserName(employee));
        model.addAttribute("userInitial", getUserInitial(employee));

        return "employee/assessment-review";
    }


    // =========================================================
    // INITIALIZE ASSESSMENT
    // =========================================================

    private void initializeAssessment(Assessment assessment) {

        if (assessment == null) {
            return;
        }

        assessment.getId();
        assessment.getName();
        assessment.getDescription();
        assessment.getType();
        assessment.getStatus();
        assessment.getAiGenerated();
        assessment.getTargetRole();
        assessment.getQuestionCount();
        assessment.getScore();
        assessment.getMaxScore();
        assessment.getCreatedAt();
        assessment.getAssignedAt();
        assessment.getStartedAt();
        assessment.getCompletedAt();
    }


    // =========================================================
    // INITIALIZE QUESTIONS
    // =========================================================

    private void initializeQuestions(
            List<AssessmentQuestion> questions
    ) {

        if (questions == null) {
            return;
        }

        for (AssessmentQuestion question : questions) {

            if (question == null) {
                continue;
            }

            question.getId();
            question.getQuestionText();
            question.getQuestionType();
            question.getOptionA();
            question.getOptionB();
            question.getOptionC();
            question.getOptionD();
            question.getDifficulty();
            question.getMarks();
            question.getQuestionOrder();
            question.getActive();

            Competency competency = question.getCompetency();

            if (competency != null) {
                competency.getId();
                competency.getName();
            }
        }
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
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {

            throw new IllegalStateException(
                    "Employee is not authenticated."
            );
        }

        String email = authentication.getName();

        if (email == null || email.isBlank()) {
            throw new IllegalStateException(
                    "Employee email not found."
            );
        }

        User employee =
                userRepository.findByEmail(email.trim())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Logged-in employee not found."
                                )
                        );

        if (employee.getRole() != User.Role.EMPLOYEE) {
            throw new IllegalStateException(
                    "The logged-in user is not an employee."
            );
        }

        if (!employee.isEnabled()) {
            throw new IllegalStateException(
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
            throw new IllegalArgumentException(
                    "Invalid assessment assignment ID."
            );
        }

        AssessmentAssignment assignment =
                assignmentRepository.findById(assignmentId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Assessment assignment not found."
                                )
                        );

        User assignedEmployee = assignment.getEmployee();

        if (assignedEmployee == null) {
            throw new IllegalStateException(
                    "This assessment has no employee assigned."
            );
        }

        if (employee.getId() == null
                || assignedEmployee.getId() == null
                || !employee.getId().equals(assignedEmployee.getId())) {

            throw new IllegalStateException(
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

        String name = getUserName(user);

        if (name.isBlank()) {
            return "E";
        }

        return name.substring(0, 1).toUpperCase();
    }
}
