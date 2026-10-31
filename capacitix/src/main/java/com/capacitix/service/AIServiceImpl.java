
package com.capacitix.service;

import com.capacitix.dto.LearningPathItem;
import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIServiceImpl implements AIService {

    private final OllamaChatModel ollamaChatModel;

    // =========================================================
    // GENERATE LEARNING PATH
    // =========================================================

    @Override
    public LearningPathResponse generateLearningPath(
            User employee,
            Object skillGaps
    ) {

        if (employee == null) {
            throw new IllegalArgumentException(
                    "Employee cannot be null."
            );
        }

        List<SkillAnalysisService.SkillResult> gaps =
                getSkillGaps(skillGaps);

        // No skill gaps: no need to call Ollama.
        if (gaps.isEmpty()) {

            log.info(
                    "No skill gaps found for employee: {}",
                    employee.getEmail()
            );

            return buildLearningPathResponse(
                    employee,
                    gaps,
                    "Your assessment results indicate that "
                            + "no major skill gaps were identified. "
                            + "Continue practicing your existing skills."
            );
        }

        // Build a compact prompt containing only real gaps.
        String prompt = buildLearningPathPrompt(
                employee,
                gaps
        );

        String aiResponse;

        // Exactly ONE Ollama request.
        try {

            log.info(
                    "Generating learning path for employee: {} "
                            + "with {} skill gaps",
                    employee.getEmail(),
                    gaps.size()
            );

            aiResponse = ollamaChatModel.call(prompt);

            if (aiResponse == null || aiResponse.isBlank()) {

                aiResponse = getFallbackRecommendation();
            }

        } catch (Exception exception) {

            log.warn(
                    "Ollama learning path generation failed "
                            + "for employee {}: {}",
                    employee.getEmail(),
                    exception.getMessage()
            );

            // Learning path is still created using Java logic.
            aiResponse = getFallbackRecommendation();
        }

        return buildLearningPathResponse(
                employee,
                gaps,
                aiResponse
        );
    }

    // =========================================================
    // GET SKILL GAPS
    // =========================================================

    private List<SkillAnalysisService.SkillResult> getSkillGaps(
            Object skillGaps
    ) {

        if (!(skillGaps instanceof
                SkillAnalysisService.SkillAnalysisResult analysis)) {

            return new ArrayList<>();
        }

        if (analysis.skillGaps() == null) {
            return new ArrayList<>();
        }

        return analysis.skillGaps();
    }

    // =========================================================
    // BUILD COMPACT AI PROMPT
    // =========================================================

    private String buildLearningPathPrompt(
            User employee,
            List<SkillAnalysisService.SkillResult> gaps
    ) {

        StringBuilder prompt = new StringBuilder(1000);

        prompt.append("""
                You are CapacitiX, an employee learning assistant.

                Analyze the actual assessment skill gaps below.

                Rules:
                - Use only the provided assessment data.
                - Never invent scores or competencies.
                - Prioritize HIGH gaps before MEDIUM gaps.
                - Give practical and achievable advice.
                - Do not recommend unrelated skills.
                - Keep the recommendation concise.
                - Do not generate a separate learning path.
                
                Employee:
                """);

        prompt.append("\nName: ")
                .append(safe(employee.getFullName()));

        prompt.append("\nDepartment: ")
                .append(safe(employee.getDepartment()));

        prompt.append("\nDesignation: ")
                .append(safe(employee.getDesignation()));

        prompt.append("\n\nActual Skill Gaps:\n");

        for (SkillAnalysisService.SkillResult skill : gaps) {

            prompt.append("- Skill: ")
                    .append(skill.competencyName());

            prompt.append(" | Score: ")
                    .append(skill.percentage())
                    .append("%");

            prompt.append(" | Priority: ")
                    .append(skill.gap());

            prompt.append(" | Current Level: ")
                    .append(skill.currentLevel());

            prompt.append(" | Required Level: ")
                    .append(skill.requiredLevel());

            if (skill.category() != null
                    && !skill.category().isBlank()) {

                prompt.append(" | Category: ")
                        .append(skill.category());
            }

            prompt.append("\n");
        }

        prompt.append("""
                
                Provide:
                1. Overall assessment observation.
                2. Most important improvement area.
                3. Recommended learning strategy.
                4. Practical advice for improving competency.

                Keep the complete response within 100 words.
                Do not use markdown tables.
                """);

        return prompt.toString();
    }

    // =========================================================
    // BUILD LEARNING PATH RESPONSE
    // =========================================================

    private LearningPathResponse buildLearningPathResponse(
            User employee,
            List<SkillAnalysisService.SkillResult> gaps,
            String aiResponse
    ) {

        List<LearningPathItem> learningItems =
                new ArrayList<>();

        int order = 1;

        for (SkillAnalysisService.SkillResult skill : gaps) {

            String practicalTask =
                    createPracticalTask(
                            skill.competencyName()
                    );

            String duration =
                    calculateDuration(
                            skill.percentage()
                    );

            String outcome =
                    createExpectedOutcome(
                            skill.competencyName()
                    );

            List<String> topics =
                    createTopics(
                            skill.competencyName(),
                            skill.category()
                    );

            LearningPathItem item =
                    LearningPathItem.builder()

                            .skillName(
                                    skill.competencyName()
                            )

                            .priority(
                                    skill.gap()
                            )

                            .currentScore(
                                    (double) skill.percentage()
                            )

                            .gapReason(
                                    buildGapReason(skill)
                            )

                            .topics(
                                    topics
                            )

                            .practicalTask(
                                    practicalTask
                            )

                            .estimatedDuration(
                                    duration
                            )

                            .expectedOutcome(
                                    outcome
                            )

                            .learningOrder(
                                    order++
                            )

                            .build();

            learningItems.add(item);
        }

        // =====================================================
        // COUNT PRIORITIES
        // =====================================================

        int highPriority = 0;
        int mediumPriority = 0;

        for (LearningPathItem item : learningItems) {

            if ("HIGH".equalsIgnoreCase(
                    item.getPriority()
            )) {

                highPriority++;

            } else if ("MEDIUM".equalsIgnoreCase(
                    item.getPriority()
            )) {

                mediumPriority++;
            }
        }

        // =====================================================
        // FINAL RESPONSE
        // =====================================================

        return LearningPathResponse.builder()

                .employeeName(
                        employee.getFullName()
                )

                .employeeRole(
                        employee.getDesignation()
                )

                .totalSkillGaps(
                        learningItems.size()
                )

                .highPriorityGaps(
                        highPriority
                )

                .mediumPriorityGaps(
                        mediumPriority
                )

                .learningPath(
                        learningItems
                )

                .overallRecommendation(
                        aiResponse
                )

                .expectedOutcome(
                        learningItems.isEmpty()

                                ? "No major skill gaps were identified."

                                : "Complete the recommended learning "
                                + "topics and practical tasks to improve "
                                + "the identified competency gaps."
                )

                .build();
    }

    // =========================================================
    // GAP REASON
    // =========================================================

    private String buildGapReason(
            SkillAnalysisService.SkillResult skill
    ) {

        return "Your assessment score for "
                + skill.competencyName()
                + " is "
                + skill.percentage()
                + "%. Your current level is "
                + skill.currentLevel()
                + ", while the required level is "
                + skill.requiredLevel()
                + ".";
    }

    // =========================================================
    // TOPICS
    // =========================================================

    private List<String> createTopics(
            String competencyName,
            String category
    ) {

        List<String> topics = new ArrayList<>();

        topics.add(
                competencyName + " fundamentals"
        );

        topics.add(
                "Important concepts of "
                        + competencyName
        );

        topics.add(
                "Common problems and practical examples"
        );

        topics.add(
                "Hands-on practice with "
                        + competencyName
        );

        if (category != null && !category.isBlank()) {

            topics.add(
                    "Practical "
                            + category
                            + " use cases"
            );
        }

        return topics;
    }

    // =========================================================
    // PRACTICAL TASK
    // =========================================================

    private String createPracticalTask(
            String competencyName
    ) {

        return "Build a small practical project or "
                + "complete hands-on exercises using "
                + competencyName
                + " and demonstrate the result.";
    }

    // =========================================================
    // DURATION
    // =========================================================

    private String calculateDuration(
            int percentage
    ) {

        if (percentage < 40) {
            return "7-14 days";
        }

        if (percentage < 60) {
            return "5-7 days";
        }

        return "3-5 days";
    }

    // =========================================================
    // EXPECTED OUTCOME
    // =========================================================

    private String createExpectedOutcome(
            String competencyName
    ) {

        return "Improve practical understanding of "
                + competencyName
                + " and apply it confidently in "
                + "real-world tasks.";
    }

    // =========================================================
    // FALLBACK RECOMMENDATION
    // =========================================================

    private String getFallbackRecommendation() {

        return "Focus on the identified competency gaps. "
                + "Start with high-priority skills, study "
                + "the recommended topics, complete practical "
                + "tasks and reassess your progress regularly.";
    }

    // =========================================================
    // SAFE STRING
    // =========================================================

    private String safe(String value) {

        if (value == null || value.isBlank()) {
            return "Not specified";
        }

        return value;
    }
}
