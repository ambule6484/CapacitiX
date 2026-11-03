
package com.capacitix.repository;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.Competency;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository for managing assessment questions.
 *
 * Supports:
 * - Fetching questions by assessment
 * - Fetching questions by competency
 * - Fetching active questions
 * - Counting questions
 * - Deleting questions by assessment
 */
public interface AssessmentQuestionRepository
        extends JpaRepository<AssessmentQuestion, Long> {

    // =========================================================
    // GET ALL QUESTIONS OF AN ASSESSMENT
    // =========================================================

    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByAssessmentOrderByQuestionOrderAsc(
            Assessment assessment
    );

    // =========================================================
    // GET QUESTIONS BY ASSESSMENT ID
    // =========================================================

    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByAssessmentIdOrderByQuestionOrderAsc(
            Long assessmentId
    );

    // =========================================================
    // GET QUESTIONS BY COMPETENCY
    // =========================================================

    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByCompetencyOrderByQuestionOrderAsc(
            Competency competency
    );

    // =========================================================
    // GET QUESTIONS BY COMPETENCY ID
    // =========================================================

    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByCompetencyIdOrderByQuestionOrderAsc(
            Long competencyId
    );

    // =========================================================
    // GET ACTIVE QUESTIONS OF AN ASSESSMENT
    // =========================================================

    @EntityGraph(attributePaths = {"assessment", "competency"})
    List<AssessmentQuestion> findByAssessmentAndActiveTrueOrderByQuestionOrderAsc(
            Assessment assessment
    );

    // =========================================================
    // COUNT QUESTIONS
    // =========================================================

    long countByAssessment(Assessment assessment);

    long countByAssessmentAndActiveTrue(Assessment assessment);

    // =========================================================
    // DELETE QUESTIONS BY ASSESSMENT
    // =========================================================

    /**
     * Explicit transaction ensures the derived delete operation
     * executes within a transaction.
     */
    @Transactional
    void deleteByAssessment(Assessment assessment);
}
