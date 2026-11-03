
package com.capacitix.service;

import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.AssessmentResponse;
import com.capacitix.entity.Competency;
import com.capacitix.repository.AssessmentResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillAnalysisService {

    private final AssessmentResponseRepository responseRepository;


    // =========================================================
    // ANALYZE COMPLETE ASSESSMENT
    // =========================================================

    public SkillAnalysisResult analyzeAssignment(
            AssessmentAssignment assignment
    ) {

        if (assignment == null) {
            throw new IllegalArgumentException(
                    "Assessment assignment is required."
            );
        }


        // =====================================================
        // LOAD RESPONSES
        // =====================================================

        List<AssessmentResponse> responses =
                responseRepository
                        .findByAssignmentOrderByQuestion_QuestionOrderAsc(
                                assignment
                        );

        if (responses == null) {
            responses = List.of();
        }


        // =====================================================
        // REMOVE DUPLICATE RESPONSES
        // =====================================================

        responses =
                removeDuplicateResponses(responses);


        // =====================================================
        // NO RESPONSES
        // =====================================================

        if (responses.isEmpty()) {

            int maxScore =
                    assignment.getMaxScore() != null
                            ? assignment.getMaxScore()
                            : 0;

            return new SkillAnalysisResult(
                    0,
                    maxScore,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }


        // =====================================================
        // OVERALL SCORE
        // =====================================================

        int totalMarks = 0;

        int obtainedMarks = 0;

        int correctAnswers = 0;

        int answeredQuestions = 0;


        for (AssessmentResponse response : responses) {

            if (response == null ||
                    response.getQuestion() == null) {

                continue;
            }


            int maxMarks =
                    getMaxMarks(response);


            int marksAwarded =
                    getEffectiveMarks(response);


            totalMarks += maxMarks;

            obtainedMarks += marksAwarded;

            answeredQuestions++;


            if (Boolean.TRUE.equals(
                    response.getCorrect()
            )) {

                correctAnswers++;
            }
        }


        // =====================================================
        // SAFETY
        // =====================================================

        if (obtainedMarks > totalMarks) {
            obtainedMarks = totalMarks;
        }

        if (obtainedMarks < 0) {
            obtainedMarks = 0;
        }


        // =====================================================
        // OVERALL PERCENTAGE
        // =====================================================

        int percentage =
                calculatePercentage(
                        obtainedMarks,
                        totalMarks
                );


        // =====================================================
        // COMPETENCY / SKILL ANALYSIS
        // =====================================================

        List<SkillResult> skillResults =
                analyzeSkills(responses);


        // =====================================================
        // SKILL GAPS
        // =====================================================

        List<SkillResult> skillGaps =
                skillResults.stream()
                        .filter(skill ->
                                skill.percentage() < 60
                        )
                        .toList();


        // =====================================================
        // LEARNING RECOMMENDATIONS
        // =====================================================

        List<LearningRecommendation> recommendations =
                createRecommendations(
                        skillGaps
                );


        // =====================================================
        // FINAL RESULT
        // =====================================================

        return new SkillAnalysisResult(
                obtainedMarks,
                totalMarks,
                percentage,
                answeredQuestions,
                correctAnswers,
                skillResults,
                skillGaps,
                recommendations
        );
    }


    // =========================================================
    // REMOVE DUPLICATE RESPONSES
    // =========================================================

    private List<AssessmentResponse> removeDuplicateResponses(
            List<AssessmentResponse> responses
    ) {

        if (responses == null ||
                responses.isEmpty()) {

            return List.of();
        }


        /*
         * One response should exist per question.
         *
         * If duplicate rows exist, keep the response
         * with the greatest database ID.
         */

        Map<Long, AssessmentResponse> uniqueResponses =
                new LinkedHashMap<>();


        for (AssessmentResponse response : responses) {

            if (response == null) {
                continue;
            }


            if (response.getQuestion() == null) {
                continue;
            }


            Long questionId =
                    response.getQuestion().getId();


            if (questionId == null) {
                continue;
            }


            AssessmentResponse existing =
                    uniqueResponses.get(questionId);


            if (existing == null) {

                uniqueResponses.put(
                        questionId,
                        response
                );

            } else {

                Long existingId =
                        existing.getId();

                Long currentId =
                        response.getId();


                if (currentId != null &&
                        (
                                existingId == null ||
                                currentId > existingId
                        )) {

                    uniqueResponses.put(
                            questionId,
                            response
                    );
                }
            }
        }


        return new ArrayList<>(
                uniqueResponses.values()
        );
    }


    // =========================================================
    // SKILL-WISE ANALYSIS
    // =========================================================

    private List<SkillResult> analyzeSkills(
            List<AssessmentResponse> responses
    ) {

        Map<Long, SkillAccumulator> skillMap =
                new LinkedHashMap<>();


        for (AssessmentResponse response : responses) {

            if (response == null ||
                    response.getQuestion() == null ||
                    response.getQuestion().getCompetency() == null) {

                continue;
            }


            Competency competency =
                    response.getQuestion()
                            .getCompetency();


            if (competency.getId() == null) {
                continue;
            }


            SkillAccumulator accumulator =
                    skillMap.computeIfAbsent(
                            competency.getId(),
                            id ->
                                    new SkillAccumulator(
                                            competency.getId(),
                                            competency.getName(),
                                            competency.getCategory(),
                                            competency.getLevel()
                                    )
                    );


            int maxMarks =
                    getMaxMarks(response);


            int marksAwarded =
                    getEffectiveMarks(response);


            accumulator.totalQuestions++;

            accumulator.maxMarks += maxMarks;

            accumulator.obtainedMarks += marksAwarded;


            if (Boolean.TRUE.equals(
                    response.getCorrect()
            )) {

                accumulator.correctAnswers++;
            }
        }


        // =====================================================
        // BUILD FINAL SKILL RESULTS
        // =====================================================

        List<SkillResult> results =
                new ArrayList<>();


        for (
                SkillAccumulator accumulator
                : skillMap.values()
        ) {

            if (accumulator.maxMarks <= 0) {
                continue;
            }


            /*
             * IMPORTANT:
             *
             * Skill percentage is calculated from the actual
             * marks obtained for questions belonging to this
             * competency.
             */

            int percentage =
                    calculatePercentage(
                            accumulator.obtainedMarks,
                            accumulator.maxMarks
                    );


            String currentLevel =
                    determineCurrentLevel(
                            percentage
                    );


            String requiredLevel =
                    normalizeLevel(
                            accumulator.requiredLevel
                    );


            String gap =
                    calculateGapPriority(
                            percentage
                    );


            results.add(
                    new SkillResult(
                            accumulator.competencyId,
                            accumulator.competencyName,
                            accumulator.category,
                            currentLevel,
                            requiredLevel,
                            accumulator.totalQuestions,
                            accumulator.correctAnswers,
                            accumulator.obtainedMarks,
                            accumulator.maxMarks,
                            percentage,
                            gap
                    )
            );
        }


        // =====================================================
        // LOWEST SCORE FIRST
        // =====================================================

        results.sort(
                Comparator.comparingInt(
                        SkillResult::percentage
                )
        );


        return results;
    }


    // =========================================================
    // EFFECTIVE MARKS
    // =========================================================
    /*
     * This is important for your current problem.
     *
     * Some AssessmentResponse records may have:
     *
     * correct = true
     * marksAwarded = null
     *
     * In that situation the previous implementation counted
     * the answer as correct but awarded ZERO marks.
     *
     * That caused a competency such as Django to remain at 0%.
     *
     * Now:
     *
     * marksAwarded exists
     *      -> use stored marks
     *
     * marksAwarded is null + correct=true
     *      -> award maxMarks
     *
     * marksAwarded is null + correct=false
     *      -> award 0
     */

    private int getEffectiveMarks(
            AssessmentResponse response
    ) {

        if (response == null) {
            return 0;
        }


        int maxMarks =
                getMaxMarks(response);


        Integer storedMarks =
                response.getMarksAwarded();


        if (storedMarks != null) {

            return clampMarks(
                    storedMarks,
                    maxMarks
            );
        }


        if (Boolean.TRUE.equals(
                response.getCorrect()
        )) {

            return maxMarks;
        }


        return 0;
    }


    // =========================================================
    // MAX MARKS
    // =========================================================

    private int getMaxMarks(
            AssessmentResponse response
    ) {

        if (response == null) {
            return 0;
        }


        Integer maxMarks =
                response.getMaxMarks();


        if (maxMarks == null ||
                maxMarks <= 0) {

            /*
             * AI-generated assessment questions normally
             * have maxMarks populated.
             *
             * If it is missing, assume one mark for a
             * standard MCQ-style question.
             */
            return 1;
        }


        return maxMarks;
    }


    // =========================================================
    // CLAMP MARKS
    // =========================================================

    private int clampMarks(
            int marks,
            int maxMarks
    ) {

        if (marks < 0) {
            return 0;
        }


        if (maxMarks <= 0) {
            return 0;
        }


        return Math.min(
                marks,
                maxMarks
        );
    }


    // =========================================================
    // LEARNING RECOMMENDATIONS
    // =========================================================

    private List<LearningRecommendation> createRecommendations(
            List<SkillResult> skillGaps
    ) {

        List<LearningRecommendation> recommendations =
                new ArrayList<>();


        if (skillGaps == null ||
                skillGaps.isEmpty()) {

            return recommendations;
        }


        for (SkillResult skill : skillGaps) {

            String priority =
                    skill.gap();


            String recommendation;


            if ("HIGH".equalsIgnoreCase(
                    priority
            )) {

                recommendation =
                        "Start with the fundamentals of "
                                + skill.competencyName()
                                + " and complete practical exercises.";

            } else if ("MEDIUM".equalsIgnoreCase(
                    priority
            )) {

                recommendation =
                        "Revise "
                                + skill.competencyName()
                                + " and practice real-world problems.";

            } else {

                recommendation =
                        "Continue practicing "
                                + skill.competencyName()
                                + " to improve competency.";
            }


            recommendations.add(
                    new LearningRecommendation(
                            skill.competencyId(),
                            skill.competencyName(),
                            skill.percentage(),
                            priority,
                            recommendation
                    )
            );
        }


        // =====================================================
        // HIGH → MEDIUM → LOW
        // =====================================================

        recommendations.sort(
                Comparator.comparing(
                        LearningRecommendation::priorityOrder
                )
        );


        return recommendations;
    }


    // =========================================================
    // SCORE CALCULATION
    // =========================================================

    private int calculatePercentage(
            int obtainedMarks,
            int maxMarks
    ) {

        if (maxMarks <= 0) {
            return 0;
        }


        if (obtainedMarks <= 0) {
            return 0;
        }


        if (obtainedMarks >= maxMarks) {
            return 100;
        }


        return (int) Math.round(
                ((double) obtainedMarks / maxMarks)
                        * 100
        );
    }


    // =========================================================
    // CURRENT LEVEL
    // =========================================================

    private String determineCurrentLevel(
            int percentage
    ) {

        if (percentage >= 80) {
            return "ADVANCED";
        }


        if (percentage >= 60) {
            return "INTERMEDIATE";
        }


        return "BEGINNER";
    }


    // =========================================================
    // REQUIRED LEVEL
    // =========================================================

    private String normalizeLevel(
            String level
    ) {

        if (level == null ||
                level.isBlank()) {

            return "INTERMEDIATE";
        }


        String normalized =
                level.trim()
                        .toUpperCase();


        return switch (normalized) {

            case "BEGINNER",
                 "BASIC" ->
                    "BEGINNER";

            case "INTERMEDIATE",
                 "MEDIUM",
                 "DEVELOPING" ->
                    "INTERMEDIATE";

            case "ADVANCED",
                 "EXPERT" ->
                    "ADVANCED";

            default ->
                    normalized;
        };
    }


    // =========================================================
    // GAP PRIORITY
    // =========================================================

    private String calculateGapPriority(
            int percentage
    ) {

        if (percentage < 40) {
            return "HIGH";
        }


        if (percentage < 60) {
            return "MEDIUM";
        }


        return "LOW";
    }


    // =========================================================
    // PUBLIC RESULT
    // =========================================================

    public record SkillAnalysisResult(

            int obtainedMarks,

            int maxMarks,

            int percentage,

            int totalQuestions,

            int correctAnswers,

            List<SkillResult> skills,

            List<SkillResult> skillGaps,

            List<LearningRecommendation> recommendations

    ) {
    }


    // =========================================================
    // SKILL RESULT
    // =========================================================

    public record SkillResult(

            Long competencyId,

            String competencyName,

            String category,

            String currentLevel,

            String requiredLevel,

            int totalQuestions,

            int correctAnswers,

            int obtainedMarks,

            int maxMarks,

            int percentage,

            String gap

    ) {
    }


    // =========================================================
    // LEARNING RECOMMENDATION
    // =========================================================

    public record LearningRecommendation(

            Long competencyId,

            String competencyName,

            int percentage,

            String priority,

            String recommendation

    ) {

        public int priorityOrder() {

            if ("HIGH".equalsIgnoreCase(
                    priority
            )) {

                return 1;
            }


            if ("MEDIUM".equalsIgnoreCase(
                    priority
            )) {

                return 2;
            }


            return 3;
        }
    }


    // =========================================================
    // INTERNAL ACCUMULATOR
    // =========================================================

    private static class SkillAccumulator {

        private final Long competencyId;

        private final String competencyName;

        private final String category;

        private final String requiredLevel;

        private int totalQuestions;

        private int correctAnswers;

        private int obtainedMarks;

        private int maxMarks;


        private SkillAccumulator(
                Long competencyId,
                String competencyName,
                String category,
                String requiredLevel
        ) {

            this.competencyId =
                    competencyId;

            this.competencyName =
                    competencyName;

            this.category =
                    category;

            this.requiredLevel =
                    requiredLevel;
        }
    }
}