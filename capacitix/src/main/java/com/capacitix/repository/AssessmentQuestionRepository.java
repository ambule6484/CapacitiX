package com.capacitix.repository;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.Competency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentQuestionRepository
        extends JpaRepository<AssessmentQuestion, Long> {

    /*
     * Get all questions of an assessment
     * in the correct question order.
     */
    List<AssessmentQuestion> findByAssessmentOrderByQuestionOrderAsc(
            Assessment assessment
    );


    /*
     * Get questions using Assessment ID.
     */
    List<AssessmentQuestion> findByAssessmentIdOrderByQuestionOrderAsc(
            Long assessmentId
    );


    /*
     * Get questions belonging to a particular competency.
     */
    List<AssessmentQuestion> findByCompetencyOrderByQuestionOrderAsc(
            Competency competency
    );


    /*
     * Get questions of a particular competency
     * using competency ID.
     */
    List<AssessmentQuestion> findByCompetencyIdOrderByQuestionOrderAsc(
            Long competencyId
    );


    /*
     * Get only active questions of an assessment.
     */
    List<AssessmentQuestion>
    findByAssessmentAndActiveTrueOrderByQuestionOrderAsc(
            Assessment assessment
    );


    /*
     * Count questions in an assessment.
     */
    long countByAssessment(Assessment assessment);


    /*
     * Count active questions in an assessment.
     */
    long countByAssessmentAndActiveTrue(
            Assessment assessment
    );


    /*
     * Delete all questions belonging to an assessment.
     *
     * Useful when regenerating an AI assessment.
     */
    void deleteByAssessment(Assessment assessment);
}