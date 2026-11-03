
package com.capacitix.repository;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.Competency;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AssessmentQuestionRepository
        extends JpaRepository<AssessmentQuestion, Long> {

    /*
     * Get all questions of an assessment
     * with assessment and competency loaded.
     */
    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByAssessmentOrderByQuestionOrderAsc(
            Assessment assessment
    );

    /*
     * Get questions using Assessment ID.
     */
    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByAssessmentIdOrderByQuestionOrderAsc(
            Long assessmentId
    );

    /*
     * Get questions belonging to a particular competency.
     */
    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByCompetencyOrderByQuestionOrderAsc(
            Competency competency
    );

    /*
     * Get questions of a particular competency
     * using competency ID.
     */
    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByCompetencyIdOrderByQuestionOrderAsc(
            Long competencyId
    );

    /*
     * Get only active questions of an assessment.
     */
    @EntityGraph(attributePaths = {"assessment", "competency"})
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
     * Explicit transaction is important because Spring Data
     * derived delete operations may call EntityManager.remove().
     */
    @Transactional
    void deleteByAssessment(Assessment assessment);
}
