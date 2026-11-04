
package com.capacitix.service;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.Competency;
import com.capacitix.repository.AssessmentQuestionRepository;
<<<<<<< HEAD
=======

>>>>>>> personal/main
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    // =====================================================
    // GROQ CONFIGURATION
    // =====================================================

<<<<<<< HEAD
    // Supports Windows environment variable GROQ_API_KEY
    @Value("${GROQ_API_KEY:${groq.api.key:${groq.api-key:}}}")
=======
    @Value("${groq.api-key:}")
>>>>>>> personal/main
    private String groqApiKey;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    @Value("${groq.timeout:300000}")
    private long groqTimeout;

    @Value("${groq.temperature:0.2}")
    private double groqTemperature;

<<<<<<< HEAD
    @Value("${groq.max-tokens:8192}")
=======
    @Value("${groq.max-tokens:4096}")
>>>>>>> personal/main
    private int groqMaxTokens;

    private static final int MAX_QUESTION_COUNT = 50;

<<<<<<< HEAD
    private static final int EXTRA_QUESTIONS = 2;

    private static final int MAX_REPAIR_REQUESTS = 1;

=======
>>>>>>> personal/main
    private RestClient restClient;

    // =====================================================
    // INITIALIZATION
    // =====================================================

    @PostConstruct
    public void initializeRestClient() {

        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("GROQ_API_KEY is not configured.");
        }

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

<<<<<<< HEAD
        factory.setConnectTimeout(Duration.ofMillis(15000));

        factory.setReadTimeout(
                Duration.ofMillis(Math.max(groqTimeout, 10000))
=======
        factory.setConnectTimeout(
                Duration.ofMillis(15000)
        );

        factory.setReadTimeout(
                Duration.ofMillis(
                        Math.max(groqTimeout, 10000)
                )
>>>>>>> personal/main
        );

        restClient = RestClient.builder()
                .baseUrl(removeTrailingSlash(groqBaseUrl))
                .requestFactory(factory)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + groqApiKey
                )
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();

        log.info("========================================");
        log.info("AI QUESTION GENERATOR INITIALIZED");
        log.info("Provider: Groq Cloud API");
        log.info("Model: {}", groqModel);
        log.info("API URL: {}", sanitizeUrl(groqBaseUrl));
<<<<<<< HEAD
        log.info("Strategy: BATCH + ONE REPAIR REQUEST");
        log.info("Extra questions: {}", EXTRA_QUESTIONS);
        log.info("Maximum repair requests: {}", MAX_REPAIR_REQUESTS);
=======
        log.info("Read timeout: {} ms", groqTimeout);
        log.info("Strategy: SINGLE BATCH");
        log.info("Repair requests: 0");
>>>>>>> personal/main
        log.info("========================================");
    }

    // =====================================================
    // MAIN GENERATION METHOD
    // =====================================================

    public List<AssessmentQuestion> generateQuestions(
            Assessment assessment,
<<<<<<< HEAD
            List<Competency> competencies) {
=======
            List<Competency> competencies
    ) {
>>>>>>> personal/main

        long startTime = System.nanoTime();

        validateInput(assessment, competencies);

        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new RuntimeException(
                    "Groq API key is missing. Configure GROQ_API_KEY."
            );
        }

        int requiredCount = assessment.getQuestionCount();

<<<<<<< HEAD
=======
        // Remove duplicate competencies.
>>>>>>> personal/main
        Map<String, Competency> competencyMap =
                competencies.stream()
                        .filter(c -> c != null)
                        .filter(c -> notBlank(c.getName()))
                        .collect(Collectors.toMap(
                                c -> normalize(c.getName()),
                                c -> c,
                                (first, second) -> first,
                                LinkedHashMap::new
                        ));

        if (competencyMap.isEmpty()) {
            throw new IllegalArgumentException(
                    "No valid competencies selected."
            );
        }

        List<Competency> validCompetencies =
                new ArrayList<>(competencyMap.values());

        log.info(
<<<<<<< HEAD
                "Starting Groq generation. Required={}, Competencies={}, Model={}",
=======
                "Starting assessment generation. Questions={}, Competencies={}, Provider=Groq, Model={}",
>>>>>>> personal/main
                requiredCount,
                validCompetencies.size(),
                groqModel
        );

        // =================================================
<<<<<<< HEAD
        // INITIAL BATCH
        // Generate extra questions to handle invalid output.
        // =================================================

        int initialCount = Math.min(
                requiredCount + EXTRA_QUESTIONS,
                MAX_QUESTION_COUNT + EXTRA_QUESTIONS
        );

        String prompt = buildPrompt(
                assessment,
                validCompetencies,
                initialCount
=======
        // ONE GROQ API REQUEST
        // =================================================

        String prompt = buildPrompt(
                assessment,
                validCompetencies,
                requiredCount
>>>>>>> personal/main
        );

        long requestStart = System.nanoTime();

<<<<<<< HEAD
        String response = callGroq(prompt, initialCount);
=======
        String response = callGroq(
                prompt,
                requiredCount
        );
>>>>>>> personal/main

        long requestTime =
                (System.nanoTime() - requestStart) / 1_000_000;

<<<<<<< HEAD
        List<AssessmentQuestion> questions =
                collectValidQuestions(
                        parseQuestions(response),
                        competencyMap,
                        validCompetencies,
                        requiredCount,
                        1
                );

        log.info(
                "Initial batch completed. Valid={}/{}",
                questions.size(),
                requiredCount
        );

        // =================================================
        // AUTOMATIC REPAIR
        // Generate only the missing questions.
        // =================================================

        int repairRequests = 0;

        while (questions.size() < requiredCount
                && repairRequests < MAX_REPAIR_REQUESTS) {

            int missing = requiredCount - questions.size();

            log.info(
                    "Repair request {}. Missing questions={}",
                    repairRequests + 1,
                    missing
            );

            String repairPrompt = buildRepairPrompt(
                    assessment,
                    validCompetencies,
                    missing,
                    questions
            );

            long repairStart = System.nanoTime();

            String repairResponse = callGroq(
                    repairPrompt,
                    missing
            );

            requestTime +=
                    (System.nanoTime() - repairStart) / 1_000_000;

            List<AssessmentQuestion> repaired =
                    collectValidQuestions(
                            parseQuestions(repairResponse),
                            competencyMap,
                            validCompetencies,
                            requiredCount,
                            questions.size() + 1
                    );

            // Avoid duplicates against the original batch.
            Set<String> existingQuestions = questions.stream()
                    .map(q -> normalize(q.getQuestionText()))
                    .collect(Collectors.toSet());

            for (AssessmentQuestion question : repaired) {

                if (questions.size() >= requiredCount) {
                    break;
                }

                String key = normalize(question.getQuestionText());

                if (existingQuestions.add(key)) {
                    question.setQuestionOrder(questions.size() + 1);
                    questions.add(question);
                }
            }

            repairRequests++;
        }

        // =================================================
        // FINAL VALIDATION
=======
        log.info(
                "Groq response received in {} ms",
                requestTime
        );

        // =================================================
        // PARSE RESPONSE
        // =================================================

        List<GeneratedQuestion> generated =
                parseQuestions(response);

        log.info(
                "Groq returned {} questions. Required={}",
                generated.size(),
                requiredCount
        );

        if (generated.isEmpty()) {
            throw generationFailure(0, requiredCount);
        }

        // =================================================
        // VALIDATE AND COLLECT QUESTIONS
        // =================================================

        Set<String> questionKeys = new HashSet<>();

        List<AssessmentQuestion> questions =
                new ArrayList<>();

        int order = 1;
        int skipped = 0;

        for (GeneratedQuestion item : generated) {

            if (questions.size() >= requiredCount) {
                break;
            }

            String validationError =
                    validateGeneratedQuestion(item);

            if (validationError != null) {
                skipped++;

                log.warn(
                        "Skipping invalid AI question: {}",
                        validationError
                );

                continue;
            }

            Competency competency = findCompetency(
                    item.competency,
                    competencyMap
            );

            // Automatically assign when only one
            // competency is selected.
            if (competency == null
                    && validCompetencies.size() == 1) {

                competency = validCompetencies.get(0);
            }

            if (competency == null) {
                skipped++;

                log.warn(
                        "Skipping unknown competency: {}",
                        item.competency
                );

                continue;
            }

            item.correctAnswer = normalizeAnswer(
                    item.correctAnswer,
                    item
            );

            if (!isValidCorrectAnswer(item.correctAnswer)) {
                skipped++;

                log.warn(
                        "Skipping question with invalid correct answer."
                );

                continue;
            }

            item.question = item.question.trim();
            item.optionA = item.optionA.trim();
            item.optionB = item.optionB.trim();
            item.optionC = item.optionC.trim();
            item.optionD = item.optionD.trim();

            item.difficulty =
                    normalizeDifficulty(item.difficulty);

            String key = normalize(item.question);

            if (!questionKeys.add(key)) {
                skipped++;

                log.warn(
                        "Skipping duplicate question: {}",
                        item.question
                );

                continue;
            }

            AssessmentQuestion question =
                    AssessmentQuestion.builder()
                            .assessment(assessment)
                            .competency(competency)
                            .questionText(item.question)
                            .questionType("MCQ")
                            .optionA(item.optionA)
                            .optionB(item.optionB)
                            .optionC(item.optionC)
                            .optionD(item.optionD)
                            .correctAnswer(item.correctAnswer)
                            .difficulty(item.difficulty)
                            .marks(1)
                            .explanation(safeNullable(item.explanation))
                            .questionOrder(order++)
                            .active(true)
                            .build();

            questions.add(question);
        }

        log.info(
                "Valid questions collected: {}, Skipped: {}",
                questions.size(),
                skipped
        );

        // =================================================
        // CHECK COMPLETENESS
>>>>>>> personal/main
        // =================================================

        if (questions.size() != requiredCount) {

<<<<<<< HEAD
            log.error(
                    "Question generation incomplete. Generated={}, Required={}",
                    questions.size(),
                    requiredCount
            );

=======
>>>>>>> personal/main
            throw generationFailure(
                    questions.size(),
                    requiredCount
            );
        }

<<<<<<< HEAD
        // Ensure sequential question ordering.
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setQuestionOrder(i + 1);
        }

        // =================================================
        // DATABASE SAVE
=======
        // =================================================
        // SAVE QUESTIONS
>>>>>>> personal/main
        // =================================================

        List<AssessmentQuestion> saved =
                saveGeneratedQuestions(
                        assessment,
                        questions,
                        requiredCount
                );

        long totalTime =
                (System.nanoTime() - startTime) / 1_000_000;

        log.info("========================================");
        log.info("ASSESSMENT GENERATION SUCCESSFUL");
        log.info("Provider: Groq Cloud API");
<<<<<<< HEAD
        log.info("Model: {}", groqModel);
        log.info("Generated: {}", saved.size());
        log.info("Repair requests: {}", repairRequests);
=======
        log.info("Questions: {}", saved.size());
        log.info("Skipped: {}", skipped);
>>>>>>> personal/main
        log.info("Groq request time: {} ms", requestTime);
        log.info("Total time: {} ms", totalTime);
        log.info("========================================");

        return saved;
    }

    // =====================================================
<<<<<<< HEAD
    // COLLECT AND VALIDATE QUESTIONS
    // =====================================================

    private List<AssessmentQuestion> collectValidQuestions(
            List<GeneratedQuestion> generated,
            Map<String, Competency> competencyMap,
            List<Competency> validCompetencies,
            int requiredCount,
            int startingOrder) {

        List<AssessmentQuestion> questions = new ArrayList<>();

        Set<String> questionKeys = new HashSet<>();

        int order = startingOrder;

        for (GeneratedQuestion item : generated) {

            if (questions.size() >= requiredCount) {
                break;
            }

            String validationError =
                    validateGeneratedQuestion(item);

            if (validationError != null) {
                log.warn("Invalid AI question: {}", validationError);
                continue;
            }

            Competency competency = findCompetency(
                    item.competency,
                    competencyMap
            );

            // Automatically assign when one competency exists.
            if (competency == null && validCompetencies.size() == 1) {
                competency = validCompetencies.get(0);
            }

            if (competency == null) {
                log.warn(
                        "Unknown competency returned by Groq: {}",
                        item.competency
                );
                continue;
            }

            item.correctAnswer = normalizeAnswer(
                    item.correctAnswer,
                    item
            );

            if (!isValidCorrectAnswer(item.correctAnswer)) {
                log.warn("Invalid correct answer.");
                continue;
            }

            String key = normalize(item.question);

            if (!questionKeys.add(key)) {
                log.warn("Duplicate question skipped: {}", item.question);
                continue;
            }

            AssessmentQuestion question =
                    AssessmentQuestion.builder()
                            .competency(competency)
                            .questionText(item.question.trim())
                            .questionType("MCQ")
                            .optionA(item.optionA.trim())
                            .optionB(item.optionB.trim())
                            .optionC(item.optionC.trim())
                            .optionD(item.optionD.trim())
                            .correctAnswer(item.correctAnswer)
                            .difficulty(normalizeDifficulty(item.difficulty))
                            .marks(1)
                            .explanation(safeNullable(item.explanation))
                            .questionOrder(order++)
                            .active(true)
                            .build();

            questions.add(question);
        }

        return questions;
    }

    // =====================================================
=======
>>>>>>> personal/main
    // TRANSACTIONAL DATABASE SAVE
    // =====================================================

    @Transactional(rollbackFor = Exception.class)
    public List<AssessmentQuestion> saveGeneratedQuestions(
            Assessment assessment,
            List<AssessmentQuestion> questions,
<<<<<<< HEAD
            int requiredCount) {

        if (questions == null || questions.size() != requiredCount) {
=======
            int requiredCount
    ) {

        if (questions == null
                || questions.size() != requiredCount) {

>>>>>>> personal/main
            throw new IllegalArgumentException(
                    "Generated question count does not match."
            );
        }

<<<<<<< HEAD
        // Set assessment before saving.
        for (AssessmentQuestion question : questions) {
            question.setAssessment(assessment);
        }

=======
>>>>>>> personal/main
        questionRepository.deleteByAssessment(assessment);

        List<AssessmentQuestion> saved =
                questionRepository.saveAll(questions);

        if (saved.size() != requiredCount) {
<<<<<<< HEAD
=======

>>>>>>> personal/main
            throw new RuntimeException(
                    "Question saving failed. Expected "
                            + requiredCount
                            + ", saved " + saved.size()
            );
        }

        assessment.setQuestionCount(saved.size());

        return saved;
    }

    // =====================================================
<<<<<<< HEAD
    // INITIAL PROMPT
=======
    // PROMPT
>>>>>>> personal/main
    // =====================================================

    private String buildPrompt(
            Assessment assessment,
            List<Competency> competencies,
<<<<<<< HEAD
            int count) {
=======
            int count
    ) {
>>>>>>> personal/main

        String competencyNames = competencies.stream()
                .map(Competency::getName)
                .collect(Collectors.joining(", "));

<<<<<<< HEAD
        return """
                You are an expert technical assessment creator.

                Generate exactly %d unique technical MCQ questions.

                Assessment: %s
                Target Role: %s

                Allowed competencies:
                %s

                STRICT RULES:
                1. Generate exactly %d questions.
                2. Use only the listed competencies.
                3. Use the exact competency names.
                4. Generate technically accurate questions.
                5. Use simple, clear English.
                6. Do not repeat questions.
                7. Include exactly four different options.
                8. Only one option must be correct.
                9. correctAnswer must be A, B, C or D.
                10. difficulty must be BEGINNER, INTERMEDIATE or ADVANCED.
                11. marks must be 1.
                12. Every field must contain valid data.
                13. Do not leave any option empty.
                14. Do not include explanations.

                Return JSON only in this structure:

=======
        String competencyInstruction =
                competencies.size() == 1
                        ? "Use this competency for every question."
                        : "Assign each question to one listed competency. Use the exact competency name.";

        return """
                Generate exactly %d technical multiple-choice questions.

                Assessment: %s
                Role: %s
                Competencies: %s

                Rules:
                - %s
                - Use simple, clear English.
                - Generate different concepts and avoid repeated questions.
                - Include exactly four unique options per question.
                - Only one option must be correct.
                - correctAnswer must be A, B, C or D.
                - difficulty must be BEGINNER, INTERMEDIATE or ADVANCED.
                - marks must be 1.
                - Keep questions technically accurate.
                - Do not include explanations.
                - Return valid JSON only.
                - Do not use Markdown code fences.
                - Generate exactly %d questions.

                Required JSON structure:
>>>>>>> personal/main
                {
                  "questions": [
                    {
                      "competency": "Exact competency name",
                      "question": "Question text?",
<<<<<<< HEAD
                      "optionA": "First option",
                      "optionB": "Second option",
                      "optionC": "Third option",
                      "optionD": "Fourth option",
                      "correctAnswer": "A",
                      "difficulty": "INTERMEDIATE",
                      "marks": 1
                    }
                  ]
                }

                Return exactly %d question objects.
                Do not use Markdown.
                """.formatted(
                count,
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyNames,
                count,
                count
        );
    }

    // =====================================================
    // REPAIR PROMPT
    // =====================================================

    private String buildRepairPrompt(
            Assessment assessment,
            List<Competency> competencies,
            int missing,
            List<AssessmentQuestion> existingQuestions) {

        String competencyNames = competencies.stream()
                .map(Competency::getName)
                .collect(Collectors.joining(", "));

        String existing = existingQuestions.stream()
                .map(AssessmentQuestion::getQuestionText)
                .collect(Collectors.joining("\n"));

        return """
                You are an expert technical MCQ assessment creator.

                Generate exactly %d NEW replacement questions.

                Assessment: %s
                Target Role: %s

                Allowed competencies:
                %s

                Previously generated questions (DO NOT REPEAT):
                %s

                RULES:
                1. Generate exactly %d new questions.
                2. Never repeat the previous questions.
                3. Use only the allowed competencies.
                4. Use exact competency names.
                5. Include four unique options.
                6. Only one correct answer.
                7. correctAnswer must be A, B, C or D.
                8. difficulty must be BEGINNER, INTERMEDIATE or ADVANCED.
                9. marks must be 1.
                10. Every field must contain valid data.
                11. No empty options.
                12. No explanations.

                Return valid JSON only:

                {
                  "questions": [
                    {
                      "competency": "Exact competency name",
                      "question": "New question?",
=======
>>>>>>> personal/main
                      "optionA": "Option A",
                      "optionB": "Option B",
                      "optionC": "Option C",
                      "optionD": "Option D",
                      "correctAnswer": "A",
                      "difficulty": "INTERMEDIATE",
                      "marks": 1
                    }
                  ]
                }
<<<<<<< HEAD

                Return exactly %d questions.
                """.formatted(
                missing,
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyNames,
                existing,
                missing,
                missing
=======
                """.formatted(
                count,
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyNames,
                competencyInstruction,
                count
>>>>>>> personal/main
        );
    }

    // =====================================================
    // GROQ CHAT COMPLETIONS API
    // =====================================================

<<<<<<< HEAD
    private String callGroq(String prompt, int questionCount) {

        int tokenLimit = Math.max(
                2048,
                Math.min(
                        Math.max(groqMaxTokens, 2048),
                        Math.min(16384, 700 + questionCount * 350)
=======
    private String callGroq(
            String prompt,
            int questionCount
    ) {

        int tokenLimit = Math.max(
                512,
                Math.min(
                        Math.max(groqMaxTokens, 512),
                        Math.min(
                                8192,
                                512 + questionCount * 180
                        )
>>>>>>> personal/main
                )
        );

        double temperature = Math.max(
                0.0,
                Math.min(0.5, groqTemperature)
        );

        log.info(
<<<<<<< HEAD
                "Groq request: Model={}, Questions={}, MaxTokens={}",
                groqModel,
                questionCount,
                tokenLimit
=======
                "Groq request: Model={}, Questions={}, MaxTokens={}, Temperature={}",
                groqModel,
                questionCount,
                tokenLimit,
                temperature
>>>>>>> personal/main
        );

        Map<String, Object> request = Map.of(
                "model", groqModel,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content",
<<<<<<< HEAD
                                "You are a precise technical MCQ generator. Return valid JSON only. Follow the requested question count exactly. Never omit required fields."
=======
                                "You are an expert technical assessment creator. Return only valid JSON matching the requested structure. Do not add Markdown or extra text."
>>>>>>> personal/main
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "temperature", temperature,
                "max_completion_tokens", tokenLimit,
                "response_format", Map.of(
                        "type", "json_object"
                )
        );

        try {

            String rawResponse = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (!notBlank(rawResponse)) {
                throw new RuntimeException(
                        "Groq returned an empty HTTP response."
                );
            }

<<<<<<< HEAD
            JsonNode root = objectMapper.readTree(rawResponse);
=======
            JsonNode root =
                    objectMapper.readTree(rawResponse);
>>>>>>> personal/main

            if (root == null || !root.isObject()) {
                throw new RuntimeException(
                        "Invalid Groq HTTP response."
                );
            }

<<<<<<< HEAD
            if (root.has("error") && !root.path("error").isNull()) {
                throw new RuntimeException(
                        "Groq API error: "
                                + root.path("error").path("message")
                                .asText(root.path("error").asText())
=======
            if (root.has("error")
                    && !root.path("error").isNull()) {

                throw new RuntimeException(
                        "Groq API error: "
                                + root.path("error").path("message").asText(
                                        root.path("error").asText()
                                )
>>>>>>> personal/main
                );
            }

            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException(
                        "Groq returned no completion choices."
                );
            }

<<<<<<< HEAD
            JsonNode choice = choices.get(0);

            JsonNode message = choice.path("message");
=======
            JsonNode message =
                    choices.get(0).path("message");
>>>>>>> personal/main

            String generatedText =
                    message.path("content").asText("");

            if (!notBlank(generatedText)) {

<<<<<<< HEAD
                throw new RuntimeException(
                        "Groq returned empty content. Finish reason: "
                                + choice.path("finish_reason").asText("")
                );
            }

            log.info(
                    "Groq finish reason: {}",
                    choice.path("finish_reason").asText("unknown")
            );

=======
                String finishReason =
                        choices.get(0).path("finish_reason").asText("");

                throw new RuntimeException(
                        "Groq returned empty generated content. Finish reason: "
                                + finishReason
                );
            }

>>>>>>> personal/main
            return generatedText;

        } catch (ResourceAccessException e) {

            throw new RuntimeException(
<<<<<<< HEAD
                    "Groq API timed out or connection failed. Timeout: "
=======
                    "Groq API request timed out or connection failed. "
                            + "Configured timeout: "
>>>>>>> personal/main
                            + groqTimeout + " ms.",
                    e
            );

        } catch (HttpStatusCodeException e) {

            String errorBody = e.getResponseBodyAsString();

            log.error(
<<<<<<< HEAD
                    "Groq HTTP error. Status={}, Details={}",
=======
                    "Groq API HTTP error. Status={}, Response={}",
>>>>>>> personal/main
                    e.getStatusCode(),
                    preview(errorBody)
            );

            throw new RuntimeException(
                    "Groq API request failed. HTTP status: "
                            + e.getStatusCode()
                            + ". Details: "
                            + preview(errorBody),
                    e
            );

        } catch (RuntimeException e) {

            throw e;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to read Groq response: "
                            + safeExceptionMessage(e),
                    e
            );
        }
    }

    // =====================================================
    // JSON PARSING
    // =====================================================

<<<<<<< HEAD
    private List<GeneratedQuestion> parseQuestions(String response) {
=======
    private List<GeneratedQuestion> parseQuestions(
            String response
    ) {
>>>>>>> personal/main

        try {

            String json = cleanJson(response);

<<<<<<< HEAD
            JsonNode root = objectMapper.readTree(json);

            if (root == null || root.isNull()) {
                throw new RuntimeException("Empty JSON response.");
=======
            JsonNode root =
                    objectMapper.readTree(json);

            if (root == null || root.isNull()) {
                throw new RuntimeException(
                        "Empty JSON response."
                );
>>>>>>> personal/main
            }

            JsonNode array;

            if (root.isArray()) {
<<<<<<< HEAD
                array = root;
            } else if (root.isObject()
                    && root.path("questions").isArray()) {
                array = root.get("questions");
            } else {
                throw new RuntimeException(
                        "Missing questions array in Groq response."
                );
            }

            List<GeneratedQuestion> questions = new ArrayList<>();
=======

                array = root;

            } else if (root.isObject()
                    && root.path("questions").isArray()) {

                array = root.get("questions");

            } else {

                throw new RuntimeException(
                        "Missing questions array in AI response."
                );
            }

            List<GeneratedQuestion> questions =
                    new ArrayList<>();
>>>>>>> personal/main

            for (JsonNode node : array) {

                if (!node.isObject()) {
                    continue;
                }

<<<<<<< HEAD
                GeneratedQuestion q = new GeneratedQuestion();

                q.competency = firstText(
                        node, "competency", "skill", "competencyName"
                );

                q.question = firstText(
                        node, "question", "questionText", "text"
=======
                GeneratedQuestion q =
                        new GeneratedQuestion();

                q.competency = firstText(
                        node,
                        "competency",
                        "skill",
                        "competencyName"
                );

                q.question = firstText(
                        node,
                        "question",
                        "questionText",
                        "text"
>>>>>>> personal/main
                );

                q.optionA = firstText(
                        node, "optionA", "option_a", "A", "a"
                );

                q.optionB = firstText(
                        node, "optionB", "option_b", "B", "b"
                );

                q.optionC = firstText(
                        node, "optionC", "option_c", "C", "c"
                );

                q.optionD = firstText(
                        node, "optionD", "option_d", "D", "d"
                );

                JsonNode options = node.get("options");

                if (options != null
                        && options.isArray()
                        && options.size() >= 4) {

                    if (!notBlank(q.optionA)) {
                        q.optionA = nodeText(options.get(0));
                    }

                    if (!notBlank(q.optionB)) {
                        q.optionB = nodeText(options.get(1));
                    }

                    if (!notBlank(q.optionC)) {
                        q.optionC = nodeText(options.get(2));
                    }

                    if (!notBlank(q.optionD)) {
                        q.optionD = nodeText(options.get(3));
                    }
                }

                q.correctAnswer = firstText(
                        node,
                        "correctAnswer",
                        "correct_answer",
                        "answer",
                        "correctOption"
                );

                q.difficulty = firstText(
<<<<<<< HEAD
                        node, "difficulty", "level"
                );

                q.explanation = firstText(
                        node, "explanation", "reason"
=======
                        node,
                        "difficulty",
                        "level"
                );

                q.explanation = firstText(
                        node,
                        "explanation",
                        "reason"
>>>>>>> personal/main
                );

                questions.add(q);
            }

            return questions;

        } catch (Exception e) {

            throw new RuntimeException(
<<<<<<< HEAD
                    "Unable to parse Groq questions: " + preview(response),
=======
                    "Unable to parse Groq questions: "
                            + preview(response),
>>>>>>> personal/main
                    e
            );
        }
    }

    // =====================================================
    // JSON CLEANING
    // =====================================================

    private String cleanJson(String response) {

        if (!notBlank(response)) {
            return "";
        }

        String json = response.trim();

        if (json.startsWith("```")) {

            int newline = json.indexOf('\n');

            if (newline >= 0) {
                json = json.substring(newline + 1);
            }

            int fence = json.lastIndexOf("```");

            if (fence >= 0) {
                json = json.substring(0, fence);
            }
        }

        json = json.trim();

        int start = json.indexOf('{');
<<<<<<< HEAD
        int end = json.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return json.substring(start, end + 1).trim();
        }

        start = json.indexOf('[');
        end = json.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return json.substring(start, end + 1).trim();
=======

        if (start >= 0) {

            int end = json.lastIndexOf('}');

            if (end > start) {
                return json.substring(start, end + 1).trim();
            }
        }

        start = json.indexOf('[');

        if (start >= 0) {

            int end = json.lastIndexOf(']');

            if (end > start) {
                return json.substring(start, end + 1).trim();
            }
>>>>>>> personal/main
        }

        return json;
    }

    // =====================================================
    // QUESTION VALIDATION
    // =====================================================

<<<<<<< HEAD
    private String validateGeneratedQuestion(GeneratedQuestion q) {
=======
    private String validateGeneratedQuestion(
            GeneratedQuestion q
    ) {
>>>>>>> personal/main

        if (q == null) {
            return "Empty question object.";
        }

<<<<<<< HEAD
        if (!notBlank(q.question) || q.question.trim().length() < 12) {
            return "Missing or too-short question text.";
=======
        if (!notBlank(q.question)) {
            return "Missing question text.";
        }

        if (q.question.trim().length() < 12) {
            return "Question is too short.";
>>>>>>> personal/main
        }

        if (!notBlank(q.optionA)
                || !notBlank(q.optionB)
                || !notBlank(q.optionC)
                || !notBlank(q.optionD)) {
<<<<<<< HEAD
=======

>>>>>>> personal/main
            return "One or more options are missing.";
        }

        Set<String> options = new HashSet<>();

        options.add(normalize(q.optionA));
        options.add(normalize(q.optionB));
        options.add(normalize(q.optionC));
        options.add(normalize(q.optionD));

        if (options.size() != 4) {
            return "Options are not unique.";
        }

        if (!notBlank(q.correctAnswer)) {
            return "Correct answer is missing.";
        }

        return null;
    }

    // =====================================================
    // COMPETENCY MATCHING
    // =====================================================

    private Competency findCompetency(
            String aiCompetency,
<<<<<<< HEAD
            Map<String, Competency> competencyMap) {
=======
            Map<String, Competency> competencyMap
    ) {
>>>>>>> personal/main

        if (!notBlank(aiCompetency)) {
            return null;
        }

        String key = normalize(aiCompetency);

        Competency exact = competencyMap.get(key);

        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, Competency> entry
                : competencyMap.entrySet()) {

            String known = entry.getKey();

            if (key.length() >= 5
                    && known.length() >= 5
<<<<<<< HEAD
                    && (key.contains(known) || known.contains(key))) {
=======
                    && (key.contains(known)
                    || known.contains(key))) {
>>>>>>> personal/main

                return entry.getValue();
            }
        }

        return null;
    }

    // =====================================================
    // ANSWER NORMALIZATION
    // =====================================================

    private String normalizeAnswer(
            String answer,
<<<<<<< HEAD
            GeneratedQuestion q) {
=======
            GeneratedQuestion q
    ) {
>>>>>>> personal/main

        if (!notBlank(answer)) {
            return "";
        }

        String value = answer.trim();

<<<<<<< HEAD
        String upper = value.toUpperCase(Locale.ROOT);
=======
        String upper =
                value.toUpperCase(Locale.ROOT);
>>>>>>> personal/main

        if (upper.matches("^(A|B|C|D)$")) {
            return upper;
        }

        if (upper.matches("^(OPTION\\s*)?A[).:\\s-].*")) {
            return "A";
        }

        if (upper.matches("^(OPTION\\s*)?B[).:\\s-].*")) {
            return "B";
        }

        if (upper.matches("^(OPTION\\s*)?C[).:\\s-].*")) {
            return "C";
        }

        if (upper.matches("^(OPTION\\s*)?D[).:\\s-].*")) {
            return "D";
        }

        if (sameText(value, q.optionA)) return "A";
        if (sameText(value, q.optionB)) return "B";
        if (sameText(value, q.optionC)) return "C";
        if (sameText(value, q.optionD)) return "D";

        return "";
    }

    private boolean isValidCorrectAnswer(String answer) {
<<<<<<< HEAD
=======

>>>>>>> personal/main
        return "A".equals(answer)
                || "B".equals(answer)
                || "C".equals(answer)
                || "D".equals(answer);
    }

    // =====================================================
    // DIFFICULTY NORMALIZATION
    // =====================================================

    private String normalizeDifficulty(String difficulty) {

        if (!notBlank(difficulty)) {
            return "INTERMEDIATE";
        }

<<<<<<< HEAD
        return switch (difficulty.trim().toUpperCase(Locale.ROOT)) {

            case "BEGINNER", "EASY" -> "BEGINNER";

            case "ADVANCED", "HARD" -> "ADVANCED";

            default -> "INTERMEDIATE";
=======
        return switch (
                difficulty.trim().toUpperCase(Locale.ROOT)
        ) {

            case "BEGINNER", "EASY" ->
                    "BEGINNER";

            case "ADVANCED", "HARD" ->
                    "ADVANCED";

            default ->
                    "INTERMEDIATE";
>>>>>>> personal/main
        };
    }

    // =====================================================
    // INPUT VALIDATION
    // =====================================================

    private void validateInput(
            Assessment assessment,
<<<<<<< HEAD
            List<Competency> competencies) {
=======
            List<Competency> competencies
    ) {
>>>>>>> personal/main

        if (assessment == null) {
            throw new IllegalArgumentException(
                    "Assessment is required."
            );
        }

<<<<<<< HEAD
        if (competencies == null || competencies.isEmpty()) {
=======
        if (competencies == null
                || competencies.isEmpty()) {

>>>>>>> personal/main
            throw new IllegalArgumentException(
                    "Please select at least one competency."
            );
        }

        if (assessment.getQuestionCount() == null
                || assessment.getQuestionCount() <= 0
<<<<<<< HEAD
                || assessment.getQuestionCount() > MAX_QUESTION_COUNT) {
=======
                || assessment.getQuestionCount()
                > MAX_QUESTION_COUNT) {
>>>>>>> personal/main

            throw new IllegalArgumentException(
                    "Question count must be between 1 and "
                            + MAX_QUESTION_COUNT
            );
        }
    }

    // =====================================================
    // GENERATION FAILURE
    // =====================================================

    private RuntimeException generationFailure(
            int generated,
<<<<<<< HEAD
            int required) {
=======
            int required
    ) {
>>>>>>> personal/main

        return new RuntimeException(
                "AI could not generate enough valid questions. "
                        + "Generated " + generated
                        + " of " + required
<<<<<<< HEAD
                        + ". Please try again. Check the application logs "
                        + "for Groq response and validation details."
=======
                        + ". Please try again or check your Groq API configuration and model availability."
>>>>>>> personal/main
        );
    }

    // =====================================================
    // JSON HELPERS
    // =====================================================

<<<<<<< HEAD
    private String firstText(JsonNode node, String... fields) {
=======
    private String firstText(
            JsonNode node,
            String... fields
    ) {
>>>>>>> personal/main

        for (String field : fields) {

            JsonNode value = node.get(field);

            if (value != null && !value.isNull()) {

                String text = nodeText(value);

                if (notBlank(text)) {
                    return text;
                }
            }
        }

        return "";
    }

    private String nodeText(JsonNode node) {

        if (node == null || node.isNull()) {
            return "";
        }

        return node.isTextual()
                ? node.asText().trim()
                : node.toString().trim();
    }

    // =====================================================
    // COMMON HELPERS
    // =====================================================

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

<<<<<<< HEAD
    private boolean sameText(String first, String second) {
=======
    private boolean sameText(
            String first,
            String second
    ) {
>>>>>>> personal/main

        return notBlank(first)
                && notBlank(second)
                && normalize(first).equals(normalize(second));
    }

    private boolean notBlank(String value) {

<<<<<<< HEAD
        return value != null && !value.trim().isEmpty();
=======
        return value != null
                && !value.trim().isEmpty();
>>>>>>> personal/main
    }

    private String safe(String value) {

<<<<<<< HEAD
        return notBlank(value) ? value.trim() : "Not specified";
=======
        return notBlank(value)
                ? value.trim()
                : "Not specified";
>>>>>>> personal/main
    }

    private String safeNullable(String value) {

<<<<<<< HEAD
        return notBlank(value) ? value.trim() : null;
=======
        return notBlank(value)
                ? value.trim()
                : null;
>>>>>>> personal/main
    }

    private String removeTrailingSlash(String url) {

        if (!notBlank(url)) {
            return "https://api.groq.com/openai/v1";
        }

        String result = url.trim();

        while (result.endsWith("/")) {
<<<<<<< HEAD
            result = result.substring(0, result.length() - 1);
=======
            result = result.substring(
                    0,
                    result.length() - 1
            );
>>>>>>> personal/main
        }

        return result;
    }

    private String sanitizeUrl(String url) {

        if (!notBlank(url)) {
            return "Not configured";
        }

        return removeTrailingSlash(url)
                .replaceAll(
                        "(?i)(https?://)[^/@]+:[^/@]+@",
                        "$1***@"
                );
    }

    private String safeExceptionMessage(Exception e) {

        if (e == null || !notBlank(e.getMessage())) {
            return e == null
                    ? "Unknown error"
                    : e.getClass().getSimpleName();
        }

        return e.getMessage();
    }

    private String preview(String value) {

        if (!notBlank(value)) {
            return "[empty response]";
        }

        String result = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();

        return result.length() > 500
                ? result.substring(0, 500) + "..."
                : result;
    }

    // =====================================================
    // GENERATED QUESTION MODEL
    // =====================================================

    private static class GeneratedQuestion {

        public String competency;

        public String question;

        public String optionA;
        public String optionB;
        public String optionC;
        public String optionD;

        public String correctAnswer;

        public String difficulty;

        public String explanation;
    }
}
