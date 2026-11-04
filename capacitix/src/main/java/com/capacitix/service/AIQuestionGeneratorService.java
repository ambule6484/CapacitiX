
package com.capacitix.service;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.Competency;
import com.capacitix.repository.AssessmentQuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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

    private final EntityManagerFactory entityManagerFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    // =====================================================
    // GROQ CONFIGURATION
    // =====================================================

    @Value("${GROQ_API_KEY:${groq.api.key:${groq.api-key:}}}")
    private String groqApiKey;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    @Value("${groq.timeout:300000}")
    private long groqTimeout;

    @Value("${groq.temperature:0.2}")
    private double groqTemperature;

    @Value("${groq.max-tokens:8192}")
    private int groqMaxTokens;

    private static final int MAX_QUESTION_COUNT = 50;
    private static final int EXTRA_QUESTIONS = 2;
    private static final int MAX_REPAIR_REQUESTS = 1;

    private RestClient restClient;

    // =====================================================
    // INITIALIZATION
    // =====================================================

    @PostConstruct
    public void initializeRestClient() {

        /*
         * IMPORTANT:
         * Explicitly use JpaTransactionManager associated
         * with the same EntityManagerFactory as repositories.
         *
         * This prevents database delete/save operations
         * from running without an active JPA transaction.
         */

        JpaTransactionManager jpaTransactionManager =
                new JpaTransactionManager(entityManagerFactory);

        jpaTransactionManager.afterPropertiesSet();

        transactionTemplate =
                new TransactionTemplate(jpaTransactionManager);

        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED
        );

        log.info("JPA TransactionTemplate initialized.");

        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("GROQ_API_KEY is not configured.");
        }

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofMillis(15000));

        factory.setReadTimeout(
                Duration.ofMillis(Math.max(groqTimeout, 10000))
        );

        restClient = RestClient.builder()
                .baseUrl(removeTrailingSlash(groqBaseUrl))
                .requestFactory(factory)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + (groqApiKey == null ? "" : groqApiKey)
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
        log.info("Read timeout: {} ms", groqTimeout);
        log.info("Strategy: BATCH + ONE REPAIR REQUEST");
        log.info("Extra questions: {}", EXTRA_QUESTIONS);
        log.info("Maximum repair requests: {}", MAX_REPAIR_REQUESTS);
        log.info("Transaction manager: JpaTransactionManager");
        log.info("========================================");
    }

    // =====================================================
    // MAIN GENERATION METHOD
    // =====================================================

    public List<AssessmentQuestion> generateQuestions(
            Assessment assessment,
            List<Competency> competencies
    ) {

        long startTime = System.nanoTime();

        validateInput(assessment, competencies);

        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new RuntimeException(
                    "Groq API key is missing. Configure GROQ_API_KEY."
            );
        }

        int requiredCount = assessment.getQuestionCount();

        // Remove duplicate competencies.

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
                "Starting Groq generation. Required={}, Competencies={}, Model={}",
                requiredCount,
                validCompetencies.size(),
                groqModel
        );

        // =================================================
        // INITIAL BATCH
        // =================================================

        int initialCount = Math.min(
                requiredCount + EXTRA_QUESTIONS,
                MAX_QUESTION_COUNT + EXTRA_QUESTIONS
        );

        String prompt = buildPrompt(
                assessment,
                validCompetencies,
                initialCount
        );

        long requestStart = System.nanoTime();

        String response = callGroq(prompt, initialCount);

        long requestTime =
                (System.nanoTime() - requestStart) / 1_000_000;

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
        // =================================================

        if (questions.size() != requiredCount) {

            log.error(
                    "Question generation incomplete. Generated={}, Required={}",
                    questions.size(),
                    requiredCount
            );

            throw generationFailure(
                    questions.size(),
                    requiredCount
            );
        }

        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setQuestionOrder(i + 1);
        }

        // =================================================
        // DATABASE SAVE
        // =================================================

        log.info(
                "Starting JPA database transaction for assessment questions."
        );

        List<AssessmentQuestion> saved =
                transactionTemplate.execute(status -> {

                    try {

                        return saveGeneratedQuestionsInTransaction(
                                assessment,
                                questions,
                                requiredCount
                        );

                    } catch (RuntimeException e) {

                        status.setRollbackOnly();

                        log.error(
                                "Assessment question transaction failed: {}",
                                e.getMessage(),
                                e
                        );

                        throw e;
                    }
                });

        if (saved == null || saved.size() != requiredCount) {

            throw new RuntimeException(
                    "Assessment question database transaction failed."
            );
        }

        long totalTime =
                (System.nanoTime() - startTime) / 1_000_000;

        log.info("========================================");
        log.info("ASSESSMENT GENERATION SUCCESSFUL");
        log.info("Provider: Groq Cloud API");
        log.info("Model: {}", groqModel);
        log.info("Generated: {}", saved.size());
        log.info("Repair requests: {}", repairRequests);
        log.info("Groq request time: {} ms", requestTime);
        log.info("Total time: {} ms", totalTime);
        log.info("========================================");

        return saved;
    }

    // =====================================================
    // TRANSACTIONAL DATABASE SAVE
    // =====================================================

    private List<AssessmentQuestion> saveGeneratedQuestionsInTransaction(
            Assessment assessment,
            List<AssessmentQuestion> questions,
            int requiredCount
    ) {

        if (!org.springframework.transaction.support
                .TransactionSynchronizationManager
                .isActualTransactionActive()) {

            throw new IllegalStateException(
                    "No active JPA transaction found before saving questions."
            );
        }

        if (assessment == null || assessment.getId() == null) {
            throw new IllegalArgumentException(
                    "Assessment must have a valid database ID."
            );
        }

        if (questions == null || questions.size() != requiredCount) {
            throw new IllegalArgumentException(
                    "Generated question count does not match."
            );
        }

        /*
         * Load a managed assessment entity using the current
         * transaction's EntityManager.
         */

        Assessment managedAssessment =
                entityManager.find(Assessment.class, assessment.getId());

        if (managedAssessment == null) {
            throw new IllegalArgumentException(
                    "Assessment not found. ID: " + assessment.getId()
            );
        }

        log.info(
                "Preparing database save. Assessment ID={}, Questions={}",
                managedAssessment.getId(),
                questions.size()
        );

        // Associate questions with the managed assessment.

        for (AssessmentQuestion question : questions) {
            question.setAssessment(managedAssessment);
        }

        /*
         * Delete old questions inside the active JPA transaction.
         */

        log.info(
                "Deleting existing questions for assessment ID={}",
                managedAssessment.getId()
        );

        questionRepository.deleteByAssessment(managedAssessment);

        // Execute DELETE before INSERT.

        questionRepository.flush();

        log.info("Old questions deleted successfully.");

        // Save newly generated questions.

        List<AssessmentQuestion> saved =
                questionRepository.saveAll(questions);

        // Flush INSERT statements to detect database errors.

        questionRepository.flush();

        if (saved.size() != requiredCount) {

            throw new RuntimeException(
                    "Question saving failed. Expected "
                            + requiredCount
                            + ", saved "
                            + saved.size()
            );
        }

        // Update the managed assessment.

        managedAssessment.setQuestionCount(saved.size());

        entityManager.flush();

        log.info(
                "Database save completed. Assessment ID={}, Saved={}",
                managedAssessment.getId(),
                saved.size()
        );

        return saved;
    }

    // =====================================================
    // COLLECT AND VALIDATE QUESTIONS
    // =====================================================

    private List<AssessmentQuestion> collectValidQuestions(
            List<GeneratedQuestion> generated,
            Map<String, Competency> competencyMap,
            List<Competency> validCompetencies,
            int requiredCount,
            int startingOrder
    ) {

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
                            .assessment(null)
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
    // INITIAL PROMPT
    // =====================================================

    private String buildPrompt(
            Assessment assessment,
            List<Competency> competencies,
            int count
    ) {

        String competencyNames = competencies.stream()
                .map(Competency::getName)
                .collect(Collectors.joining(", "));

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

                {
                  "questions": [
                    {
                      "competency": "Exact competency name",
                      "question": "Question text?",
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
            List<AssessmentQuestion> existingQuestions
    ) {

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

                Return exactly %d questions.
                """.formatted(
                missing,
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyNames,
                existing,
                missing,
                missing
        );
    }

    // =====================================================
    // GROQ CHAT COMPLETIONS API
    // =====================================================

    private String callGroq(String prompt, int questionCount) {

        int tokenLimit = Math.max(
                2048,
                Math.min(
                        Math.max(groqMaxTokens, 2048),
                        Math.min(16384, 700 + questionCount * 350)
                )
        );

        double temperature = Math.max(
                0.0,
                Math.min(0.5, groqTemperature)
        );

        log.info(
                "Groq request: Model={}, Questions={}, MaxTokens={}, Temperature={}",
                groqModel,
                questionCount,
                tokenLimit,
                temperature
        );

        Map<String, Object> request = Map.of(
                "model", groqModel,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content",
                                "You are a precise technical MCQ generator. Return valid JSON only. Follow the requested question count exactly. Never omit required fields."
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

            JsonNode root = objectMapper.readTree(rawResponse);

            if (root == null || !root.isObject()) {
                throw new RuntimeException(
                        "Invalid Groq HTTP response."
                );
            }

            if (root.has("error") && !root.path("error").isNull()) {
                throw new RuntimeException(
                        "Groq API error: "
                                + root.path("error").path("message")
                                .asText(root.path("error").asText())
                );
            }

            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException(
                        "Groq returned no completion choices."
                );
            }

            JsonNode choice = choices.get(0);

            JsonNode message = choice.path("message");

            String generatedText =
                    message.path("content").asText("");

            if (!notBlank(generatedText)) {
                throw new RuntimeException(
                        "Groq returned empty content. Finish reason: "
                                + choice.path("finish_reason").asText("")
                );
            }

            log.info(
                    "Groq finish reason: {}",
                    choice.path("finish_reason").asText("unknown")
            );

            return generatedText;

        } catch (ResourceAccessException e) {

            throw new RuntimeException(
                    "Groq API timed out or connection failed. Timeout: "
                            + groqTimeout + " ms.",
                    e
            );

        } catch (HttpStatusCodeException e) {

            String errorBody = e.getResponseBodyAsString();

            log.error(
                    "Groq HTTP error. Status={}, Details={}",
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

    private List<GeneratedQuestion> parseQuestions(String response) {

        try {

            String json = cleanJson(response);

            JsonNode root = objectMapper.readTree(json);

            if (root == null || root.isNull()) {
                throw new RuntimeException("Empty JSON response.");
            }

            JsonNode array;

            if (root.isArray()) {
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

            for (JsonNode node : array) {

                if (!node.isObject()) {
                    continue;
                }

                GeneratedQuestion q = new GeneratedQuestion();

                q.competency = firstText(
                        node, "competency", "skill", "competencyName"
                );

                q.question = firstText(
                        node, "question", "questionText", "text"
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
                        node, "difficulty", "level"
                );

                q.explanation = firstText(
                        node, "explanation", "reason"
                );

                questions.add(q);
            }

            return questions;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to parse Groq questions: " + preview(response),
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
        int end = json.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return json.substring(start, end + 1).trim();
        }

        start = json.indexOf('[');
        end = json.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return json.substring(start, end + 1).trim();
        }

        return json;
    }

    // =====================================================
    // QUESTION VALIDATION
    // =====================================================

    private String validateGeneratedQuestion(GeneratedQuestion q) {

        if (q == null) {
            return "Empty question object.";
        }

        if (!notBlank(q.question) || q.question.trim().length() < 12) {
            return "Missing or too-short question text.";
        }

        if (!notBlank(q.optionA)
                || !notBlank(q.optionB)
                || !notBlank(q.optionC)
                || !notBlank(q.optionD)) {

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
            Map<String, Competency> competencyMap
    ) {

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
                    && (key.contains(known) || known.contains(key))) {

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
            GeneratedQuestion q
    ) {

        if (!notBlank(answer)) {
            return "";
        }

        String value = answer.trim();

        String upper = value.toUpperCase(Locale.ROOT);

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

        return switch (difficulty.trim().toUpperCase(Locale.ROOT)) {

            case "BEGINNER", "EASY" -> "BEGINNER";

            case "ADVANCED", "HARD" -> "ADVANCED";

            default -> "INTERMEDIATE";
        };
    }

    // =====================================================
    // INPUT VALIDATION
    // =====================================================

    private void validateInput(
            Assessment assessment,
            List<Competency> competencies
    ) {

        if (assessment == null) {
            throw new IllegalArgumentException(
                    "Assessment is required."
            );
        }

        if (competencies == null || competencies.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select at least one competency."
            );
        }

        if (assessment.getQuestionCount() == null
                || assessment.getQuestionCount() <= 0
                || assessment.getQuestionCount() > MAX_QUESTION_COUNT) {

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
            int required
    ) {

        return new RuntimeException(
                "AI could not generate enough valid questions. "
                        + "Generated " + generated
                        + " of " + required
                        + ". Please try again. Check the application logs "
                        + "for Groq response and validation details."
        );
    }

    // =====================================================
    // JSON HELPERS
    // =====================================================

    private String firstText(JsonNode node, String... fields) {

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

    private boolean sameText(String first, String second) {

        return notBlank(first)
                && notBlank(second)
                && normalize(first).equals(normalize(second));
    }

    private boolean notBlank(String value) {

        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {

        return notBlank(value) ? value.trim() : "Not specified";
    }

    private String safeNullable(String value) {

        return notBlank(value) ? value.trim() : null;
    }

    private String removeTrailingSlash(String url) {

        if (!notBlank(url)) {
            return "https://api.groq.com/openai/v1";
        }

        String result = url.trim();

        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
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
