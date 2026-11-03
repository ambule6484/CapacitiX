package com.capacitix.repository;

import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.AssessmentResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentResponseRepository
        extends JpaRepository<AssessmentResponse, Long> {

    /*
     * Find all responses for one assessment assignment.
     */
    List<AssessmentResponse> findByAssignmentOrderByQuestion_QuestionOrderAsc(
            AssessmentAssignment assignment
    );


    /*
     * Find a response for a particular question
     * in a particular assignment.
     */
    Optional<AssessmentResponse> findByAssignmentAndQuestion(
            AssessmentAssignment assignment,
            AssessmentQuestion question
    );


    /*
     * Check whether the employee has already answered
     * a particular question.
     */
    boolean existsByAssignmentAndQuestion(
            AssessmentAssignment assignment,
            AssessmentQuestion question
    );


    /*
     * Count responses for an assignment.
     */
    long countByAssignment(
            AssessmentAssignment assignment
    );


    /*
     * Count correct answers.
     */
    long countByAssignmentAndCorrectTrue(
            AssessmentAssignment assignment
    );


    /*
     * Count wrong answers.
     */
    long countByAssignmentAndCorrectFalse(
            AssessmentAssignment assignment
    );


    /*
     * Find all correct responses of an assignment.
     */
    List<AssessmentResponse> findByAssignmentAndCorrectTrue(
            AssessmentAssignment assignment
    );


    /*
     * Find all wrong responses of an assignment.
     *
     * These will later help identify skill gaps.
     */
    List<AssessmentResponse> findByAssignmentAndCorrectFalse(
            AssessmentAssignment assignment
    );
}