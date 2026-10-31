
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    @Value("${ollama.timeout:180000}")
    private long ollamaTimeout;

    @Value("${ollama.temperature:0.2}")
    private double ollamaTemperature;

    @Value("${ollama.num-predict:4096}")
    private int ollamaNumPredict;

    @Value("${ollama.keep-alive:30m}")
    private String ollamaKeepAlive;

    /*
     * One initial batch request and at most one repair request.
     */
    private static final int MAX_REPAIR_REQUESTS = 1;

    private static final int EXTRA_REPAIR_CANDIDATES = 2;

    private static final int MAX_QUESTION_COUNT = 50;

    private static final Set<String> STOP_WORDS = Set.of(
            "what", "which", "when", "where", "why", "how",
            "the", "is", "are", "was", "were", "does", "do",
            "did", "can", "could", "would", "should", "will",
            "a", "an", "and", "or", "of", "to", "in", "on",
            "for", "with", "from", "by", "at", "be", "this",
            "that", "these", "those", "using", "used"
    );

    private RestClient restClient;

    // =========================================================
    // INITIALIZATION
    // =========================================================

    @PostConstruct
    public void initializeRestClient() {

        var factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMillis(ollamaTimeout));

        restClient = RestClient.builder()
                .baseUrl(removeTrailingSlash(ollamaBaseUrl))
                .requestFactory(factory)
                .build();

        log.info(
                "AI question generator initialized. Model={}, Strategy=Batch generation, MaxRepairRequests={}, Timeout={}ms, NumPredict={}",
                ollamaModel,
                MAX_REPAIR_REQUESTS,
                ollamaTimeout,
                ollamaNumPredict
        );
    }

    // =========================================================
    // MAIN QUESTION GENERATION
    // =========================================================

    @Transactional
    public List<AssessmentQuestion> generateQuestions(
            Assessment assessment,
            List<Competency> competencies
    ) {

        long startTime = System.nanoTime();

        validateInput(assessment, competencies);

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
                    "No valid competencies were selected."
            );
        }

        List<Competency> validCompetencies =
                new ArrayList<>(competencyMap.values());

        List<GeneratedQuestion> generated = new ArrayList<>();

        Set<String> questionKeys = new HashSet<>();

        log.info(
                "Starting AI assessment generation. Required={}, Competencies={}, Model={}",
                requiredCount,
                validCompetencies.size(),
                ollamaModel
        );

        /*
         * REQUEST 1:
         * Generate the complete required question count in one request.
         *
         * REQUEST 2:
         * Generate only the missing questions, if required.
         */
        for (int requestNumber = 1;
             requestNumber <= MAX_REPAIR_REQUESTS + 1;
             requestNumber++) {

            if (generated.size() >= requiredCount) {
                break;
            }

            int remaining = requiredCount - generated.size();

            int candidateCount;

            if (requestNumber == 1) {
                candidateCount = requiredCount;
            } else {
                candidateCount = Math.min(
                        MAX_QUESTION_COUNT,
                        remaining + EXTRA_REPAIR_CANDIDATES
                );
            }

            log.info(
                    "Ollama batch request {}. Accepted={}/{}, Requesting={} candidates",
                    requestNumber,
                    generated.size(),
                    requiredCount,
                    candidateCount
            );

            long requestStart = System.nanoTime();

            try {

                String prompt = buildBatchPrompt(
                        assessment,
                        validCompetencies,
                        candidateCount,
                        generated,
                        requestNumber
                );

                String response = callOllama(prompt);

                long requestMillis =
                        (System.nanoTime() - requestStart) / 1_000_000;

                log.info(
                        "Ollama request {} completed in {} ms",
                        requestNumber,
                        requestMillis
                );

                List<GeneratedQuestion> batch =
                        parseQuestions(response);

                log.info(
                        "Ollama returned {} question candidates in request {}",
                        batch.size(),
                        requestNumber
                );

                int acceptedThisBatch = 0;
                int rejectedThisBatch = 0;

                for (GeneratedQuestion question : batch) {

                    if (generated.size() >= requiredCount) {
                        break;
                    }

                    String validationError =
                            validateGeneratedQuestion(question);

                    if (validationError != null) {

                        rejectedThisBatch++;

                        log.warn(
                                "Rejected generated question: {}",
                                validationError
                        );

                        continue;
                    }

                    Competency competency =
                            findCompetency(
                                    question.competency,
                                    competencyMap
                            );

                    /*
                     * If only one competency was selected, assigning
                     * that competency is unambiguous even if the model
                     * omitted or slightly changed its label.
                     */
                    if (competency == null
                            && validCompetencies.size() == 1) {

                        competency = validCompetencies.get(0);
                    }

                    if (competency == null) {

                        rejectedThisBatch++;

                        log.warn(
                                "Rejected question because competency '{}' does not match selected competencies.",
                                question.competency
                        );

                        continue;
                    }

                    question.competency = competency.getName();

                    question.correctAnswer =
                            normalizeAnswer(
                                    question.correctAnswer,
                                    question
                            );

                    if (!isValidCorrectAnswer(question.correctAnswer)) {

                        rejectedThisBatch++;

                        log.warn(
                                "Rejected question because its correct answer is invalid."
                        );

                        continue;
                    }

                    question.question = question.question.trim();
                    question.optionA = question.optionA.trim();
                    question.optionB = question.optionB.trim();
                    question.optionC = question.optionC.trim();
                    question.optionD = question.optionD.trim();

                    String key = questionKey(question);

                    if (!questionKeys.add(key)) {

                        rejectedThisBatch++;

                        log.warn(
                                "Rejected duplicate question: {}",
                                preview(question.question)
                        );

                        continue;
                    }

                    if (isSimilarToPrevious(question, generated)) {

                        questionKeys.remove(key);
                        rejectedThisBatch++;

                        log.warn(
                                "Rejected similar question: {}",
                                preview(question.question)
                        );

                        continue;
                    }

                    question.difficulty =
                            normalizeDifficulty(question.difficulty);

                    question.marks = 1;

                    generated.add(question);
                    acceptedThisBatch++;

                    log.info(
                            "Accepted question {}/{} for competency: {}",
                            generated.size(),
                            requiredCount,
                            competency.getName()
                    );
                }

                log.info(
                        "Batch {} result: Accepted={}, Rejected={}, Total={}/{}",
                        requestNumber,
                        acceptedThisBatch,
                        rejectedThisBatch,
                        generated.size(),
                        requiredCount
                );

            } catch (RuntimeException e) {

                log.error(
                        "Ollama batch request {} failed: {}",
                        requestNumber,
                        safeExceptionMessage(e),
                        e
                );

                /*
                 * The next loop iteration is the single repair attempt.
                 */
            }
        }

        if (generated.size() != requiredCount) {

            throw generationFailure(
                    generated.size(),
                    requiredCount
            );
        }

        // =====================================================
        // SAVE ONLY AFTER COMPLETE GENERATION
        // =====================================================

        List<AssessmentQuestion> questions = new ArrayList<>();

        int order = 1;

        for (GeneratedQuestion item : generated) {

            Competency competency =
                    findCompetency(
                            item.competency,
                            competencyMap
                    );

            if (competency == null
                    && validCompetencies.size() == 1) {

                competency = validCompetencies.get(0);
            }

            if (competency == null) {
                throw new RuntimeException(
                        "A generated question has an invalid competency."
                );
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

        if (questions.size() != requiredCount) {

            throw new RuntimeException(
                    "Expected " + requiredCount
                            + " questions but prepared "
                            + questions.size() + "."
            );
        }

        /*
         * Existing questions are deleted only after a complete
         * valid replacement set has been generated.
         */
        questionRepository.deleteByAssessment(assessment);

        List<AssessmentQuestion> saved =
                questionRepository.saveAll(questions);

        if (saved.size() != requiredCount) {

            throw new RuntimeException(
                    "Expected " + requiredCount
                            + " questions to be saved but saved "
                            + saved.size() + "."
            );
        }

        assessment.setQuestionCount(saved.size());

        long totalMillis =
                (System.nanoTime() - startTime) / 1_000_000;

        log.info(
                "Assessment generation completed. Questions={}, TotalTime={}ms",
                saved.size(),
                totalMillis
        );

        return saved;
    }

    // =========================================================
    // GENERATION FAILURE
    // =========================================================

    private RuntimeException generationFailure(
            int generated,
            int required
    ) {

        return new RuntimeException(
                "AI could not generate enough valid questions. Generated "
                        + generated + " of " + required
                        + " after one initial batch and one repair attempt. "
                        + "Please try again or check the Ollama model and logs."
        );
    }

    // =========================================================
    // BATCH PROMPT
    // =========================================================

    private String buildBatchPrompt(
            Assessment assessment,
            List<Competency> competencies,
            int count,
            List<GeneratedQuestion> previousQuestions,
            int requestNumber
    ) {

        String competencyText = competencies.stream()
                .map(c ->
                        "- " + c.getName()
                                + " | Level: " + safe(c.getLevel())
                                + " | Description: "
                                + safe(c.getDescription())
                )
                .collect(Collectors.joining("\n"));

        String previousText;

        if (previousQuestions.isEmpty()) {

            previousText = "None.";

        } else {

            previousText = previousQuestions.stream()
                    .map(q -> "- " + q.question)
                    .collect(Collectors.joining("\n"));
        }

        String taskInstruction;

        if (requestNumber == 1) {

            taskInstruction =
                    "Generate the complete assessment question set.";

        } else {

            taskInstruction =
                    "Generate only new questions. Do not repeat or rephrase any previous question.";
        }

        return """
                You are a professional MCQ assessment designer.

                TASK:
                %s

                Generate EXACTLY %d valid multiple-choice questions.

                ASSESSMENT:
                Name: %s
                Target role: %s

                SELECTED COMPETENCIES:
                %s

                PREVIOUS QUESTIONS - DO NOT REPEAT:
                %s

                RULES:
                1. Every question must test one selected competency.
                2. Use the exact competency name from the selected list.
                3. If only one competency is listed, use that competency for every question.
                4. Stay strictly within the selected competency topics.
                5. Do not generate unrelated general knowledge questions.
                6. Every question must test a different concept.
                7. Use clear, simple, grammatically correct English.
                8. Make questions appropriate for the target role.
                9. Every question must have exactly four meaningful options.
                10. All four options must be different.
                11. Exactly one option must be correct.
                12. correctAnswer must be only A, B, C, or D.
                13. difficulty must be BEGINNER, INTERMEDIATE, or ADVANCED.
                14. marks must be the number 1.
                15. explanation must be one short correct sentence.
                16. Do not use Markdown.
                17. Do not add text outside the JSON.
                18. Do not omit any required field.
                19. Return exactly %d question objects.

                QUESTION VARIETY:
                Include a balanced mix of conceptual, practical,
                scenario-based, and debugging/output questions where relevant.

                OUTPUT:
                Return one valid JSON object in this exact structure:

                {
                  "questions": [
                    {
                      "competency": "Exact selected competency name",
                      "question": "Question text?",
                      "optionA": "Option A",
                      "optionB": "Option B",
                      "optionC": "Option C",
                      "optionD": "Option D",
                      "correctAnswer": "A",
                      "difficulty": "INTERMEDIATE",
                      "marks": 1,
                      "explanation": "Short explanation."
                    }
                  ]
                }

                JSON ONLY. No introduction. No conclusion.
                """.formatted(
                taskInstruction,
                count,
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyText,
                previousText,
                count
        );
    }

    // =========================================================
    // OLLAMA API
    // =========================================================

    private String callOllama(String prompt) {

        int tokenLimit = Math.max(
                2048,
                Math.min(8192, ollamaNumPredict)
        );

        double temperature = Math.max(
                0.0,
                Math.min(0.5, ollamaTemperature)
        );

        OllamaRequest request = new OllamaRequest(
                ollamaModel,
                prompt,
                false,
                "json",
                new OllamaOptions(
                        temperature,
                        tokenLimit,
                        1.15,
                        0.9
                ),
                ollamaKeepAlive
        );

        try {

            OllamaResponse response = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaResponse.class);

            if (response == null
                    || response.response() == null
                    || response.response().isBlank()) {

                throw new RuntimeException(
                        "Ollama returned an empty response."
                );
            }

            if (!response.done()) {

                log.warn(
                        "Ollama response indicates generation may not have completed."
                );
            }

            return response.response();

        } catch (RestClientResponseException e) {

            throw new RuntimeException(
                    "Ollama HTTP error "
                            + e.getStatusCode().value()
                            + ": "
                            + preview(e.getResponseBodyAsString()),
                    e
            );

        } catch (ResourceAccessException e) {

            throw new RuntimeException(
                    "Cannot connect to Ollama at "
                            + removeTrailingSlash(ollamaBaseUrl)
                            + ". Check whether Ollama is running and responding.",
                    e
            );

        } catch (RuntimeException e) {

            throw e;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Ollama request failed: "
                            + safeExceptionMessage(e),
                    e
            );
        }
    }

    // =========================================================
    // JSON PARSING
    // =========================================================

    private List<GeneratedQuestion> parseQuestions(String response) {

        try {

            String json = cleanJson(response);

            if (json.isBlank()) {
                throw new RuntimeException("AI returned empty JSON.");
            }

            JsonNode root = objectMapper.readTree(json);

            if (root == null || root.isNull()) {
                throw new RuntimeException("AI returned no JSON data.");
            }

            JsonNode array;

            if (root.isArray()) {

                array = root;

            } else if (root.isObject()
                    && root.has("questions")
                    && root.get("questions").isArray()) {

                array = root.get("questions");

            } else if (root.isObject()) {

                array = objectMapper.createArrayNode().add(root);

            } else {

                throw new RuntimeException(
                        "AI response has an unsupported JSON structure."
                );
            }

            List<GeneratedQuestion> questions = new ArrayList<>();

            for (JsonNode node : array) {

                if (node == null || !node.isObject()) {
                    continue;
                }

                GeneratedQuestion q = new GeneratedQuestion();

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
                        "correctOption",
                        "correct_option"
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

                JsonNode marks = node.get("marks");

                q.marks = marks != null && marks.canConvertToInt()
                        ? marks.asInt()
                        : 1;

                questions.add(q);
            }

            return questions;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to parse AI questions: " + preview(response),
                    e
            );
        }
    }

    // =========================================================
    // JSON CLEANING
    // =========================================================

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

        int objectStart = json.indexOf('{');
        int arrayStart = json.indexOf('[');

        if (arrayStart >= 0
                && (objectStart < 0 || arrayStart < objectStart)) {

            int arrayEnd = json.lastIndexOf(']');

            if (arrayEnd > arrayStart) {
                return json.substring(arrayStart, arrayEnd + 1).trim();
            }
        }

        if (objectStart >= 0) {

            int objectEnd = json.lastIndexOf('}');

            if (objectEnd > objectStart) {
                return json.substring(objectStart, objectEnd + 1).trim();
            }
        }

        if (arrayStart >= 0) {

            int arrayEnd = json.lastIndexOf(']');

            if (arrayEnd > arrayStart) {
                return json.substring(arrayStart, arrayEnd + 1).trim();
            }
        }

        return json;
    }

    // =========================================================
    // QUESTION VALIDATION
    // =========================================================

    private String validateGeneratedQuestion(GeneratedQuestion q) {

        if (q == null) {
            return "Empty question object";
        }

        if (!notBlank(q.question)) {
            return "Missing question text";
        }

        if (q.question.trim().length() < 12) {
            return "Question text is too short";
        }

        if (!notBlank(q.optionA)
                || !notBlank(q.optionB)
                || !notBlank(q.optionC)
                || !notBlank(q.optionD)) {

            return "One or more options are missing";
        }

        List<String> options = List.of(
                normalize(q.optionA),
                normalize(q.optionB),
                normalize(q.optionC),
                normalize(q.optionD)
        );

        if (options.stream().distinct().count() != 4) {
            return "Options are not unique";
        }

        if (!notBlank(q.correctAnswer)) {
            return "Correct answer is missing";
        }

        return null;
    }

    // =========================================================
    // DUPLICATE DETECTION
    // =========================================================

    private String questionKey(GeneratedQuestion q) {
        return normalize(q.question);
    }

    private boolean isSimilarToPrevious(
            GeneratedQuestion candidate,
            List<GeneratedQuestion> previousQuestions
    ) {

        for (GeneratedQuestion previous : previousQuestions) {

            if (sameText(candidate.question, previous.question)) {
                return true;
            }

            if (calculateSimilarity(
                    candidate.question,
                    previous.question
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean calculateSimilarity(String first, String second) {

        Set<String> firstWords = meaningfulWords(first);
        Set<String> secondWords = meaningfulWords(second);

        if (firstWords.size() < 4 || secondWords.size() < 4) {
            return false;
        }

        Set<String> intersection = new HashSet<>(firstWords);
        intersection.retainAll(secondWords);

        Set<String> union = new HashSet<>(firstWords);
        union.addAll(secondWords);

        if (union.isEmpty()) {
            return false;
        }

        double jaccard =
                (double) intersection.size() / union.size();

        double overlap =
                (double) intersection.size()
                        / Math.min(firstWords.size(), secondWords.size());

        return jaccard >= 0.82 || overlap >= 0.92;
    }

    private Set<String> meaningfulWords(String question) {

        String normalized = normalize(question);

        return Arrays.stream(normalized.split("\\s+"))
                .filter(word -> word.length() >= 3)
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // =========================================================
    // CORRECT ANSWER NORMALIZATION
    // =========================================================

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

        if (upper.matches("^(OPTION\\s*)?A[).:\\s-].*")) return "A";
        if (upper.matches("^(OPTION\\s*)?B[).:\\s-].*")) return "B";
        if (upper.matches("^(OPTION\\s*)?C[).:\\s-].*")) return "C";
        if (upper.matches("^(OPTION\\s*)?D[).:\\s-].*")) return "D";

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

    // =========================================================
    // COMPETENCY MATCHING
    // =========================================================

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

    // =========================================================
    // DIFFICULTY
    // =========================================================

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

    // =========================================================
    // INPUT VALIDATION
    // =========================================================

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
                            + MAX_QUESTION_COUNT + "."
            );
        }
    }

    // =========================================================
    // JSON FIELD HELPERS
    // =========================================================

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

    // =========================================================
    // COMMON HELPERS
    // =========================================================

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
            return "http://localhost:11434";
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

    // =========================================================
    // OLLAMA REQUEST / RESPONSE
    // =========================================================

    private record OllamaRequest(
            String model,
            String prompt,
            boolean stream,
            String format,
            OllamaOptions options,
            String keep_alive
    ) {}

    private record OllamaOptions(
            double temperature,
            int num_predict,
            double repeat_penalty,
            double top_p
    ) {}

    private record OllamaResponse(
            String model,
            String response,
            boolean done
    ) {}

    // =========================================================
    // GENERATED QUESTION MODEL
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
