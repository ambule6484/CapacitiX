
package com.capacitix.service;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.Competency;
import com.capacitix.repository.AssessmentQuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIQuestionGeneratorService {

    private final AssessmentQuestionRepository questionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================
    // OLLAMA CONFIGURATION
    // =========================================================

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    @Value("${ollama.timeout:120000}")
    private long ollamaTimeout;

    @Value("${ollama.temperature:0.3}")
    private double ollamaTemperature;

    @Value("${ollama.num-predict:2048}")
    private int ollamaNumPredict;

    /*
     * Number of attempts for one question.
     */
    private static final int MAX_ATTEMPTS_PER_QUESTION = 5;

    // =========================================================
    // MAIN METHOD
    // =========================================================

    @Transactional
    public List<AssessmentQuestion> generateQuestions(
            Assessment assessment,
            List<Competency> competencies
    ) {

        validateInput(assessment, competencies);

        int requiredCount = assessment.getQuestionCount();

        // -----------------------------------------------------
        // CREATE COMPETENCY MAP
        // -----------------------------------------------------

        Map<String, Competency> competencyMap =
                competencies.stream()
                        .filter(c -> c != null)
                        .filter(c -> notBlank(c.getName()))
                        .collect(Collectors.toMap(
                                c -> normalize(c.getName()),
                                c -> c,
                                (first, second) -> first
                        ));

        if (competencyMap.isEmpty()) {

            throw new RuntimeException(
                    "No valid competencies were selected."
            );
        }

        // -----------------------------------------------------
        // GENERATE QUESTIONS
        // -----------------------------------------------------

        List<GeneratedQuestion> generatedQuestions =
                new ArrayList<>();

        Set<String> questionKeys =
                new HashSet<>();

        int questionNumber = 1;

        while (generatedQuestions.size() < requiredCount) {

            log.info(
                    "Generating assessment question {} of {}",
                    questionNumber,
                    requiredCount
            );

            GeneratedQuestion question =
                    generateOneQuestion(
                            assessment,
                            competencies,
                            competencyMap,
                            questionNumber,
                            questionKeys
                    );

            if (question == null) {

                throw new RuntimeException(
                        "AI could not generate question "
                                + questionNumber
                                + " after "
                                + MAX_ATTEMPTS_PER_QUESTION
                                + " attempts."
                );
            }

            // -------------------------------------------------
            // NORMALIZE COMPETENCY
            // -------------------------------------------------

            Competency matchedCompetency =
                    findCompetency(
                            question.competency,
                            competencyMap
                    );

            if (matchedCompetency == null) {

                throw new RuntimeException(
                        "AI returned an invalid competency "
                                + "for question "
                                + questionNumber
                                + "."
                );
            }

            question.competency =
                    matchedCompetency.getName();

            // -------------------------------------------------
            // NORMALIZE ANSWER
            // -------------------------------------------------

            question.correctAnswer =
                    normalizeAnswer(
                            question.correctAnswer,
                            question
                    );

            if (!isValidCorrectAnswer(
                    question.correctAnswer
            )) {

                throw new RuntimeException(
                        "AI returned an invalid correct answer "
                                + "for question "
                                + questionNumber
                                + "."
                );
            }

            // -------------------------------------------------
            // DUPLICATE CHECK
            // -------------------------------------------------

            String key =
                    questionKey(question);

            if (questionKeys.contains(key)) {

                /*
                 * Normally this should already be prevented
                 * inside generateOneQuestion().
                 */
                log.warn(
                        "Duplicate question detected after generation: {}",
                        question.question
                );

                continue;
            }

            questionKeys.add(key);

            generatedQuestions.add(question);

            log.info(
                    "Successfully generated question {}",
                    questionNumber
            );

            questionNumber++;
        }

        // -----------------------------------------------------
        // FINAL CHECK
        // -----------------------------------------------------

        if (generatedQuestions.size() != requiredCount) {

            throw new RuntimeException(
                    "AI generated "
                            + generatedQuestions.size()
                            + " questions instead of "
                            + requiredCount
                            + "."
            );
        }

        // -----------------------------------------------------
        // CONVERT TO DATABASE QUESTIONS
        // -----------------------------------------------------

        List<AssessmentQuestion> questions =
                new ArrayList<>();

        int order = 1;

        for (GeneratedQuestion generated :
                generatedQuestions) {

            Competency competency =
                    findCompetency(
                            generated.competency,
                            competencyMap
                    );

            if (competency == null) {
                continue;
            }

            int marks =
                    generated.marks > 0
                            ? generated.marks
                            : 1;

            AssessmentQuestion question =
                    AssessmentQuestion.builder()
                            .assessment(assessment)
                            .competency(competency)
                            .questionText(
                                    generated.question.trim()
                            )
                            .questionType("MCQ")
                            .optionA(
                                    generated.optionA.trim()
                            )
                            .optionB(
                                    generated.optionB.trim()
                            )
                            .optionC(
                                    generated.optionC.trim()
                            )
                            .optionD(
                                    generated.optionD.trim()
                            )
                            .correctAnswer(
                                    generated.correctAnswer
                            )
                            .difficulty(
                                    normalizeDifficulty(
                                            generated.difficulty
                                    )
                            )
                            .marks(marks)
                            .explanation(
                                    safeNullable(
                                            generated.explanation
                                    )
                            )
                            .questionOrder(order)
                            .active(true)
                            .build();

            questions.add(question);

            order++;
        }

        // -----------------------------------------------------
        // FINAL DATABASE QUESTION CHECK
        // -----------------------------------------------------

        if (questions.size() != requiredCount) {

            throw new RuntimeException(
                    "Only "
                            + questions.size()
                            + " valid questions could be "
                            + "prepared out of "
                            + requiredCount
                            + "."
            );
        }

        // -----------------------------------------------------
        // DELETE OLD QUESTIONS
        // -----------------------------------------------------

        questionRepository.deleteByAssessment(
                assessment
        );

        // -----------------------------------------------------
        // SAVE QUESTIONS
        // -----------------------------------------------------

        List<AssessmentQuestion> savedQuestions =
                questionRepository.saveAll(
                        questions
                );

        // -----------------------------------------------------
        // DATABASE VALIDATION
        // -----------------------------------------------------

        if (savedQuestions.size() != requiredCount) {

            throw new RuntimeException(
                    "Assessment generation failed. "
                            + "Expected "
                            + requiredCount
                            + " questions but saved "
                            + savedQuestions.size()
                            + "."
            );
        }

        assessment.setQuestionCount(
                savedQuestions.size()
        );

        log.info(
                "Assessment question generation completed successfully. {} questions created.",
                savedQuestions.size()
        );

        return savedQuestions;
    }

    // =========================================================
    // GENERATE ONE QUESTION
    // =========================================================

    private GeneratedQuestion generateOneQuestion(
            Assessment assessment,
            List<Competency> competencies,
            Map<String, Competency> competencyMap,
            int questionNumber,
            Set<String> existingQuestionKeys
    ) {

        RuntimeException lastException = null;

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS_PER_QUESTION;
             attempt++) {

            try {

                /*
                 * Every retry receives a different prompt strategy.
                 * This is important because otherwise llama3.2 may
                 * repeatedly return the same invalid/duplicate question.
                 */
                String prompt =
                        buildQuestionPrompt(
                                assessment,
                                competencies,
                                competencyMap,
                                questionNumber,
                                existingQuestionKeys,
                                attempt
                        );

                log.info(
                        "Ollama attempt {}/{} for question {}",
                        attempt,
                        MAX_ATTEMPTS_PER_QUESTION,
                        questionNumber
                );

                String response =
                        callOllama(
                                prompt,
                                attempt
                        );

                log.debug(
                        "Ollama response for question {} attempt {}: {}",
                        questionNumber,
                        attempt,
                        responsePreview(response)
                );

                GeneratedQuestion question =
                        parseQuestion(response);

                if (!isValidQuestion(question)) {

                    throw new RuntimeException(
                            "AI returned an incomplete question."
                    );
                }

                // -------------------------------------------------
                // MATCH COMPETENCY
                // -------------------------------------------------

                Competency competency =
                        findCompetency(
                                question.competency,
                                competencyMap
                        );

                if (competency == null) {

                    throw new RuntimeException(
                            "AI returned an unknown competency: "
                                    + question.competency
                    );
                }

                question.competency =
                        competency.getName();

                // -------------------------------------------------
                // NORMALIZE ANSWER
                // -------------------------------------------------

                question.correctAnswer =
                        normalizeAnswer(
                                question.correctAnswer,
                                question
                        );

                if (!isValidCorrectAnswer(
                        question.correctAnswer
                )) {

                    throw new RuntimeException(
                            "Invalid correct answer: "
                                    + question.correctAnswer
                    );
                }

                // -------------------------------------------------
                // CHECK DUPLICATE
                // -------------------------------------------------

                String key =
                        questionKey(question);

                if (existingQuestionKeys.contains(key)) {

                    throw new RuntimeException(
                            "AI generated a duplicate question: "
                                    + question.question
                    );
                }

                return question;

            } catch (RuntimeException e) {

                lastException = e;

                log.warn(
                        "Question {} attempt {} failed: {}",
                        questionNumber,
                        attempt,
                        safeExceptionMessage(e)
                );

                /*
                 * Short delay between retries.
                 */
                try {

                    Thread.sleep(500L);

                } catch (InterruptedException interruptedException) {

                    Thread.currentThread().interrupt();

                    throw new RuntimeException(
                            "Question generation was interrupted.",
                            interruptedException
                    );
                }
            }
        }

        if (lastException != null) {

            log.error(
                    "Unable to generate question {} after {} attempts. Last error: {}",
                    questionNumber,
                    MAX_ATTEMPTS_PER_QUESTION,
                    safeExceptionMessage(lastException)
            );
        }

        return null;
    }

    // =========================================================
    // BUILD QUESTION PROMPT
    // =========================================================

    private String buildQuestionPrompt(
            Assessment assessment,
            List<Competency> competencies,
            Map<String, Competency> competencyMap,
            int questionNumber,
            Set<String> existingQuestionKeys,
            int attempt
    ) {

        String competencyText =
                competencies.stream()
                        .filter(c -> c != null)
                        .map(this::formatCompetency)
                        .collect(
                                Collectors.joining("\n")
                        );

        String previousQuestions =
                buildPreviousQuestionsText(
                        existingQuestionKeys
                );

        String retryInstruction;

        switch (attempt) {

            case 2:
                retryInstruction =
                        """
                        This is a retry.
                        Generate a completely different question.
                        Do not reuse the wording or concept of a previous question.
                        """;
                break;

            case 3:
                retryInstruction =
                        """
                        This is retry number 3.
                        Change the question angle.
                        Test a different practical concept from the selected competency.
                        Do not repeat any previous question.
                        """;
                break;

            case 4:
                retryInstruction =
                        """
                        This is retry number 4.
                        Use a new technical scenario or example.
                        Make the question clearly different from all previous questions.
                        Return strictly valid JSON.
                        """;
                break;

            case 5:
                retryInstruction =
                        """
                        Final retry.
                        Create a simple but completely new technical MCQ.
                        Avoid all previous question wording and concepts.
                        Output ONLY the JSON object.
                        """;
                break;

            default:
                retryInstruction =
                        """
                        Create a fresh question.
                        """;
        }

        return """
                You are an MCQ question generator for the
                CapacitiX employee skill assessment system.

                Generate EXACTLY ONE question.

                Assessment name:
                %s

                Target role:
                %s

                Allowed competencies:
                %s

                Question number:
                %d

                Generation attempt:
                %d

                %s

                IMPORTANT RULES:

                1. Use ONLY one competency from the allowed list.
                2. Use the exact competency name.
                3. Generate a useful technical question.
                4. Use simple English.
                5. Test understanding, not memorization only.
                6. Avoid duplicate questions.
                7. Avoid questions with the same concept as previous questions.
                8. Provide exactly four options.
                9. optionA, optionB, optionC and optionD must contain text.
                10. Only one option must be correct.
                11. correctAnswer must be exactly A, B, C or D.
                12. difficulty must be BEGINNER, INTERMEDIATE or ADVANCED.
                13. marks must be exactly 1.
                14. Keep explanation short.
                15. Return ONLY one JSON object.
                16. Do NOT use Markdown.
                17. Do NOT write ```json.
                18. Do NOT write any text before JSON.
                19. Do NOT write any text after JSON.
                20. Ensure all JSON strings are properly quoted.
                21. Do not include newline characters inside JSON string values.

                Previous questions to avoid:
                %s

                Return exactly this structure:

                {
                  "competency": "Exact competency name",
                  "question": "Question text",
                  "optionA": "First option",
                  "optionB": "Second option",
                  "optionC": "Third option",
                  "optionD": "Fourth option",
                  "correctAnswer": "A",
                  "difficulty": "INTERMEDIATE",
                  "marks": 1,
                  "explanation": "Short explanation"
                }
                """.formatted(
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyText,
                questionNumber,
                attempt,
                retryInstruction,
                previousQuestions
        );
    }

    // =========================================================
    // PREVIOUS QUESTIONS
    // =========================================================

    private String buildPreviousQuestionsText(
            Set<String> existingQuestionKeys
    ) {

        if (existingQuestionKeys == null
                || existingQuestionKeys.isEmpty()) {

            return "None.";
        }

        StringBuilder builder =
                new StringBuilder();

        int count = 0;

        /*
         * Send all generated questions when possible.
         * For larger assessments, keep the prompt reasonable.
         */
        for (String key :
                existingQuestionKeys) {

            if (count >= 15) {
                break;
            }

            builder.append("- ")
                    .append(key)
                    .append("\n");

            count++;
        }

        return builder.toString();
    }

    // =========================================================
    // CALL OLLAMA
    // =========================================================

    private String callOllama(
            String prompt,
            int attempt
    ) {

        String baseUrl =
                removeTrailingSlash(
                        ollamaBaseUrl
                );

        RestClient restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .requestFactory(
                                createRequestFactory()
                        )
                        .build();

        /*
         * Increase temperature slightly on retries.
         *
         * Example:
         * attempt 1 -> configured temperature
         * attempt 2 -> +0.10
         * attempt 3 -> +0.20
         * ...
         *
         * This helps prevent llama3.2 from returning the
         * same question repeatedly.
         */
        double retryTemperature =
                Math.min(
                        0.9,
                        Math.max(
                                0.0,
                                ollamaTemperature
                        ) + ((attempt - 1) * 0.10)
                );

        OllamaRequest request =
                new OllamaRequest(
                        ollamaModel,
                        prompt,
                        false,
                        "json",
                        new OllamaOptions(
                                retryTemperature,
                                ollamaNumPredict
                        )
                );

        try {

            OllamaResponse response =
                    restClient.post()
                            .uri("/api/generate")
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(request)
                            .retrieve()
                            .body(OllamaResponse.class);

            if (response == null) {

                throw new RuntimeException(
                        "Ollama returned an empty response."
                );
            }

            if (response.response() == null
                    || response.response().isBlank()) {

                throw new RuntimeException(
                        "Ollama returned an empty AI response."
                );
            }

            return response.response();

        } catch (RestClientResponseException e) {

            String body =
                    e.getResponseBodyAsString();

            String message =
                    "Ollama returned HTTP "
                            + e.getStatusCode().value()
                            + " "
                            + e.getStatusText();

            if (body != null
                    && !body.isBlank()) {

                message +=
                        ". Response: "
                                + responsePreview(body);
            }

            throw new RuntimeException(
                    message,
                    e
            );

        } catch (ResourceAccessException e) {

            throw new RuntimeException(
                    "Cannot connect to Ollama at "
                            + baseUrl
                            + ". Make sure Ollama is running "
                            + "on port 11434.",
                    e
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Ollama generation failed: "
                            + safeExceptionMessage(e),
                    e
            );
        }
    }

    // =========================================================
    // REQUEST FACTORY
    // =========================================================

    private org.springframework.http.client.ClientHttpRequestFactory
    createRequestFactory() {

        org.springframework.http.client
                .SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client
                        .SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                Duration.ofSeconds(10)
        );

        factory.setReadTimeout(
                Duration.ofMillis(ollamaTimeout)
        );

        return factory;
    }

    // =========================================================
    // PARSE ONE QUESTION
    // =========================================================

    private GeneratedQuestion parseQuestion(
            String response
    ) {

        try {

            String json =
                    cleanJson(response);

            if (json.isBlank()) {

                throw new RuntimeException(
                        "Empty JSON response."
                );
            }

            JsonNode root =
                    objectMapper.readTree(json);

            if (root == null
                    || root.isNull()) {

                throw new RuntimeException(
                        "Empty JSON object."
                );
            }

            /*
             * Sometimes the model returns an array.
             * Accept the first object.
             */
            if (root.isArray()) {

                if (root.isEmpty()) {

                    throw new RuntimeException(
                            "AI returned an empty array."
                    );
                }

                root =
                        root.get(0);
            }

            if (!root.isObject()) {

                throw new RuntimeException(
                        "AI response is not a JSON object."
                );
            }

            GeneratedQuestion question =
                    new GeneratedQuestion();

            question.competency =
                    firstText(
                            root,
                            "competency",
                            "skill",
                            "competencyName"
                    );

            question.question =
                    firstText(
                            root,
                            "question",
                            "questionText",
                            "text"
                    );

            question.optionA =
                    firstText(
                            root,
                            "optionA",
                            "option_a",
                            "A",
                            "a"
                    );

            question.optionB =
                    firstText(
                            root,
                            "optionB",
                            "option_b",
                            "B",
                            "b"
                    );

            question.optionC =
                    firstText(
                            root,
                            "optionC",
                            "option_c",
                            "C",
                            "c"
                    );

            question.optionD =
                    firstText(
                            root,
                            "optionD",
                            "option_d",
                            "D",
                            "d"
                    );

            // -------------------------------------------------
            // OPTIONS ARRAY SUPPORT
            // -------------------------------------------------

            JsonNode options =
                    root.get("options");

            if (options != null
                    && options.isArray()
                    && options.size() >= 4) {

                if (!notBlank(question.optionA)) {
                    question.optionA =
                            nodeText(
                                    options.get(0)
                            );
                }

                if (!notBlank(question.optionB)) {
                    question.optionB =
                            nodeText(
                                    options.get(1)
                            );
                }

                if (!notBlank(question.optionC)) {
                    question.optionC =
                            nodeText(
                                    options.get(2)
                            );
                }

                if (!notBlank(question.optionD)) {
                    question.optionD =
                            nodeText(
                                    options.get(3)
                            );
                }
            }

            question.correctAnswer =
                    firstText(
                            root,
                            "correctAnswer",
                            "correct_answer",
                            "answer",
                            "correctOption",
                            "correct_option"
                    );

            question.difficulty =
                    firstText(
                            root,
                            "difficulty",
                            "level"
                    );

            question.explanation =
                    firstText(
                            root,
                            "explanation",
                            "reason"
                    );

            JsonNode marks =
                    root.get("marks");

            if (marks != null
                    && marks.isNumber()) {

                question.marks =
                        marks.asInt();

            } else {

                question.marks = 1;
            }

            return question;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to parse AI question. "
                            + "Response: "
                            + responsePreview(response),
                    e
            );
        }
    }

    // =========================================================
    // TEXT FROM JSON
    // =========================================================

    private String text(
            JsonNode node,
            String field
    ) {

        if (node == null
                || field == null) {

            return "";
        }

        JsonNode value =
                node.get(field);

        return nodeText(value);
    }

    private String firstText(
            JsonNode node,
            String... fields
    ) {

        if (node == null
                || fields == null) {

            return "";
        }

        for (String field : fields) {

            String value =
                    text(node, field);

            if (notBlank(value)) {
                return value;
            }
        }

        return "";
    }

    private String nodeText(
            JsonNode node
    ) {

        if (node == null
                || node.isNull()) {

            return "";
        }

        if (node.isTextual()) {

            return node.asText().trim();
        }

        return node.toString().trim();
    }

    // =========================================================
    // CLEAN JSON
    // =========================================================

    private String cleanJson(
            String response
    ) {

        if (response == null
                || response.isBlank()) {

            return "";
        }

        String json =
                response.trim();

        // Remove Markdown fences.
        if (json.startsWith("```")) {

            int firstNewLine =
                    json.indexOf('\n');

            if (firstNewLine >= 0) {

                json =
                        json.substring(
                                firstNewLine + 1
                        );
            }

            int lastFence =
                    json.lastIndexOf("```");

            if (lastFence >= 0) {

                json =
                        json.substring(
                                0,
                                lastFence
                        );
            }
        }

        json =
                json.trim();

        /*
         * Sometimes Ollama may return a short explanation
         * before the JSON object.
         *
         * Extract the JSON object.
         */
        int objectStart =
                json.indexOf('{');

        int objectEnd =
                json.lastIndexOf('}');

        if (objectStart >= 0
                && objectEnd > objectStart) {

            return json.substring(
                    objectStart,
                    objectEnd + 1
            ).trim();
        }

        // Array fallback.
        int arrayStart =
                json.indexOf('[');

        int arrayEnd =
                json.lastIndexOf(']');

        if (arrayStart >= 0
                && arrayEnd > arrayStart) {

            return json.substring(
                    arrayStart,
                    arrayEnd + 1
            ).trim();
        }

        return json;
    }

    // =========================================================
    // FIND COMPETENCY
    // =========================================================

    private Competency findCompetency(
            String aiCompetency,
            Map<String, Competency> competencyMap
    ) {

        if (!notBlank(aiCompetency)) {
            return null;
        }

        String normalizedAI =
                normalize(aiCompetency);

        // Exact match.
        Competency exact =
                competencyMap.get(
                        normalizedAI
                );

        if (exact != null) {
            return exact;
        }

        // Contains match.
        for (Map.Entry<String, Competency> entry :
                competencyMap.entrySet()) {

            String known =
                    entry.getKey();

            if (normalizedAI.contains(known)
                    || known.contains(normalizedAI)) {

                return entry.getValue();
            }
        }

        // Word match.
        String[] aiWords =
                normalizedAI.split("\\s+");

        Competency bestMatch = null;

        int bestScore = 0;

        for (Map.Entry<String, Competency> entry :
                competencyMap.entrySet()) {

            String[] knownWords =
                    entry.getKey().split("\\s+");

            int score = 0;

            for (String aiWord :
                    aiWords) {

                if (aiWord.length() < 3) {
                    continue;
                }

                for (String knownWord :
                        knownWords) {

                    if (aiWord.equals(knownWord)) {
                        score++;
                    }
                }
            }

            if (score > bestScore) {

                bestScore = score;

                bestMatch =
                        entry.getValue();
            }
        }

        return bestScore > 0
                ? bestMatch
                : null;
    }

    // =========================================================
    // QUESTION KEY
    // =========================================================

    private String questionKey(
            GeneratedQuestion question
    ) {

        if (question == null) {
            return "";
        }

        return normalize(
                question.competency
        )
                + "::"
                + normalize(
                question.question
        );
    }

    // =========================================================
    // VALIDATE QUESTION
    // =========================================================

    private boolean isValidQuestion(
            GeneratedQuestion question
    ) {

        if (question == null) {
            return false;
        }

        if (!notBlank(question.competency)) {
            return false;
        }

        if (!notBlank(question.question)) {
            return false;
        }

        if (!notBlank(question.optionA)) {
            return false;
        }

        if (!notBlank(question.optionB)) {
            return false;
        }

        if (!notBlank(question.optionC)) {
            return false;
        }

        if (!notBlank(question.optionD)) {
            return false;
        }

        if (!notBlank(question.correctAnswer)) {
            return false;
        }

        /*
         * Prevent duplicate option values.
         */
        if (sameText(
                question.optionA,
                question.optionB
        )) {
            return false;
        }

        if (sameText(
                question.optionA,
                question.optionC
        )) {
            return false;
        }

        if (sameText(
                question.optionA,
                question.optionD
        )) {
            return false;
        }

        if (sameText(
                question.optionB,
                question.optionC
        )) {
            return false;
        }

        if (sameText(
                question.optionB,
                question.optionD
        )) {
            return false;
        }

        if (sameText(
                question.optionC,
                question.optionD
        )) {
            return false;
        }

        return true;
    }

    // =========================================================
    // NORMALIZE ANSWER
    // =========================================================

    private String normalizeAnswer(
            String answer,
            GeneratedQuestion question
    ) {

        if (answer == null
                || answer.isBlank()) {

            return "";
        }

        String value =
                answer.trim();

        String upper =
                value.toUpperCase(
                        Locale.ROOT
                );

        // -----------------------------------------------------
        // Direct A/B/C/D
        // -----------------------------------------------------

        if (upper.equals("A")
                || upper.matches(
                "^A[\\s\\.\\):\\-].*"
        )
                || upper.startsWith("OPTION A")
                || upper.startsWith("ANSWER A")
                || upper.startsWith("ANSWER: A")) {

            return "A";
        }

        if (upper.equals("B")
                || upper.matches(
                "^B[\\s\\.\\):\\-].*"
        )
                || upper.startsWith("OPTION B")
                || upper.startsWith("ANSWER B")
                || upper.startsWith("ANSWER: B")) {

            return "B";
        }

        if (upper.equals("C")
                || upper.matches(
                "^C[\\s\\.\\):\\-].*"
        )
                || upper.startsWith("OPTION C")
                || upper.startsWith("ANSWER C")
                || upper.startsWith("ANSWER: C")) {

            return "C";
        }

        if (upper.equals("D")
                || upper.matches(
                "^D[\\s\\.\\):\\-].*"
        )
                || upper.startsWith("OPTION D")
                || upper.startsWith("ANSWER D")
                || upper.startsWith("ANSWER: D")) {

            return "D";
        }

        // -----------------------------------------------------
        // Numeric answer support
        //
        // 0 -> A
        // 1 -> B
        // 2 -> C
        // 3 -> D
        // -----------------------------------------------------

        switch (upper) {

            case "0":
                return "A";

            case "1":
                return "B";

            case "2":
                return "C";

            case "3":
                return "D";

            default:
                break;
        }

        // -----------------------------------------------------
        // Complete option text
        // -----------------------------------------------------

        if (question != null) {

            if (sameText(
                    value,
                    question.optionA
            )) {
                return "A";
            }

            if (sameText(
                    value,
                    question.optionB
            )) {
                return "B";
            }

            if (sameText(
                    value,
                    question.optionC
            )) {
                return "C";
            }

            if (sameText(
                    value,
                    question.optionD
            )) {
                return "D";
            }
        }

        return "";
    }

    // =========================================================
    // VALIDATE ANSWER
    // =========================================================

    private boolean isValidCorrectAnswer(
            String answer
    ) {

        return "A".equals(answer)
                || "B".equals(answer)
                || "C".equals(answer)
                || "D".equals(answer);
    }

    // =========================================================
    // NORMALIZE DIFFICULTY
    // =========================================================

    private String normalizeDifficulty(
            String difficulty
    ) {

        if (!notBlank(difficulty)) {
            return "INTERMEDIATE";
        }

        String value =
                difficulty
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if ("BEGINNER".equals(value)
                || "EASY".equals(value)) {

            return "BEGINNER";
        }

        if ("ADVANCED".equals(value)
                || "HARD".equals(value)) {

            return "ADVANCED";
        }

        return "INTERMEDIATE";
    }

    // =========================================================
    // VALIDATE INPUT
    // =========================================================

    private void validateInput(
            Assessment assessment,
            List<Competency> competencies
    ) {

        if (assessment == null) {

            throw new RuntimeException(
                    "Assessment is required."
            );
        }

        if (competencies == null
                || competencies.isEmpty()) {

            throw new RuntimeException(
                    "Please select at least one competency."
            );
        }

        if (assessment.getQuestionCount() == null
                || assessment.getQuestionCount() <= 0) {

            throw new RuntimeException(
                    "Question count must be greater than 0."
            );
        }

        if (assessment.getQuestionCount() > 50) {

            throw new RuntimeException(
                    "Question count cannot be greater than 50."
            );
        }
    }

    // =========================================================
    // FORMAT COMPETENCY
    // =========================================================

    private String formatCompetency(
            Competency competency
    ) {

        return "- "
                + safe(competency.getName())
                + " | Category: "
                + safe(competency.getCategory())
                + " | Level: "
                + safe(competency.getLevel())
                + " | Description: "
                + safe(competency.getDescription());
    }

    // =========================================================
    // NORMALIZE TEXT
    // =========================================================

    private String normalize(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll(
                        "[^a-z0-9\\s]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    // =========================================================
    // COMPARE TEXT
    // =========================================================

    private boolean sameText(
            String first,
            String second
    ) {

        if (!notBlank(first)
                || !notBlank(second)) {

            return false;
        }

        return normalize(first)
                .equals(
                        normalize(second)
                );
    }

    // =========================================================
    // NOT BLANK
    // =========================================================

    private boolean notBlank(
            String value
    ) {

        return value != null
                && !value.trim().isEmpty();
    }

    // =========================================================
    // SAFE
    // =========================================================

    private String safe(
            String value
    ) {

        return value == null
                || value.isBlank()
                ? "Not specified"
                : value.trim();
    }

    // =========================================================
    // SAFE NULLABLE
    // =========================================================

    private String safeNullable(
            String value
    ) {

        if (!notBlank(value)) {
            return null;
        }

        return value.trim();
    }

    // =========================================================
    // REMOVE TRAILING SLASH
    // =========================================================

    private String removeTrailingSlash(
            String url
    ) {

        if (!notBlank(url)) {
            return "http://localhost:11434";
        }

        String value =
                url.trim();

        while (value.endsWith("/")) {

            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }

        return value;
    }

    // =========================================================
    // SAFE EXCEPTION
    // =========================================================

    private String safeExceptionMessage(
            Exception exception
    ) {

        if (exception == null) {
            return "Unknown error";
        }

        String message =
                exception.getMessage();

        if (!notBlank(message)) {

            return exception
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }

    // =========================================================
    // RESPONSE PREVIEW
    // =========================================================

    private String responsePreview(
            String response
    ) {

        if (!notBlank(response)) {
            return "[empty response]";
        }

        String cleaned =
                response
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .trim();

        int maxLength = 800;

        if (cleaned.length() > maxLength) {

            return cleaned.substring(
                    0,
                    maxLength
            ) + "...";
        }

        return cleaned;
    }

    // =========================================================
    // OLLAMA REQUEST
    // =========================================================

    private record OllamaRequest(
            String model,
            String prompt,
            boolean stream,
            String format,
            OllamaOptions options
    ) {
    }

    // =========================================================
    // OLLAMA OPTIONS
    // =========================================================

    private record OllamaOptions(
            double temperature,
            int num_predict
    ) {
    }

    // =========================================================
    // OLLAMA RESPONSE
    // =========================================================

    private record OllamaResponse(
            String model,
            String response,
            boolean done
    ) {
    }

    // =========================================================
    // GENERATED QUESTION
    // =========================================================

    private static class GeneratedQuestion {

        public String competency;

        public String question;

        public String optionA;

        public String optionB;

        public String optionC;

        public String optionD;

        public String correctAnswer;

        public String difficulty;

        public int marks = 1;

        public String explanation;
    }
}