
package com.capacitix.service;

import com.capacitix.entity.Assessment;
import com.capacitix.entity.AssessmentQuestion;
import com.capacitix.entity.Competency;
import com.capacitix.repository.AssessmentQuestionRepository;

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

    @Value("${groq.api-key:}")
    private String groqApiKey;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    @Value("${groq.timeout:300000}")
    private long groqTimeout;

    @Value("${groq.temperature:0.2}")
    private double groqTemperature;

    @Value("${groq.max-tokens:4096}")
    private int groqMaxTokens;

    private static final int MAX_QUESTION_COUNT = 50;

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

        factory.setConnectTimeout(
                Duration.ofMillis(15000)
        );

        factory.setReadTimeout(
                Duration.ofMillis(
                        Math.max(groqTimeout, 10000)
                )
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
        log.info("Read timeout: {} ms", groqTimeout);
        log.info("Strategy: SINGLE BATCH");
        log.info("Repair requests: 0");
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
                "Starting assessment generation. Questions={}, Competencies={}, Provider=Groq, Model={}",
                requiredCount,
                validCompetencies.size(),
                groqModel
        );

        // =================================================
        // ONE GROQ API REQUEST
        // =================================================

        String prompt = buildPrompt(
                assessment,
                validCompetencies,
                requiredCount
        );

        long requestStart = System.nanoTime();

        String response = callGroq(
                prompt,
                requiredCount
        );

        long requestTime =
                (System.nanoTime() - requestStart) / 1_000_000;

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
        // =================================================

        if (questions.size() != requiredCount) {

            throw generationFailure(
                    questions.size(),
                    requiredCount
            );
        }

        // =================================================
        // SAVE QUESTIONS
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
        log.info("Questions: {}", saved.size());
        log.info("Skipped: {}", skipped);
        log.info("Groq request time: {} ms", requestTime);
        log.info("Total time: {} ms", totalTime);
        log.info("========================================");

        return saved;
    }

    // =====================================================
    // TRANSACTIONAL DATABASE SAVE
    // =====================================================

    @Transactional(rollbackFor = Exception.class)
    public List<AssessmentQuestion> saveGeneratedQuestions(
            Assessment assessment,
            List<AssessmentQuestion> questions,
            int requiredCount
    ) {

        if (questions == null
                || questions.size() != requiredCount) {

            throw new IllegalArgumentException(
                    "Generated question count does not match."
            );
        }

        questionRepository.deleteByAssessment(assessment);

        List<AssessmentQuestion> saved =
                questionRepository.saveAll(questions);

        if (saved.size() != requiredCount) {

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
    // PROMPT
    // =====================================================

    private String buildPrompt(
            Assessment assessment,
            List<Competency> competencies,
            int count
    ) {

        String competencyNames = competencies.stream()
                .map(Competency::getName)
                .collect(Collectors.joining(", "));

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
                {
                  "questions": [
                    {
                      "competency": "Exact competency name",
                      "question": "Question text?",
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
                """.formatted(
                count,
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyNames,
                competencyInstruction,
                count
        );
    }

    // =====================================================
    // GROQ CHAT COMPLETIONS API
    // =====================================================

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
                                "You are an expert technical assessment creator. Return only valid JSON matching the requested structure. Do not add Markdown or extra text."
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

            JsonNode root =
                    objectMapper.readTree(rawResponse);

            if (root == null || !root.isObject()) {
                throw new RuntimeException(
                        "Invalid Groq HTTP response."
                );
            }

            if (root.has("error")
                    && !root.path("error").isNull()) {

                throw new RuntimeException(
                        "Groq API error: "
                                + root.path("error").path("message").asText(
                                        root.path("error").asText()
                                )
                );
            }

            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException(
                        "Groq returned no completion choices."
                );
            }

            JsonNode message =
                    choices.get(0).path("message");

            String generatedText =
                    message.path("content").asText("");

            if (!notBlank(generatedText)) {

                String finishReason =
                        choices.get(0).path("finish_reason").asText("");

                throw new RuntimeException(
                        "Groq returned empty generated content. Finish reason: "
                                + finishReason
                );
            }

            return generatedText;

        } catch (ResourceAccessException e) {

            throw new RuntimeException(
                    "Groq API request timed out or connection failed. "
                            + "Configured timeout: "
                            + groqTimeout + " ms.",
                    e
            );

        } catch (HttpStatusCodeException e) {

            String errorBody = e.getResponseBodyAsString();

            log.error(
                    "Groq API HTTP error. Status={}, Response={}",
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

    private List<GeneratedQuestion> parseQuestions(
            String response
    ) {

        try {

            String json = cleanJson(response);

            JsonNode root =
                    objectMapper.readTree(json);

            if (root == null || root.isNull()) {
                throw new RuntimeException(
                        "Empty JSON response."
                );
            }

            JsonNode array;

            if (root.isArray()) {

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

            for (JsonNode node : array) {

                if (!node.isObject()) {
                    continue;
                }

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
                        node,
                        "difficulty",
                        "level"
                );

                q.explanation = firstText(
                        node,
                        "explanation",
                        "reason"
                );

                questions.add(q);
            }

            return questions;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to parse Groq questions: "
                            + preview(response),
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
        }

        return json;
    }

    // =====================================================
    // QUESTION VALIDATION
    // =====================================================

    private String validateGeneratedQuestion(
            GeneratedQuestion q
    ) {

        if (q == null) {
            return "Empty question object.";
        }

        if (!notBlank(q.question)) {
            return "Missing question text.";
        }

        if (q.question.trim().length() < 12) {
            return "Question is too short.";
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
                    && (key.contains(known)
                    || known.contains(key))) {

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

        String upper =
                value.toUpperCase(Locale.ROOT);

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

        return switch (
                difficulty.trim().toUpperCase(Locale.ROOT)
        ) {

            case "BEGINNER", "EASY" ->
                    "BEGINNER";

            case "ADVANCED", "HARD" ->
                    "ADVANCED";

            default ->
                    "INTERMEDIATE";
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

        if (competencies == null
                || competencies.isEmpty()) {

            throw new IllegalArgumentException(
                    "Please select at least one competency."
            );
        }

        if (assessment.getQuestionCount() == null
                || assessment.getQuestionCount() <= 0
                || assessment.getQuestionCount()
                > MAX_QUESTION_COUNT) {

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
                        + ". Please try again or check your Groq API configuration and model availability."
        );
    }

    // =====================================================
    // JSON HELPERS
    // =====================================================

    private String firstText(
            JsonNode node,
            String... fields
    ) {

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

    private boolean sameText(
            String first,
            String second
    ) {

        return notBlank(first)
                && notBlank(second)
                && normalize(first).equals(normalize(second));
    }

    private boolean notBlank(String value) {

        return value != null
                && !value.trim().isEmpty();
    }

    private String safe(String value) {

        return notBlank(value)
                ? value.trim()
                : "Not specified";
    }

    private String safeNullable(String value) {

        return notBlank(value)
                ? value.trim()
                : null;
    }

    private String removeTrailingSlash(String url) {

        if (!notBlank(url)) {
            return "https://api.groq.com/openai/v1";
        }

        String result = url.trim();

        while (result.endsWith("/")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
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
