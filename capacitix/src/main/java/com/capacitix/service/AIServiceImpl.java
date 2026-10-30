package com.capacitix.service;

import com.capacitix.dto.LearningPathItem;
import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
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


        // -----------------------------------------------------
        // BUILD AI PROMPT
        // -----------------------------------------------------

        String prompt =
                buildLearningPathPrompt(
                        employee,
                        skillGaps
                );


        // -----------------------------------------------------
        // CALL OLLAMA
        // -----------------------------------------------------

        String aiResponse =
                ollamaChatModel.call(prompt);


        // -----------------------------------------------------
        // CONVERT AI RESPONSE TO LEARNING PATH
        // -----------------------------------------------------

        return buildLearningPathResponse(
                employee,
                skillGaps,
                aiResponse
        );
    }


    // =========================================================
    // BUILD PROMPT
    // =========================================================

    private String buildLearningPathPrompt(
            User employee,
            Object skillGaps
    ) {

        StringBuilder prompt =
                new StringBuilder();


        prompt.append("""
                You are the AI Learning Path Generator
                for CapacitiX, an employee competency
                development platform.

                Create a personalized learning path
                based ONLY on the employee's assessment
                results and identified skill gaps.

                IMPORTANT RULES:

                1. Do not recommend skills that do not
                   have a skill gap.

                2. Give HIGH priority to skills below 40%.

                3. Give MEDIUM priority to skills from
                   40% to 59%.

                4. Do not recommend unnecessary learning
                   for skills at 60% or above.

                5. Start with basic concepts when the
                   employee has a large gap.

                6. Give practical learning topics.

                7. Give a practical task for every skill.

                8. Give an estimated learning duration.

                9. Give the expected outcome.

                10. Keep the explanation simple and
                    practical.

                Employee Information:
                """);


        prompt.append("\nName: ")
                .append(safe(employee.getFullName()));


        prompt.append("\nDepartment: ")
                .append(safe(employee.getDepartment()));


        prompt.append("\nDesignation: ")
                .append(safe(employee.getDesignation()));


        prompt.append("""

                
                Assessment Skill Analysis:
                """);


        prompt.append(skillGaps);


        prompt.append("""

                
                Based on the above assessment analysis,
                create a personalized learning path.

                For every skill gap provide:

                - Skill name
                - Current percentage
                - Priority
                - Reason for the gap
                - Topics to learn
                - Practical task
                - Estimated duration
                - Expected outcome

                Learning order must start with the
                highest priority skill gap.

                Do not invent assessment scores.
                Do not recommend unrelated skills.

                Return the result in simple structured
                text.
                """);


        return prompt.toString();
    }


    // =========================================================
    // BUILD RESPONSE
    // =========================================================

    private LearningPathResponse buildLearningPathResponse(
            User employee,
            Object skillGaps,
            String aiResponse
    ) {

        List<LearningPathItem> learningItems =
                new ArrayList<>();


        /*
         * The actual skill-gap data is already calculated
         * by SkillAnalysisService.
         *
         * We use that real data to create the learning path.
         */

        if (skillGaps instanceof
                SkillAnalysisService.SkillAnalysisResult analysis) {


            List<SkillAnalysisService.SkillResult> gaps =
                    analysis.skillGaps();


            int order = 1;


            for (SkillAnalysisService.SkillResult skill :
                    gaps) {


                String priority =
                        skill.gap();


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
                                        priority
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
        }


        // =====================================================
        // COUNT PRIORITIES
        // =====================================================

        int highPriority = 0;

        int mediumPriority = 0;


        for (LearningPathItem item :
                learningItems) {

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

        List<String> topics =
                new ArrayList<>();


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


        if (category != null
                && !category.isBlank()) {

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
    // SAFE STRING
    // =========================================================

    private String safe(String value) {

        if (value == null
                || value.isBlank()) {

            return "Not specified";
        }


        return value;
    }
}