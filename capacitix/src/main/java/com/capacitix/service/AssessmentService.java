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

            throw new RuntimeException(
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

        if (name == null || name.trim().isEmpty()) {

            throw new RuntimeException(
                    "Assessment name is required."
            );
        }

        if (questionCount <= 0) {

            throw new RuntimeException(
                    "Question count must be greater than 0."
            );
        }

        String assessmentType =
                type == null || type.trim().isEmpty()
                        ? "SKILL"
                        : type.trim().toUpperCase();

        validateAssessmentType(assessmentType);

        Assessment assessment =
                Assessment.builder()
                        .name(name.trim())

                        .description(
                                description == null
                                        || description.trim().isEmpty()
                                        ? null
                                        : description.trim()
                        )

                        .type(assessmentType)

                        /*
                         * AI assessment is generated and ready
                         * for trainer/admin assignment.
                         */
                        .status("GENERATED")

                        .aiGenerated(true)

                        /*
                         * No employee initially.
                         */
                        .employee(null)

                        .targetRole(
                                targetRole == null
                                        || targetRole.trim().isEmpty()
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

        if (name == null || name.trim().isEmpty()) {

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
                userRepository
                        .findById(employeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );

        validateEmployee(employee);

        String assessmentType =
                type == null || type.trim().isEmpty()
                        ? "SKILL"
                        : type.trim().toUpperCase();

        validateAssessmentType(assessmentType);

        User trainer =
                getEmployeeTrainer(employee);

        LocalDateTime now =
                LocalDateTime.now();

        Assessment assessment =
                Assessment.builder()
                        .name(name.trim())

                        .description(
                                description == null
                                        || description.trim().isEmpty()
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

    /*
     * Used for the FIRST assignment of an AI-generated
     * assessment.
     *
     * Trainer can assign only an unassigned/generated
     * assessment.
     *
     * Admin reassignment uses reassignAssessment().
     */

    @Transactional
    public Assessment assignAssessment(
            Long assessmentId,
            Long employeeId
    ) {

        Assessment assessment =
                getAssessment(assessmentId);

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
                userRepository
                        .findById(employeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );

        validateEmployee(employee);

        User trainer =
                getEmployeeTrainer(employee);

        /*
         * First assignment must be to an unassigned
         * assessment.
         */
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
                    "This assessment is already assigned to another employee. " +
                    "Use Admin Reassign Assessment to change the employee."
            );
        }

        /*
         * Safety check for duplicate assignment history.
         */
        if (assessmentAssignmentRepository
                .existsByAssessmentAndEmployee(
                        assessment,
                        employee
                )) {

            throw new RuntimeException(
                    "This assessment already has an assignment for this employee."
            );
        }

        Integer questionCount =
                assessment.getQuestionCount();

        if (questionCount == null
                || questionCount <= 0) {

            throw new RuntimeException(
                    "This AI assessment has no generated questions. " +
                    "Please generate the assessment again."
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

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

        return assessmentAssignmentRepository.save(
                assignment
        );
    }


    // =========================================================
    // ADMIN REASSIGN SAME ASSESSMENT
    // =========================================================

    /*
     * IMPORTANT:
     *
     * This does NOT create a new Assessment.
     *
     * SAME:
     *   Assessment ID
     *   Questions
     *   Assessment name
     *   Description
     *   Competencies
     *
     * OLD EMPLOYEE:
     *   AssessmentAssignment -> CANCELLED
     *   Old score/history preserved
     *
     * NEW EMPLOYEE:
     *   AssessmentAssignment -> ASSIGNED
     *   score = 0
     *   startedAt = null
     *   completedAt = null
     *
     * Assessment master record is reset for the new employee.
     */

    @Transactional
    public void reassignAssessment(
            Long assessmentId,
            Long newEmployeeId
    ) {

        // =====================================================
        // VALIDATE IDS
        // =====================================================

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


        // =====================================================
        // LOAD SAME ASSESSMENT
        // =====================================================

        Assessment assessment =
                assessmentRepository
                        .findById(assessmentId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Assessment not found."
                                )
                        );


        // =====================================================
        // AI ASSESSMENT CHECK
        // =====================================================

        if (!assessment.isAiGenerated()) {

            throw new RuntimeException(
                    "Only AI-generated assessments can be reassigned."
            );
        }


        // =====================================================
        // QUESTION CHECK
        // =====================================================

        Integer questionCount =
                assessment.getQuestionCount();

        if (questionCount == null
                || questionCount <= 0) {

            throw new RuntimeException(
                    "This assessment has no generated questions."
            );
        }


        // =====================================================
        // LOAD NEW EMPLOYEE
        // =====================================================

        User newEmployee =
                userRepository
                        .findById(newEmployeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );


        // =====================================================
        // VALIDATE NEW EMPLOYEE
        // =====================================================

        validateEmployee(newEmployee);


        // =====================================================
        // GET NEW EMPLOYEE TRAINER
        // =====================================================

        User newTrainer =
                getEmployeeTrainer(newEmployee);


        // =====================================================
        // SAME EMPLOYEE CHECK
        // =====================================================

        if (assessment.getEmployee() != null
                && assessment.getEmployee().getId() != null
                && assessment.getEmployee().getId()
                .equals(newEmployeeId)) {

            throw new RuntimeException(
                    "This assessment is already assigned to this employee."
            );
        }


        // =====================================================
        // CHECK NEW EMPLOYEE HISTORY
        // =====================================================

        /*
         * Because assessment_assignments has:
         *
         * UNIQUE(assessment_id, employee_id)
         *
         * we cannot create another row if this employee
         * already has an assignment for this assessment.
         *
         * We therefore reject it instead of destroying
         * previous assignment history.
         */

        if (assessmentAssignmentRepository
                .existsByAssessmentAndEmployee(
                        assessment,
                        newEmployee
                )) {

            throw new RuntimeException(
                    "This employee already has an assignment for this assessment."
            );
        }


        // =====================================================
        // FIND OLD EMPLOYEE
        // =====================================================

        User oldEmployee =
                assessment.getEmployee();


        // =====================================================
        // FIND OLD ASSIGNMENT
        // =====================================================

        AssessmentAssignment oldAssignment =
                null;

        if (oldEmployee != null) {

            oldAssignment =
                    assessmentAssignmentRepository
                            .findByAssessmentAndEmployee(
                                    assessment,
                                    oldEmployee
                            )
                            .orElse(null);
        }


        // =====================================================
        // CANCEL OLD ASSIGNMENT
        // =====================================================

        if (oldAssignment != null) {

            /*
             * IMPORTANT:
             *
             * Only change STATUS.
             *
             * DO NOT RESET:
             *
             * score
             * maxScore
             * startedAt
             * completedAt
             * assignedAt
             *
             * This keeps the old employee's history.
             */

            oldAssignment.setStatus("CANCELLED");

            assessmentAssignmentRepository.save(
                    oldAssignment
            );
        }


        // =====================================================
        // RESET SAME ASSESSMENT FOR NEW EMPLOYEE
        // =====================================================

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * SAME assessment object.
         * SAME assessment ID.
         * SAME questions.
         */
        assessment.setEmployee(
                newEmployee
        );

        assessment.setStatus(
                "ASSIGNED"
        );

        assessment.setAssignedAt(
                now
        );

        /*
         * New employee has not started yet.
         */
        assessment.setStartedAt(
                null
        );

        /*
         * New employee has not completed yet.
         */
        assessment.setCompletedAt(
                null
        );

        /*
         * New employee starts with zero score.
         */
        assessment.setScore(
                0
        );


        // =====================================================
        // PRESERVE MAX SCORE
        // =====================================================

        /*
         * Do NOT change a valid existing maxScore.
         *
         * Only repair it if it is invalid.
         */
        if (assessment.getMaxScore() == null
                || assessment.getMaxScore() <= 0) {

            assessment.setMaxScore(
                    questionCount
            );
        }


        // =====================================================
        // SAVE SAME ASSESSMENT
        // =====================================================

        Assessment savedAssessment =
                assessmentRepository.save(
                        assessment
                );


        // =====================================================
        // CREATE NEW ASSIGNMENT
        // =====================================================

        AssessmentAssignment newAssignment =
                createAssignment(
                        savedAssessment,
                        newEmployee,
                        newTrainer,
                        now
                );


        // =====================================================
        // VERIFY NEW ASSIGNMENT
        // =====================================================

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

        Assessment assessment =
                getAssessment(id);

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

        assessment.setStatus(
                "IN_PROGRESS"
        );

        if (assessment.getStartedAt() == null) {

            assessment.setStartedAt(
                    LocalDateTime.now()
            );
        }

        return assessmentRepository.save(
                assessment
        );
    }


    // =========================================================
    // UPDATE STATUS
    // =========================================================

    @Transactional
    public Assessment updateStatus(
            Long id,
            String status
    ) {

        Assessment assessment =
                getAssessment(id);

        String newStatus =
                status == null
                        ? ""
                        : status.trim().toUpperCase();

        validateStatus(newStatus);

        assessment.setStatus(
                newStatus
        );

        if ("IN_PROGRESS".equals(newStatus)
                && assessment.getStartedAt() == null) {

            assessment.setStartedAt(
                    LocalDateTime.now()
            );
        }

        if ("COMPLETED".equals(newStatus)) {

            if (assessment.getCompletedAt() == null) {

                assessment.setCompletedAt(
                        LocalDateTime.now()
                );
            }

        } else {

            assessment.setCompletedAt(
                    null
            );
        }

        return assessmentRepository.save(
                assessment
        );
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

        return assessmentRepository.countByStatus(
                "ACTIVE"
        );
    }

    public long getPendingAssessments() {

        return assessmentRepository.countByStatus(
                "PENDING"
        );
    }

    public long getCompletedAssessments() {

        return assessmentRepository.countByStatus(
                "COMPLETED"
        );
    }

    public long getGeneratedAssessments() {

        return assessmentRepository.countByStatus(
                "GENERATED"
        );
    }

    public long getAssignedAssessments() {

        return assessmentRepository.countByStatus(
                "ASSIGNED"
        );
    }


    // =========================================================
    // DELETE ASSESSMENT
    // =========================================================

    @Transactional
    public void deleteAssessment(Long id) {

        Assessment assessment =
                getAssessment(id);

        /*
         * Delete assignment records first because
         * assessment_assignments references assessments.
         */
        List<AssessmentAssignment> assignments =
                assessmentAssignmentRepository
                        .findByAssessmentOrderByAssignedAtDesc(
                                assessment
                        );

        if (assignments != null
                && !assignments.isEmpty()) {

            assessmentAssignmentRepository.deleteAll(
                    assignments
            );
        }

        assessmentRepository.delete(
                assessment
        );
    }


    // =========================================================
    // VALIDATE EMPLOYEE
    // =========================================================

    private void validateEmployee(
            User employee
    ) {

        if (employee == null) {

            throw new RuntimeException(
                    "Employee is required."
            );
        }

        if (employee.getRole()
                != User.Role.EMPLOYEE) {

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

    private User getEmployeeTrainer(
            User employee
    ) {

        User trainer =
                employee.getTrainer();

        if (trainer == null) {

            throw new RuntimeException(
                    "This employee does not have an assigned trainer. " +
                    "Please assign a trainer to the employee first."
            );
        }

        if (trainer.getRole()
                != User.Role.TRAINER) {

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

    private void validateAssessmentType(
            String type
    ) {

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

    private void validateStatus(
            String status
    ) {

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