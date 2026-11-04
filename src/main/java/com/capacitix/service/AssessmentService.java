
package com.capacitix.service;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.User;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.AssessmentRepository;
import com.capacitix.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final int MAX_QUESTION_COUNT = 100;

    private final AssessmentRepository assessmentRepository;
    private final AssessmentAssignmentRepository assessmentAssignmentRepository;
    private final UserRepository userRepository;

    // =========================================================
    // GET ALL ASSESSMENTS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Assessment> getAllAssessments() {

        return assessmentRepository
                .findAllByOrderByCreatedAtDesc();
    }

    // =========================================================
    // GET ASSESSMENT BY ID
    // =========================================================

    @Transactional(readOnly = true)
    public Assessment getAssessment(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Assessment ID is required."
            );
        }

        return assessmentRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Assessment not found with ID: " + id
                        )
                );
    }

    // =========================================================
    // CREATE AI GENERATED ASSESSMENT
    // =========================================================

    @Transactional
    public Assessment createGeneratedAssessment(
            String name,
            String description,
            String type,
            String targetRole,
            int questionCount
    ) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Assessment name is required."
            );
        }

        if (questionCount <= 0
                || questionCount > MAX_QUESTION_COUNT) {

            throw new IllegalArgumentException(
                    "Question count must be between 1 and "
                            + MAX_QUESTION_COUNT + "."
            );
        }

        String assessmentType =
                type == null || type.isBlank()
                        ? "SKILL"
                        : type.trim().toUpperCase();

        validateAssessmentType(assessmentType);

        Assessment assessment =
                Assessment.builder()
                        .name(name.trim())
                        .description(
                                description == null
                                        || description.isBlank()
                                        ? null
                                        : description.trim()
                        )
                        .type(assessmentType)
                        .status("GENERATED")
                        .aiGenerated(true)
                        .employee(null)
                        .targetRole(
                                targetRole == null
                                        || targetRole.isBlank()
                                        ? null
                                        : targetRole.trim()
                        )
                        .questionCount(questionCount)
                        .score(0)
                        .maxScore(questionCount)
                        .createdAt(LocalDateTime.now())
                        .build();

        return assessmentRepository.save(assessment);
    }

    // =========================================================
    // CREATE MANUAL ASSESSMENT FOR EMPLOYEE
    // =========================================================

    @Transactional
    public Assessment createAssessment(
            String name,
            String description,
            String type,
            Long employeeId
    ) {

        if (name == null || name.isBlank()) {
            throw new RuntimeException(
                    "Assessment name is required."
            );
        }

        if (employeeId == null) {
            throw new RuntimeException(
                    "Employee ID is required."
            );
        }

        User employee =
                userRepository.findById(employeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );

        validateEmployee(employee);

        String assessmentType =
                type == null || type.isBlank()
                        ? "SKILL"
                        : type.trim().toUpperCase();

        validateAssessmentType(assessmentType);

        User trainer = getEmployeeTrainer(employee);

        LocalDateTime now = LocalDateTime.now();

        Assessment assessment =
                Assessment.builder()
                        .name(name.trim())
                        .description(
                                description == null
                                        || description.isBlank()
                                        ? null
                                        : description.trim()
                        )
                        .type(assessmentType)
                        .status("ASSIGNED")
                        .aiGenerated(false)
                        .employee(employee)
                        .score(0)
                        .maxScore(100)
                        .createdAt(now)
                        .assignedAt(now)
                        .build();

        Assessment savedAssessment =
                assessmentRepository.save(assessment);

        createAssignment(
                savedAssessment,
                employee,
                trainer,
                now
        );

        return savedAssessment;
    }

    // =========================================================
    // ASSIGN AI ASSESSMENT TO EMPLOYEE
    // =========================================================

    @Transactional
    public Assessment assignAssessment(
            Long assessmentId,
            Long employeeId
    ) {

        Assessment assessment = getAssessment(assessmentId);

        if (!assessment.isAiGenerated()) {
            throw new RuntimeException(
                    "Only AI-generated assessments can be assigned through this flow."
            );
        }

        if (employeeId == null) {
            throw new RuntimeException(
                    "Employee ID is required."
            );
        }

        User employee =
                userRepository.findById(employeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );

        validateEmployee(employee);

        User trainer = getEmployeeTrainer(employee);

        if (assessment.getEmployee() != null) {

            Long existingEmployeeId =
                    assessment.getEmployee().getId();

            if (existingEmployeeId != null
                    && existingEmployeeId.equals(employeeId)) {

                throw new RuntimeException(
                        "This assessment is already assigned to this employee."
                );
            }

            throw new RuntimeException(
                    "This assessment is already assigned to another employee. "
                            + "Use Admin Reassign Assessment to change the employee."
            );
        }

        if (assessmentAssignmentRepository
                .existsByAssessmentAndEmployee(
                        assessment,
                        employee
                )) {

            throw new RuntimeException(
                    "This assessment already has an assignment for this employee."
            );
        }

        Integer questionCount = assessment.getQuestionCount();

        if (questionCount == null || questionCount <= 0) {
            throw new RuntimeException(
                    "This AI assessment has no generated questions. "
                            + "Please generate the assessment again."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        assessment.setEmployee(employee);
        assessment.setStatus("ASSIGNED");
        assessment.setAssignedAt(now);
        assessment.setStartedAt(null);
        assessment.setCompletedAt(null);
        assessment.setScore(0);

        if (assessment.getMaxScore() == null
                || assessment.getMaxScore() <= 0) {

            assessment.setMaxScore(questionCount);
        }

        Assessment savedAssessment =
                assessmentRepository.save(assessment);

        AssessmentAssignment assignment =
                createAssignment(
                        savedAssessment,
                        employee,
                        trainer,
                        now
                );

        if (assignment.getId() == null) {
            throw new RuntimeException(
                    "Assessment assignment could not be created."
            );
        }

        return savedAssessment;
    }

    // =========================================================
    // CREATE ASSESSMENT ASSIGNMENT
    // =========================================================

    private AssessmentAssignment createAssignment(
            Assessment assessment,
            User employee,
            User trainer,
            LocalDateTime assignedAt
    ) {

        if (assessment == null) {
            throw new RuntimeException(
                    "Assessment is required."
            );
        }

        if (employee == null) {
            throw new RuntimeException(
                    "Employee is required."
            );
        }

        if (trainer == null) {
            throw new RuntimeException(
                    "Trainer is required."
            );
        }

        AssessmentAssignment assignment =
                AssessmentAssignment.builder()
                        .assessment(assessment)
                        .employee(employee)
                        .trainer(trainer)
                        .status("ASSIGNED")
                        .assignedAt(
                                assignedAt != null
                                        ? assignedAt
                                        : LocalDateTime.now()
                        )
                        .startedAt(null)
                        .completedAt(null)
                        .score(0)
                        .maxScore(
                                assessment.getMaxScore() != null
                                        ? assessment.getMaxScore()
                                        : 0
                        )
                        .build();

        return assessmentAssignmentRepository.save(assignment);
    }

    // =========================================================
    // ADMIN REASSIGN SAME ASSESSMENT
    // =========================================================

    @Transactional
    public void reassignAssessment(
            Long assessmentId,
            Long newEmployeeId
    ) {

        if (assessmentId == null) {
            throw new RuntimeException(
                    "Assessment ID is required."
            );
        }

        if (newEmployeeId == null) {
            throw new RuntimeException(
                    "Employee ID is required."
            );
        }

        Assessment assessment =
                assessmentRepository.findById(assessmentId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Assessment not found."
                                )
                        );

        if (!assessment.isAiGenerated()) {
            throw new RuntimeException(
                    "Only AI-generated assessments can be reassigned."
            );
        }

        Integer questionCount = assessment.getQuestionCount();

        if (questionCount == null || questionCount <= 0) {
            throw new RuntimeException(
                    "This assessment has no generated questions."
            );
        }

        User newEmployee =
                userRepository.findById(newEmployeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );

        validateEmployee(newEmployee);

        User newTrainer = getEmployeeTrainer(newEmployee);

        if (assessment.getEmployee() != null
                && assessment.getEmployee().getId() != null
                && assessment.getEmployee().getId()
                .equals(newEmployeeId)) {

            throw new RuntimeException(
                    "This assessment is already assigned to this employee."
            );
        }

        if (assessmentAssignmentRepository
                .existsByAssessmentAndEmployee(
                        assessment,
                        newEmployee
                )) {

            throw new RuntimeException(
                    "This employee already has an assignment for this assessment."
            );
        }

        User oldEmployee = assessment.getEmployee();

        AssessmentAssignment oldAssignment = null;

        if (oldEmployee != null) {

            oldAssignment =
                    assessmentAssignmentRepository
                            .findByAssessmentAndEmployee(
                                    assessment,
                                    oldEmployee
                            )
                            .orElse(null);
        }

        if (oldAssignment != null) {

            oldAssignment.setStatus("CANCELLED");

            assessmentAssignmentRepository.save(
                    oldAssignment
            );
        }

        LocalDateTime now = LocalDateTime.now();

        assessment.setEmployee(newEmployee);
        assessment.setStatus("ASSIGNED");
        assessment.setAssignedAt(now);
        assessment.setStartedAt(null);
        assessment.setCompletedAt(null);
        assessment.setScore(0);

        if (assessment.getMaxScore() == null
                || assessment.getMaxScore() <= 0) {

            assessment.setMaxScore(questionCount);
        }

        Assessment savedAssessment =
                assessmentRepository.save(assessment);

        AssessmentAssignment newAssignment =
                createAssignment(
                        savedAssessment,
                        newEmployee,
                        newTrainer,
                        now
                );

        if (newAssignment == null
                || newAssignment.getId() == null) {

            throw new RuntimeException(
                    "Unable to create the new assessment assignment."
            );
        }
    }

    // =========================================================
    // START ASSESSMENT
    // =========================================================

    @Transactional
    public Assessment startAssessment(Long id) {

        Assessment assessment = getAssessment(id);

        if (assessment.getEmployee() == null) {
            throw new RuntimeException(
                    "Assessment is not assigned to an employee."
            );
        }

        if ("COMPLETED".equalsIgnoreCase(
                assessment.getStatus()
        )) {

            throw new RuntimeException(
                    "Assessment is already completed."
            );
        }

        assessment.setStatus("IN_PROGRESS");

        if (assessment.getStartedAt() == null) {
            assessment.setStartedAt(LocalDateTime.now());
        }

        return assessmentRepository.save(assessment);
    }

    // =========================================================
    // UPDATE STATUS
    // =========================================================

    @Transactional
    public Assessment updateStatus(
            Long id,
            String status
    ) {

        Assessment assessment = getAssessment(id);

        String newStatus =
                status == null
                        ? ""
                        : status.trim().toUpperCase();

        validateStatus(newStatus);

        assessment.setStatus(newStatus);

        if ("IN_PROGRESS".equals(newStatus)
                && assessment.getStartedAt() == null) {

            assessment.setStartedAt(LocalDateTime.now());
        }

        if ("COMPLETED".equals(newStatus)) {

            if (assessment.getCompletedAt() == null) {
                assessment.setCompletedAt(LocalDateTime.now());
            }

        } else {

            assessment.setCompletedAt(null);
        }

        return assessmentRepository.save(assessment);
    }

    // =========================================================
    // GET EMPLOYEE ASSESSMENTS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Assessment> getEmployeeAssessments(
            Long employeeId
    ) {

        if (employeeId == null) {
            throw new RuntimeException(
                    "Employee ID is required."
            );
        }

        return assessmentRepository
                .findByEmployee_IdOrderByCreatedAtDesc(
                        employeeId
                );
    }

    // =========================================================
    // GET GENERATED UNASSIGNED ASSESSMENTS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Assessment> getGeneratedUnassignedAssessments() {

        return assessmentRepository
                .findByAiGeneratedTrueAndStatusAndEmployeeIsNullOrderByCreatedAtDesc(
                        "GENERATED"
                );
    }

    // =========================================================
    // GET ASSESSMENTS BY TARGET ROLE
    // =========================================================

    @Transactional(readOnly = true)
    public List<Assessment> getAssessmentsByRole(
            String targetRole
    ) {

        return assessmentRepository
                .findByTargetRoleOrderByCreatedAtDesc(
                        targetRole
                );
    }

    // =========================================================
    // STATISTICS
    // =========================================================

    public long getTotalAssessments() {
        return assessmentRepository.count();
    }

    public long getActiveAssessments() {
        return assessmentRepository.countByStatus("ACTIVE");
    }

    public long getPendingAssessments() {
        return assessmentRepository.countByStatus("PENDING");
    }

    public long getCompletedAssessments() {
        return assessmentRepository.countByStatus("COMPLETED");
    }

    public long getGeneratedAssessments() {
        return assessmentRepository.countByStatus("GENERATED");
    }

    public long getAssignedAssessments() {
        return assessmentRepository.countByStatus("ASSIGNED");
    }

    // =========================================================
    // DELETE ASSESSMENT
    // =========================================================

    @Transactional
    public void deleteAssessment(Long id) {

        Assessment assessment = getAssessment(id);

        List<AssessmentAssignment> assignments =
                assessmentAssignmentRepository
                        .findByAssessmentOrderByAssignedAtDesc(
                                assessment
                        );

        if (assignments != null && !assignments.isEmpty()) {

            assessmentAssignmentRepository.deleteAll(
                    assignments
            );
        }

        assessmentRepository.delete(assessment);
    }

    // =========================================================
    // VALIDATE EMPLOYEE
    // =========================================================

    private void validateEmployee(User employee) {

        if (employee == null) {
            throw new RuntimeException(
                    "Employee is required."
            );
        }

        if (employee.getRole() != User.Role.EMPLOYEE) {
            throw new RuntimeException(
                    "Selected user is not an employee."
            );
        }

        if (!employee.isEnabled()) {
            throw new RuntimeException(
                    "Employee account is disabled."
            );
        }
    }

    // =========================================================
    // GET EMPLOYEE TRAINER
    // =========================================================

    private User getEmployeeTrainer(User employee) {

        User trainer = employee.getTrainer();

        if (trainer == null) {
            throw new RuntimeException(
                    "This employee does not have an assigned trainer. "
                            + "Please assign a trainer to the employee first."
            );
        }

        if (trainer.getRole() != User.Role.TRAINER) {
            throw new RuntimeException(
                    "The employee's assigned user is not a trainer."
            );
        }

        if (!trainer.isEnabled()) {
            throw new RuntimeException(
                    "The employee's assigned trainer is disabled."
            );
        }

        return trainer;
    }

    // =========================================================
    // VALIDATE ASSESSMENT TYPE
    // =========================================================

    private void validateAssessmentType(String type) {

        if (!type.equals("SKILL")
                && !type.equals("TECHNICAL")
                && !type.equals("PRACTICAL")) {

            throw new RuntimeException(
                    "Invalid assessment type."
            );
        }
    }

    // =========================================================
    // VALIDATE STATUS
    // =========================================================

    private void validateStatus(String status) {

        if (!status.equals("GENERATED")
                && !status.equals("ASSIGNED")
                && !status.equals("IN_PROGRESS")
                && !status.equals("ACTIVE")
                && !status.equals("PENDING")
                && !status.equals("COMPLETED")) {

            throw new RuntimeException(
                    "Invalid assessment status."
            );
        }
    }
}
