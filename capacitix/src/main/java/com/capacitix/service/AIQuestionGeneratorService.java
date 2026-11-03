
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

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    @Value("${ollama.timeout:120000}")
    private long ollamaTimeout;

    @Value("${ollama.temperature:0.2}")
    private double ollamaTemperature;

    @Value("${ollama.num-predict:1024}")
    private int ollamaNumPredict;

    private static final int BATCH_SIZE = 3;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final int MAX_PREVIOUS_QUESTIONS = 10;

    private RestClient restClient;

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
                "AI question generator initialized. Model={}, batchSize={}",
                ollamaModel,
                BATCH_SIZE
        );
    }

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

        int consecutiveFailures = 0;
        int requestCount = 0;

        while (generated.size() < requiredCount) {

            int remaining = requiredCount - generated.size();
            int batchCount = Math.min(BATCH_SIZE, remaining);

            requestCount++;

            log.info(
                    "Generating assessment questions: {}/{} completed; requesting batch of {}",
                    generated.size(),
                    requiredCount,
                    batchCount
            );

            long requestStart = System.nanoTime();

            try {
                String prompt = buildBatchPrompt(
                        assessment,
                        validCompetencies,
                        batchCount,
                        generated
                );

                String response = callOllama(prompt, batchCount);

                long requestMillis =
                        (System.nanoTime() - requestStart) / 1_000_000;

                log.info(
                        "Ollama request {} completed in {} ms",
                        requestCount,
                        requestMillis
                );

                List<GeneratedQuestion> batch =
                        parseQuestions(response);

                int acceptedThisBatch = 0;

                for (GeneratedQuestion question : batch) {

                    if (generated.size() >= requiredCount) {
                        break;
                    }

                    if (!isValidQuestion(question)) {
                        log.warn("Skipping invalid AI-generated question.");
                        continue;
                    }

                    Competency competency =
                            findCompetency(question.competency, competencyMap);

                    if (competency == null) {
                        log.warn(
                                "Skipping question with unknown competency: {}",
                                question.competency
                        );
                        continue;
                    }

                    question.competency = competency.getName();

                    question.correctAnswer =
                            normalizeAnswer(question.correctAnswer, question);

                    if (!isValidCorrectAnswer(question.correctAnswer)) {
                        log.warn("Skipping question with invalid correct answer.");
                        continue;
                    }

                    String key = questionKey(question);

                    if (!questionKeys.add(key)) {
                        log.warn(
                                "Skipping duplicate question: {}",
                                question.question
                        );
                        continue;
                    }

                    question.question = question.question.trim();
                    question.optionA = question.optionA.trim();
                    question.optionB = question.optionB.trim();
                    question.optionC = question.optionC.trim();
                    question.optionD = question.optionD.trim();

                    generated.add(question);
                    acceptedThisBatch++;
                }

                if (acceptedThisBatch == 0) {
                    consecutiveFailures++;
                    log.warn(
                            "Batch produced no usable questions. Consecutive failures: {}/{}",
                            consecutiveFailures,
                            MAX_CONSECUTIVE_FAILURES
                    );
                } else {
                    consecutiveFailures = 0;
                }

            } catch (RuntimeException e) {
                consecutiveFailures++;

                log.warn(
                        "Question batch request {} failed: {}",
                        requestCount,
                        safeExceptionMessage(e)
                );
            }

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                throw new RuntimeException(
                        "AI could not generate enough valid questions. Generated "
                                + generated.size() + " of " + requiredCount
                                + ". Check the Ollama logs and model availability."
                );
            }
        }

        List<AssessmentQuestion> questions = new ArrayList<>();
        int order = 1;

        for (GeneratedQuestion item : generated) {

            Competency competency =
                    findCompetency(item.competency, competencyMap);

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
                            .difficulty(normalizeDifficulty(item.difficulty))
                            .marks(item.marks > 0 ? item.marks : 1)
                            .explanation(safeNullable(item.explanation))
                            .questionOrder(order++)
                            .active(true)
                            .build();

            questions.add(question);
        }

        if (questions.size() != requiredCount) {
            throw new RuntimeException(
                    "Expected " + requiredCount
                            + " questions but prepared " + questions.size() + "."
            );
        }

        // Replace existing questions only after a complete valid set is ready.
        questionRepository.deleteByAssessment(assessment);

        List<AssessmentQuestion> saved =
                questionRepository.saveAll(questions);

        if (saved.size() != requiredCount) {
            throw new RuntimeException(
                    "Expected " + requiredCount
                            + " questions to be saved but saved " + saved.size() + "."
            );
        }

        assessment.setQuestionCount(saved.size());

        long totalMillis =
                (System.nanoTime() - startTime) / 1_000_000;

        log.info(
                "Assessment generation completed: {} questions, {} Ollama requests, {} ms total",
                saved.size(),
                requestCount,
                totalMillis
        );

        return saved;
    }

    private String buildBatchPrompt(
            Assessment assessment,
            List<Competency> competencies,
            int count,
            List<GeneratedQuestion> previousQuestions
    ) {
        String competencyText = competencies.stream()
                .map(c -> "- " + c.getName()
                        + " | Level: " + safe(c.getLevel())
                        + " | Description: " + safe(c.getDescription()))
                .collect(Collectors.joining("\n"));

        StringBuilder previousText = new StringBuilder();

        int start = Math.max(
                0,
                previousQuestions.size() - MAX_PREVIOUS_QUESTIONS
        );

        for (int i = start; i < previousQuestions.size(); i++) {
            GeneratedQuestion q = previousQuestions.get(i);

            previousText.append("- ")
                    .append(q.competency)
                    .append(": ")
                    .append(q.question)
                    .append('\n');
        }

        if (previousText.isEmpty()) {
            previousText.append("None.");
        }

        return """
                Generate exactly %d distinct technical multiple-choice questions for CapacitiX.

                Assessment: %s
                Target role: %s

                Allowed competencies:
                %s

                Previous questions to avoid:
                %s

                Rules:
                - Use only the supplied competencies and their exact names.
                - Use simple English and practical technical concepts.
                - Every question must test a different concept.
                - Include exactly four distinct options per question.
                - correctAnswer must be A, B, C, or D.
                - difficulty must be BEGINNER, INTERMEDIATE, or ADVANCED.
                - marks must be 1.
                - Keep explanations to one short sentence.
                - Do not repeat any previous question.
                - Return only valid JSON, without Markdown or extra text.

                Return a JSON object with a "questions" array.
                Every array item must follow this structure:
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
                count,
                safe(assessment.getName()),
                safe(assessment.getTargetRole()),
                competencyText,
                previousText
        );
    }

    private String callOllama(String prompt, int batchCount) {
        int tokenLimit = Math.min(
                1200,
                Math.max(512, ollamaNumPredict)
        );

        OllamaRequest request = new OllamaRequest(
                ollamaModel,
                prompt,
                false,
                "json",
                new OllamaOptions(
                        Math.max(0.0, Math.min(0.8, ollamaTemperature)),
                        tokenLimit,
                        1.1,
                        0.9
                ),
                "10m"
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
                throw new RuntimeException("Ollama returned an empty response.");
            }

            return response.response();

        } catch (RestClientResponseException e) {
            throw new RuntimeException(
                    "Ollama HTTP error " + e.getStatusCode().value()
                            + ": " + preview(e.getResponseBodyAsString()),
                    e
            );
        } catch (ResourceAccessException e) {
            throw new RuntimeException(
                    "Cannot connect to Ollama at "
                            + removeTrailingSlash(ollamaBaseUrl)
                            + ". Make sure Ollama is running.",
                    e
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Ollama request failed: " + safeExceptionMessage(e),
                    e
            );
        }
    }

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
            } else if (root.isObject() && root.has("questions")
                    && root.get("questions").isArray()) {
                array = root.get("questions");
            } else if (root.isObject()) {
                array = objectMapper.createArrayNode().add(root);
            } else {
                throw new RuntimeException("AI response has an unsupported JSON structure.");
            }

            List<GeneratedQuestion> questions = new ArrayList<>();

            for (JsonNode node : array) {
                if (node == null || !node.isObject()) {
                    continue;
                }

                GeneratedQuestion q = new GeneratedQuestion();

                q.competency = firstText(node,
                        "competency", "skill", "competencyName");

                q.question = firstText(node,
                        "question", "questionText", "text");

                q.optionA = firstText(node, "optionA", "option_a", "A", "a");
                q.optionB = firstText(node, "optionB", "option_b", "B", "b");
                q.optionC = firstText(node, "optionC", "option_c", "C", "c");
                q.optionD = firstText(node, "optionD", "option_d", "D", "d");

                JsonNode options = node.get("options");

                if (options != null && options.isArray()
                        && options.size() >= 4) {
                    if (!notBlank(q.optionA)) q.optionA = nodeText(options.get(0));
                    if (!notBlank(q.optionB)) q.optionB = nodeText(options.get(1));
                    if (!notBlank(q.optionC)) q.optionC = nodeText(options.get(2));
                    if (!notBlank(q.optionD)) q.optionD = nodeText(options.get(3));
                }

                q.correctAnswer = firstText(node,
                        "correctAnswer", "correct_answer",
                        "answer", "correctOption", "correct_option");

                q.difficulty = firstText(node, "difficulty", "level");
                q.explanation = firstText(node, "explanation", "reason");

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
        int objectEnd = json.lastIndexOf('}');

        if (objectStart >= 0 && objectEnd > objectStart) {
            return json.substring(objectStart, objectEnd + 1).trim();
        }

        int arrayStart = json.indexOf('[');
        int arrayEnd = json.lastIndexOf(']');

        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return json.substring(arrayStart, arrayEnd + 1).trim();
        }

        return json;
    }

    private boolean isValidQuestion(GeneratedQuestion q) {
        if (q == null
                || !notBlank(q.competency)
                || !notBlank(q.question)
                || !notBlank(q.optionA)
                || !notBlank(q.optionB)
                || !notBlank(q.optionC)
                || !notBlank(q.optionD)
                || !notBlank(q.correctAnswer)) {
            return false;
        }

        List<String> options = List.of(
                normalize(q.optionA),
                normalize(q.optionB),
                normalize(q.optionC),
                normalize(q.optionD)
        );

        return options.stream().distinct().count() == 4;
    }

    private String normalizeAnswer(String answer, GeneratedQuestion q) {
        if (!notBlank(answer)) {
            return "";
        }

        String value = answer.trim();
        String upper = value.toUpperCase(Locale.ROOT);

        if (upper.matches("^(A|B|C|D)$")) {
            return upper;
        }

        if (upper.matches("^OPTION\\s*A.*") || upper.matches("^ANSWER:?\\s*A.*")) return "A";
        if (upper.matches("^OPTION\\s*B.*") || upper.matches("^ANSWER:?\\s*B.*")) return "B";
        if (upper.matches("^OPTION\\s*C.*") || upper.matches("^ANSWER:?\\s*C.*")) return "C";
        if (upper.matches("^OPTION\\s*D.*") || upper.matches("^ANSWER:?\\s*D.*")) return "D";

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

    private String questionKey(GeneratedQuestion q) {
        return normalize(q.competency) + "::" + normalize(q.question);
    }

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

        for (Map.Entry<String, Competency> entry : competencyMap.entrySet()) {
            String known = entry.getKey();

            if (key.contains(known) || known.contains(key)) {
                return entry.getValue();
            }
        }

        String[] words = key.split("\\s+");
        Competency best = null;
        int bestScore = 0;

        for (Map.Entry<String, Competency> entry : competencyMap.entrySet()) {
            int score = 0;

            for (String word : words) {
                if (word.length() >= 3
                        && entry.getKey().contains(word)) {
                    score++;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = entry.getValue();
            }
        }

        return bestScore > 0 ? best : null;
    }

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

    private void validateInput(
            Assessment assessment,
            List<Competency> competencies
    ) {
        if (assessment == null) {
            throw new IllegalArgumentException("Assessment is required.");
        }

        if (competencies == null || competencies.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select at least one competency."
            );
        }

        if (assessment.getQuestionCount() == null
                || assessment.getQuestionCount() <= 0
                || assessment.getQuestionCount() > 50) {
            throw new IllegalArgumentException(
                    "Question count must be between 1 and 50."
            );
        }
    }

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
            return e == null ? "Unknown error" : e.getClass().getSimpleName();
        }

        return e.getMessage();
    }

    private String preview(String value) {
        if (!notBlank(value)) {
            return "[empty response]";
        }

        String result = value.replace('\n', ' ').replace('\r', ' ').trim();

        return result.length() > 500
                ? result.substring(0, 500) + "..."
                : result;
    }

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
