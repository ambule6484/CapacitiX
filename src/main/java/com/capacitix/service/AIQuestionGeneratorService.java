
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

    @Value("${GROQ_API_KEY:${groq.api.key:}}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    @Value("${groq.assessment.timeout:180000}")
    private long groqTimeout;

    @Value("${groq.connect-timeout:10000}")
    private long connectTimeout;

    @Value("${groq.assessment.temperature:0.2}")
    private double groqTemperature;

    // IMPORTANT: Read assessment-specific token property.
    // Previously this service read groq.max-tokens instead.
    @Value("${groq.assessment.max-tokens:4096}")
    private int groqAssessmentMaxTokens;

    private static final int MAX_QUESTION_COUNT = 50;

    private RestClient restClient;

    // =====================================================
    // INITIALIZATION
    // =====================================================

    @PostConstruct
    public void initializeRestClient() {

        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn(
                    "GROQ_API_KEY is not configured. AI question generation will not work."
            );
        }

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                Duration.ofMillis(Math.max(connectTimeout, 1000))
        );

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
        log.info("Provider: Groq");
        log.info("Model: {}", groqModel);
        log.info("API URL: {}", groqBaseUrl);
        log.info("Connect timeout: {} ms", connectTimeout);
        log.info("Assessment timeout: {} ms", groqTimeout);
        log.info("Assessment max completion tokens: {}", groqAssessmentMaxTokens);
        log.info("Assessment temperature: {}", groqTemperature);
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
            throw new IllegalStateException(
                    "Groq API key is missing. Configure GROQ_API_KEY."
            );
        }

        int requiredCount = assessment.getQuestionCount();

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
                "Starting assessment generation. Questions={}, Competencies={}, Model={}",
                requiredCount,
                validCompetencies.size(),
                groqModel
        );

        String prompt = buildPrompt(
                assessment,
                validCompetencies,
                requiredCount
        );

        long requestStart = System.nanoTime();

        String response = callGroq(prompt, requiredCount);

        long requestTime =
                (System.nanoTime() - requestStart) / 1_000_000;

        log.info("Groq response received in {} ms", requestTime);

        // =================================================
        // PARSE RESPONSE
        // =================================================

        List<GeneratedQuestion> generated = parseQuestions(response);

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

        List<AssessmentQuestion> questions = new ArrayList<>();

        int order = 1;
        int skipped = 0;

        for (GeneratedQuestion item : generated) {

            if (questions.size() >= requiredCount) {
                break;
            }

            String validationError = validateGeneratedQuestion(item);

            if (validationError != null) {
                skipped++;
                log.warn("Skipping invalid AI question: {}", validationError);
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
                skipped++;
                log.warn("Skipping unknown competency: {}", item.competency);
                continue;
            }

            item.correctAnswer = normalizeAnswer(
                    item.correctAnswer,
                    item
            );

            if (!isValidCorrectAnswer(item.correctAnswer)) {
                skipped++;
                log.warn("Skipping question with invalid correct answer.");
                continue;
            }

            item.question = item.question.trim();
            item.optionA = item.optionA.trim();
            item.optionB = item.optionB.trim();
            item.optionC = item.optionC.trim();
            item.optionD = item.optionD.trim();

            item.difficulty = normalizeDifficulty(item.difficulty);

            String key = normalize(item.question);

            if (!questionKeys.add(key)) {
                skipped++;
                log.warn("Skipping duplicate question: {}", item.question);
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
        log.info("Provider: Groq");
        log.info("Questions: {}", saved.size());
        log.info("Skipped: {}", skipped);
        log.info("Groq request time: {} ms", requestTime);
        log.info("Total time: {} ms", totalTime);
        log.info("========================================");

        return saved;
    }

    // =====================================================
    // DATABASE SAVE
    // =====================================================

    @Transactional(rollbackFor = Exception.class)
    public List<AssessmentQuestion> saveGeneratedQuestions(
            Assessment assessment,
            List<AssessmentQuestion> questions,
            int requiredCount
    ) {

        if (questions == null || questions.size() != requiredCount) {
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
                        : "Assign each question to one listed competency.";

        return """
                Generate exactly %d technical multiple-choice questions.

                Assessment: %s
                Target role: %s
                Allowed competencies: %s

                REQUIREMENTS:
                1. Generate exactly %d questions.
                2. %s
                3. Use simple, clear English.
                4. Questions must test different technical concepts.
                5. Every question must have exactly four options.
                6. All four options must be different.
                7. Only one option must be correct.
                8. correctAnswer must contain only A, B, C or D.
                9. difficulty must be BEGINNER, INTERMEDIATE or ADVANCED.
                10. marks must always be the number 1.
                11. Do not include explanations.
                12. The competency value must exactly match one of the allowed competency names.
                13. Do not use Markdown or code fences.
                14. Do not add comments or text outside the JSON.
                15. Return one complete, syntactically valid JSON object.
                16. Do not stop before closing every JSON object and array.

                REQUIRED JSON STRUCTURE:
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

                Return exactly %d question objects inside the questions array.
                """.formatted(
                count,
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyNames,
                count,
                competencyInstruction,
                count
        );
    }

    // =====================================================
    // GROQ API
    // =====================================================

    private String callGroq(
            String prompt,
            int questionCount
    ) {

        // Scale the token budget with question count.
        // For 10 questions and a configured limit of 4096,
        // this will use 4096 completion tokens.
        int configuredLimit = Math.max(
                2048,
                groqAssessmentMaxTokens
        );

        int calculatedLimit = 1024 + (questionCount * 350);

        int tokenLimit = Math.min(
                configuredLimit,
                calculatedLimit
        );

        double temperature = Math.max(
                0.0,
                Math.min(0.5, groqTemperature)
        );

        log.info(
                "Groq request: Model={}, Questions={}, MaxCompletionTokens={}, Temperature={}",
                groqModel,
                questionCount,
                tokenLimit,
                temperature
        );

        Map<String, Object> systemMessage = Map.of(
                "role", "system",
                "content",
                "You are a professional technical assessment question generator. "
                        + "Return exactly the requested number of questions as one "
                        + "complete valid JSON object. Never return incomplete JSON."
        );

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", prompt
        );

        Map<String, Object> request = new LinkedHashMap<>();

        request.put("model", groqModel);
        request.put("messages", List.of(systemMessage, userMessage));
        request.put("temperature", temperature);

        // GPT-OSS / Groq completion token parameter.
        // Do not use the old max_tokens parameter.
        request.put("max_completion_tokens", tokenLimit);

        request.put("reasoning_effort", "low");
        request.put("reasoning_format", "hidden");

        request.put("top_p", 0.9);

        // Require JSON object output.
        request.put(
                "response_format",
                Map.of("type", "json_object")
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
                        "Groq error: " + root.path("error").toString()
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

            String finishReason =
                    choice.path("finish_reason").asText("unknown");

            JsonNode usage = root.path("usage");

            if (usage.isObject()) {
                log.info(
                        "Groq token usage: prompt={}, completion={}, total={}",
                        usage.path("prompt_tokens").asInt(-1),
                        usage.path("completion_tokens").asInt(-1),
                        usage.path("total_tokens").asInt(-1)
                );
            }

            if ("length".equalsIgnoreCase(finishReason)) {
                throw new RuntimeException(
                        "Groq reached the completion token limit. "
                                + "Generated JSON may be incomplete. "
                                + "Increase groq.assessment.max-tokens."
                );
            }

            if (!notBlank(generatedText)) {
                throw new RuntimeException(
                        "Groq returned empty generated content. "
                                + "Finish reason: " + finishReason
                );
            }

            log.info(
                    "Groq completion finished. Finish reason={}",
                    finishReason
            );

            return generatedText;

        } catch (HttpStatusCodeException e) {

            String errorBody = e.getResponseBodyAsString();

            log.error(
                    "Groq API HTTP error. Status={}, Response={}",
                    e.getStatusCode(),
                    preview(errorBody)
            );

            throw new RuntimeException(
                    "Groq API request failed. HTTP "
                            + e.getStatusCode().value()
                            + ". " + groqErrorMessage(errorBody),
                    e
            );

        } catch (ResourceAccessException e) {

            throw new RuntimeException(
                    "Groq request timed out or connection failed. "
                            + "Configured timeout: "
                            + groqTimeout + " ms.",
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

    private String groqErrorMessage(String response) {

        if (!notBlank(response)) {
            return "No error details returned.";
        }

        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode error = root.path("error");

            if (error.isObject()) {

                String message = error.path("message").asText("");

                String code = error.path("code").asText("");

                if (notBlank(code)) {
                    return message + " (Code: " + code + ")";
                }

                return notBlank(message)
                        ? message
                        : preview(response);
            }

            if (error.isTextual()) {
                return error.asText();
            }

        } catch (Exception ignored) {
            // Use response preview below.
        }

        return preview(response);
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
                        "Missing questions array in AI response."
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

    private String validateGeneratedQuestion(GeneratedQuestion q) {

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

        return switch (
                difficulty.trim().toUpperCase(Locale.ROOT)
        ) {

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
                        + ". Please try again or check Groq API configuration."
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

        return value != null && !value.trim().isEmpty();
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
            result = result.substring(0, result.length() - 1);
        }

        return result;
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
