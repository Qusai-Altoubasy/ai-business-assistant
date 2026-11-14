package com.aibusinessassistant;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.aibusinessassistant.chat.ai.AiProvider;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.chat.dto.ChatRequestDTO;
import com.aibusinessassistant.knowledge.KnowledgeAnswerService;
import com.aibusinessassistant.knowledge.KnowledgeBaseIndexer;
import com.aibusinessassistant.knowledge.KnowledgeChunkRepository;
import com.aibusinessassistant.knowledge.dto.KnowledgeAnswerDTO;
import com.aibusinessassistant.knowledge.dto.KnowledgeQuestionDTO;
import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

// Calls the real Gemini embedding and chat models, so it runs only on request:
// ./mvnw test -Dtest=EvaluationLiveTest -Dgemini.live=true (with GEMINI_API_KEY and GEMINI_MODEL set);
// -Deval.repeat (default 3) and -Deval.delay-ms (default 4000) change the repetitions and the pause between requests;
// a rate-limited run waits -Deval.rate-limit-wait-ms (default 60000) and is retried at most twice.
// Sends every question in eval/questions.json over HTTP and writes target/eval/report.md and report.json. It
// measures and does not judge: the test passes when the run completes, and question failures are in the report.
@QuarkusTest
@TestProfile(EvaluationLiveTest.RealGemini.class)
@EnabledIfSystemProperty(named = "gemini.live", matches = "true")
class EvaluationLiveTest {

    private static final Path REPORT_DIR = Path.of("target", "eval");
    private static final Path LOG_FILE = REPORT_DIR.resolve("quarkus.log");
    private static final String CHAT = "chat";
    private static final String KNOWLEDGE = "knowledge";
    private static final Pattern CHUNK_ID = Pattern.compile("<chunk id=\"([^\"]+)\"");
    private static final Pattern RULE_ID = Pattern.compile("\\b(?:INV|REF|CUS)-\\d+\\b");
    private static final Pattern RATE_LIMITED = Pattern.compile("\\b429\\b|RESOURCE_EXHAUSTED|quota|rate.?limit",
            Pattern.CASE_INSENSITIVE);
    private static final String REDACTED = "[redacted]";
    private static final int MAX_RATE_LIMIT_RETRIES = 2;

    @Inject
    KnowledgeBaseIndexer indexer;

    @Inject
    KnowledgeChunkRepository chunks;

    @Inject
    ModelCallRecorder recorder;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.ai.gemini.business-gemini.chat-model.model-id")
    String modelId;

    @Test
    void evaluateEveryQuestion() throws IOException, InterruptedException {
        int repeat = Integer.getInteger("eval.repeat", 3);
        long delayMs = Long.getLong("eval.delay-ms", 4000);
        long rateLimitWaitMs = Long.getLong("eval.rate-limit-wait-ms", 60_000);
        List<Question> questions = readQuestions();

        long chunksAfterStartup = QuarkusTransaction.requiringNew().call(chunks::count);
        assertTrue(chunksAfterStartup > 0, "knowledge_chunks is empty after startup indexing; the run was not started");
        // Embeds only chunks a failed startup run left out; a complete index costs no embedding call.
        indexer.indexAll();
        long chunkCount = QuarkusTransaction.requiringNew().call(chunks::count);

        Instant started = Instant.now();
        List<Run> runs = new ArrayList<>();
        int total = repeat * questions.stream().mapToInt(question -> question.endpoints().size()).sum();
        for (int repetition = 1; repetition <= repeat; repetition++) {
            for (Question question : questions) {
                for (String endpoint : question.endpoints()) {
                    if (!runs.isEmpty()) {
                        Thread.sleep(delayMs);
                    }
                    Run run = executeRetryingRateLimits(question, endpoint, repetition, rateLimitWaitMs);
                    runs.add(run);
                    System.out.println(redact("EVAL " + runs.size() + "/" + total + " " + run.id() + " " + endpoint
                            + " run " + repetition + ": " + run.status() + " (" + run.latencyMs() + " ms)"));
                }
            }
        }
        Duration wallClock = Duration.between(started, Instant.now());

        Report report = report(questions, runs, repeat, delayMs, rateLimitWaitMs, chunksAfterStartup, chunkCount,
                started, wallClock);
        Files.createDirectories(REPORT_DIR);
        String json = redact(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        String markdown = redact(markdown(report));
        Files.writeString(REPORT_DIR.resolve("report.json"), json);
        Files.writeString(REPORT_DIR.resolve("report.md"), markdown);
        System.out.println(markdown.lines().limit(40).collect(Collectors.joining("\n")));

        // A key in the log is a leak to report, not a question failure; the copy on disk is redacted.
        boolean keyInLog = keyIn(LOG_FILE);
        if (keyInLog) {
            Files.writeString(LOG_FILE, redact(Files.readString(LOG_FILE)));
        }
        assertFalse(keyInLog, "the API key value appeared in " + LOG_FILE + " (redacted on disk)");
        assertFalse(keyIn(REPORT_DIR.resolve("report.json")) || keyIn(REPORT_DIR.resolve("report.md")),
                "the API key value appeared in the report");
    }

    // Only a rate-limited run is retried, after waiting, at most MAX_RATE_LIMIT_RETRIES times; any other error is
    // kept. The returned run counts the model calls and tokens of the rate-limited attempts too.
    private Run executeRetryingRateLimits(Question question, String endpoint, int repetition, long waitMs)
            throws InterruptedException {
        int earlierCalls = 0;
        long earlierInputTokens = 0;
        long earlierOutputTokens = 0;
        for (int retries = 0; ; retries++) {
            Run run = execute(question, endpoint, repetition);
            if (run.status() != Status.RATE_LIMITED || retries == MAX_RATE_LIMIT_RETRIES) {
                return run.afterRetries(retries, earlierCalls, earlierInputTokens, earlierOutputTokens);
            }
            earlierCalls += run.modelCalls();
            earlierInputTokens += run.inputTokens();
            earlierOutputTokens += run.outputTokens();
            System.out.println("EVAL " + question.id() + " " + endpoint + " run " + repetition + ": rate-limited, retry "
                    + (retries + 1) + " of " + MAX_RATE_LIMIT_RETRIES + " in " + waitMs + " ms");
            Thread.sleep(waitMs);
        }
    }

    private Run execute(Question question, String endpoint, int repetition) {
        recorder.clear();
        long startedAtNanos = System.nanoTime();
        Response response = null;
        String clientError = null;
        try {
            response = CHAT.equals(endpoint)
                    ? given().contentType(ContentType.JSON).accept(ContentType.JSON)
                            .body(new ChatRequestDTO(UUID.randomUUID().toString(), question.question(), AiProvider.GEMINI))
                            .when().post("/api/chat")
                    : given().contentType(ContentType.JSON).accept(ContentType.JSON)
                            .body(new KnowledgeQuestionDTO(question.question()))
                            .when().post("/api/knowledge/answer");
        } catch (RuntimeException exception) {
            clientError = exception.getClass().getName() + ": " + exception.getMessage();
        }
        long latencyMs = (System.nanoTime() - startedAtNanos) / 1_000_000;

        List<ToolCall> tools = recorder.toolCalls();
        List<String> retrieved = recorder.retrievedIds();
        List<String> modelErrors = recorder.errors();
        if (response == null || response.statusCode() != 200) {
            String error = clientError != null ? clientError : serverError(response);
            boolean rateLimited = recorder.rateLimited() || RATE_LIMITED.matcher(error).find()
                    || modelErrors.stream().anyMatch(message -> RATE_LIMITED.matcher(message).find());
            if (!modelErrors.isEmpty()) {
                error = error + " | model error: " + String.join(" | ", modelErrors);
            }
            return new Run(question.id(), question.category(), endpoint, repetition, 0,
                    rateLimited ? Status.RATE_LIMITED : Status.ERROR, Map.of(), tools, retrieved, List.of(),
                    latencyMs, recorder.modelCalls(), recorder.inputTokens(), recorder.outputTokens(), null,
                    truncate(error, 1000));
        }

        String answer;
        List<KnowledgeSourceDTO> sources;
        if (CHAT.equals(endpoint)) {
            BusinessAnalysisDTO analysis = response.as(BusinessAnalysisDTO.class);
            answer = Stream.of(Stream.of(analysis.summary()), analysis.insights().stream(),
                            analysis.recommendations().stream())
                    .flatMap(lines -> lines).filter(Objects::nonNull).collect(Collectors.joining("\n"));
            sources = analysis.sources();
        } else {
            KnowledgeAnswerDTO knowledge = response.as(KnowledgeAnswerDTO.class);
            answer = knowledge.answer();
            sources = knowledge.sources();
        }
        List<String> sourceIds = sources.stream().map(EvaluationLiveTest::sourceId).toList();
        Map<String, Check> checks = checks(question, endpoint, tools, retrieved, sourceIds, answer);
        boolean passed = checks.values().stream().allMatch(Check::passed);
        return new Run(question.id(), question.category(), endpoint, repetition, 0, passed ? Status.PASS : Status.FAIL,
                checks, tools, retrieved, sourceIds, latencyMs, recorder.modelCalls(), recorder.inputTokens(),
                recorder.outputTokens(), answer, null);
    }

    // Only the checks a question defines are run; the tools check applies to /api/chat only.
    private static Map<String, Check> checks(Question question, String endpoint, List<ToolCall> tools,
                                             List<String> retrieved, List<String> sourceIds, String answer) {
        Map<String, Check> checks = new LinkedHashMap<>();
        List<String> called = tools.stream().map(ToolCall::name).toList();
        if (CHAT.equals(endpoint) && !(question.expectedTools().isEmpty() && question.anyOfTools().isEmpty()
                && question.forbiddenTools().isEmpty())) {
            List<String> missing = question.expectedTools().stream().filter(tool -> !called.contains(tool)).toList();
            boolean anyOf = question.anyOfTools().isEmpty() || question.anyOfTools().stream().anyMatch(called::contains);
            List<String> forbidden = question.forbiddenTools().stream().filter(called::contains).toList();
            checks.put("tools", new Check(missing.isEmpty() && anyOf && forbidden.isEmpty(),
                    "called " + called + (missing.isEmpty() ? "" : ", missing " + missing)
                            + (anyOf ? "" : ", none of " + question.anyOfTools())
                            + (forbidden.isEmpty() ? "" : ", forbidden " + forbidden)));
        }
        if (!question.expectedRuleIds().isEmpty() || !question.anyOfRuleIds().isEmpty()) {
            checks.put("retrieval", ruleCheck(question, retrieved));
            checks.put("citation", ruleCheck(question, sourceIds));
        }
        String text = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        if (!question.mustMention().isEmpty()) {
            List<String> missing = question.mustMention().stream()
                    .filter(entity -> !text.contains(entity.toLowerCase(Locale.ROOT))).toList();
            checks.put("mention", new Check(missing.isEmpty(), missing.isEmpty() ? "all present" : "missing " + missing));
        }
        if (!question.mustNotMention().isEmpty()) {
            List<String> present = question.mustNotMention().stream()
                    .filter(entity -> text.contains(entity.toLowerCase(Locale.ROOT))).toList();
            checks.put("notMention", new Check(present.isEmpty(), present.isEmpty() ? "none present" : "present " + present));
        }
        if (question.insufficient() != null) {
            if (KNOWLEDGE.equals(endpoint)) {
                boolean insufficient = KnowledgeAnswerService.INSUFFICIENT.equals(answer) && sourceIds.isEmpty();
                checks.put("insufficient", new Check(insufficient == question.insufficient(),
                        (insufficient ? "INSUFFICIENT" : "answered") + ", expected "
                                + (question.insufficient() ? "INSUFFICIENT" : "an answer")));
            } else if (question.insufficient()) {
                boolean ruleInText = RULE_ID.matcher(answer == null ? "" : answer).find();
                checks.put("insufficient", new Check(sourceIds.isEmpty() && !ruleInText,
                        "sources " + sourceIds + (ruleInText ? ", rule id in text" : "")));
            }
        }
        return checks;
    }

    // Every expected id must be matched and, when listed, at least one any-of id; "REF-*" matches any REF rule.
    private static Check ruleCheck(Question question, List<String> ids) {
        List<String> missing = question.expectedRuleIds().stream()
                .filter(expected -> ids.stream().noneMatch(id -> ruleMatches(expected, id))).toList();
        boolean anyOf = question.anyOfRuleIds().isEmpty() || question.anyOfRuleIds().stream()
                .anyMatch(expected -> ids.stream().anyMatch(id -> ruleMatches(expected, id)));
        return new Check(missing.isEmpty() && anyOf, "got " + ids + (missing.isEmpty() ? "" : ", missing " + missing)
                + (anyOf ? "" : ", none of " + question.anyOfRuleIds()));
    }

    private static boolean ruleMatches(String expected, String id) {
        return expected.endsWith("*") ? id.startsWith(expected.substring(0, expected.length() - 1)) : id.equals(expected);
    }

    private static String sourceId(KnowledgeSourceDTO source) {
        return source.ruleId() != null ? source.ruleId() : source.source() + "#preamble";
    }

    // The test profile's 500 body holds "Error id …, <class>: <message>" and the stack trace with its causes.
    private String serverError(Response response) {
        String body = response.asString();
        try {
            JsonNode node = objectMapper.readTree(body);
            String details = node.path("details").asText("");
            List<String> causes = node.path("stack").asText("").lines()
                    .filter(line -> line.startsWith("Caused by:")).toList();
            String error = "HTTP " + response.statusCode() + " " + details;
            return causes.isEmpty() ? error : error + " | root cause: " + causes.getLast();
        } catch (IOException exception) {
            return "HTTP " + response.statusCode() + " " + body;
        }
    }

    private Report report(List<Question> questions, List<Run> runs, int repeat, long delayMs, long rateLimitWaitMs,
                          long chunksAfterStartup, long chunkCount, Instant started, Duration wallClock) {
        List<Item> items = new ArrayList<>();
        for (Question question : questions) {
            for (String endpoint : question.endpoints()) {
                List<Run> itemRuns = runs.stream()
                        .filter(run -> run.id().equals(question.id()) && run.endpoint().equals(endpoint)).toList();
                items.add(item(question, endpoint, itemRuns));
            }
        }
        Map<String, Totals> byCategory = new LinkedHashMap<>();
        for (Item item : items) {
            byCategory.computeIfAbsent(item.category(), category -> new Totals()).add(item);
        }
        Totals overall = new Totals();
        items.forEach(overall::add);
        long inputTokens = runs.stream().mapToLong(Run::inputTokens).sum();
        long outputTokens = runs.stream().mapToLong(Run::outputTokens).sum();
        int modelCalls = runs.stream().mapToInt(Run::modelCalls).sum();
        return new Report(modelId, started.toString(), repeat, delayMs, rateLimitWaitMs, questions.size(), runs.size(),
                chunksAfterStartup, chunkCount, wallClock.toSeconds(), modelCalls, inputTokens, outputTokens,
                overall.retries, overall, byCategory, items, runs);
    }

    // pass = every scored run passed, flaky = some, fail = none; errors and rate limits are not scored.
    private static Item item(Question question, String endpoint, List<Run> runs) {
        long scored = runs.stream().filter(run -> run.status() == Status.PASS || run.status() == Status.FAIL).count();
        long passes = runs.stream().filter(run -> run.status() == Status.PASS).count();
        long errors = runs.stream().filter(run -> run.status() == Status.ERROR).count();
        long rateLimited = runs.stream().filter(run -> run.status() == Status.RATE_LIMITED).count();
        String result;
        if (scored == 0) {
            result = errors > 0 ? "error" : "rate-limited";
        } else {
            result = passes == scored ? "pass" : passes == 0 ? "fail" : "flaky";
        }
        Set<String> failedChecks = new LinkedHashSet<>();
        runs.forEach(run -> run.checks().forEach((name, check) -> {
            if (!check.passed()) {
                failedChecks.add(name);
            }
        }));
        List<Long> latencies = runs.stream().map(Run::latencyMs).sorted().toList();
        return new Item(question.id(), question.category(), endpoint, question.question(), result, passes, scored,
                errors, rateLimited, runs.stream().mapToLong(Run::retries).sum(), List.copyOf(failedChecks),
                latencies.get(latencies.size() / 2),
                runs.stream().mapToLong(Run::inputTokens).sum(), runs.stream().mapToLong(Run::outputTokens).sum());
    }

    private static String markdown(Report report) {
        StringBuilder md = new StringBuilder();
        md.append("# Evaluation report\n\n");
        md.append("- Model: ").append(report.modelId()).append('\n');
        md.append("- Started: ").append(report.started()).append('\n');
        md.append("- Repetitions: ").append(report.repetitions()).append(", pause ").append(report.delayMs())
                .append(" ms\n");
        md.append("- Rate-limit retries: ").append(report.retries()).append(" (wait ").append(report.rateLimitWaitMs())
                .append(" ms, at most ").append(MAX_RATE_LIMIT_RETRIES).append(" per run)\n");
        md.append("- Questions: ").append(report.questions()).append(", runs: ").append(report.runCount()).append('\n');
        md.append("- knowledge_chunks: ").append(report.chunksAfterStartup()).append(" after startup, ")
                .append(report.chunkCount()).append(" at the start of the run\n");
        md.append("- Wall clock: ").append(report.wallClockSeconds()).append(" s\n");
        md.append("- Chat model calls: ").append(report.modelCalls()).append(", input tokens: ")
                .append(report.inputTokens()).append(", output tokens: ").append(report.outputTokens())
                .append(" (rate-limited attempts included)\n\n");

        md.append("## Totals\n\n| category | items | pass | flaky | fail | error | rate-limited | error runs | "
                + "rate-limited runs | retries |\n|---|---|---|---|---|---|---|---|---|---|\n");
        report.byCategory().forEach((category, totals) -> md.append(totalsRow(category, totals)));
        md.append(totalsRow("**overall**", report.overall())).append('\n');

        md.append("## Questions\n\n| id | endpoint | result | passed | errors | rate-limited | retries | failed checks | "
                + "median ms | tokens in/out |\n|---|---|---|---|---|---|---|---|---|---|\n");
        for (Item item : report.items()) {
            md.append("| ").append(item.id()).append(" | ").append(item.endpoint()).append(" | ").append(item.result())
                    .append(" | ").append(item.passes()).append('/').append(item.scored()).append(" | ")
                    .append(item.errors()).append(" | ").append(item.rateLimited()).append(" | ")
                    .append(item.retries()).append(" | ")
                    .append(String.join(", ", item.failedChecks())).append(" | ").append(item.medianLatencyMs())
                    .append(" | ").append(item.inputTokens()).append('/').append(item.outputTokens()).append(" |\n");
        }

        md.append("\n## Runs that did not pass\n\nOne run per question and endpoint: the first that failed or errored.\n");
        for (Item item : report.items()) {
            report.runs().stream()
                    .filter(run -> run.id().equals(item.id()) && run.endpoint().equals(item.endpoint())
                            && run.status() != Status.PASS)
                    .findFirst()
                    .ifPresent(run -> appendRun(md, item, run));
        }
        return md.toString();
    }

    private static String totalsRow(String label, Totals totals) {
        return "| " + label + " | " + totals.items + " | " + totals.pass + " | " + totals.flaky + " | " + totals.fail
                + " | " + totals.error + " | " + totals.rateLimited + " | " + totals.errorRuns + " | "
                + totals.rateLimitedRuns + " | " + totals.retries + " |\n";
    }

    private static void appendRun(StringBuilder md, Item item, Run run) {
        md.append("\n### ").append(item.id()).append(' ').append(item.endpoint()).append(" (").append(item.result())
                .append("), run ").append(run.repetition()).append(": ").append(run.status()).append("\n\n");
        md.append("- Question: ").append(item.question()).append('\n');
        run.checks().forEach((name, check) -> md.append("- ").append(check.passed() ? "pass " : "FAIL ").append(name)
                .append(": ").append(check.detail()).append('\n'));
        md.append("- Tools: ").append(run.tools().stream().map(tool -> tool.name() + " " + tool.arguments()).toList())
                .append('\n');
        md.append("- Retrieved: ").append(run.retrievedIds()).append('\n');
        md.append("- Sources: ").append(run.sourceIds()).append('\n');
        md.append("- Latency: ").append(run.latencyMs()).append(" ms, model calls: ").append(run.modelCalls())
                .append(", rate-limit retries: ").append(run.retries()).append('\n');
        if (run.error() != null) {
            md.append("- Error: ").append(run.error()).append('\n');
        }
        if (run.answer() != null) {
            md.append("- Answer:\n\n").append(run.answer().lines().map(line -> "  > " + line)
                    .collect(Collectors.joining("\n"))).append('\n');
        }
    }

    private List<Question> readQuestions() throws IOException {
        try (InputStream file = EvaluationLiveTest.class.getResourceAsStream("/eval/questions.json")) {
            assertTrue(file != null, "eval/questions.json is missing");
            return objectMapper.readValue(file, new TypeReference<List<Question>>() {
            });
        }
    }

    private static String redact(String text) {
        String key = System.getenv("GEMINI_API_KEY");
        return key == null || key.isBlank() || text == null ? text : text.replace(key, REDACTED);
    }

    private static boolean keyIn(Path file) throws IOException {
        String key = System.getenv("GEMINI_API_KEY");
        return key != null && !key.isBlank() && Files.exists(file) && Files.readString(file).contains(key);
    }

    private static String truncate(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length) + "…";
    }

    record Question(String id, String category, List<String> endpoints, String question, List<String> expectedTools,
                    List<String> anyOfTools, List<String> forbiddenTools, List<String> expectedRuleIds,
                    List<String> anyOfRuleIds, List<String> mustMention, List<String> mustNotMention,
                    Boolean insufficient, String basis) {

        Question {
            endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
            expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
            anyOfTools = anyOfTools == null ? List.of() : List.copyOf(anyOfTools);
            forbiddenTools = forbiddenTools == null ? List.of() : List.copyOf(forbiddenTools);
            expectedRuleIds = expectedRuleIds == null ? List.of() : List.copyOf(expectedRuleIds);
            anyOfRuleIds = anyOfRuleIds == null ? List.of() : List.copyOf(anyOfRuleIds);
            mustMention = mustMention == null ? List.of() : List.copyOf(mustMention);
            mustNotMention = mustNotMention == null ? List.of() : List.copyOf(mustNotMention);
        }
    }

    enum Status {
        PASS, FAIL, ERROR, RATE_LIMITED
    }

    record Check(boolean passed, String detail) {
    }

    record ToolCall(String name, String arguments) {
    }

    // modelCalls and tokens include the rate-limited attempts before the last one.
    record Run(String id, String category, String endpoint, int repetition, int retries, Status status,
               Map<String, Check> checks, List<ToolCall> tools, List<String> retrievedIds, List<String> sourceIds,
               long latencyMs, int modelCalls, long inputTokens, long outputTokens, String answer, String error) {

        Run afterRetries(int retries, int earlierCalls, long earlierInputTokens, long earlierOutputTokens) {
            return new Run(id, category, endpoint, repetition, retries, status, checks, tools, retrievedIds, sourceIds,
                    latencyMs, modelCalls + earlierCalls, inputTokens + earlierInputTokens,
                    outputTokens + earlierOutputTokens, answer, error);
        }
    }

    record Item(String id, String category, String endpoint, String question, String result, long passes, long scored,
                long errors, long rateLimited, long retries, List<String> failedChecks, long medianLatencyMs,
                long inputTokens, long outputTokens) {
    }

    record Report(String modelId, String started, int repetitions, long delayMs, long rateLimitWaitMs, int questions,
                  int runCount, long chunksAfterStartup, long chunkCount, long wallClockSeconds, int modelCalls,
                  long inputTokens, long outputTokens, long retries, Totals overall, Map<String, Totals> byCategory,
                  List<Item> items, List<Run> runs) {
    }

    static class Totals {
        public int items;
        public int pass;
        public int flaky;
        public int fail;
        public int error;
        public int rateLimited;
        public long errorRuns;
        public long rateLimitedRuns;
        public long retries;

        void add(Item item) {
            items++;
            switch (item.result()) {
                case "pass" -> pass++;
                case "flaky" -> flaky++;
                case "fail" -> fail++;
                case "error" -> error++;
                default -> rateLimited++;
            }
            errorRuns += item.errors();
            rateLimitedRuns += item.rateLimited();
            retries += item.retries();
        }
    }

    // Replaces the %test placeholder key and chat model id with the real ones from the environment, removes the
    // ChatResourceTest replacement of GeminiAiService, indexes the knowledge base at startup so HTTP requests see
    // committed chunks, and writes the log to a file the run can check for the key.
    public static class RealGemini implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.langchain4j.ai.gemini.business-gemini.api-key", "${GEMINI_API_KEY}",
                    "quarkus.langchain4j.ai.gemini.business-gemini.chat-model.model-id", "${GEMINI_MODEL}",
                    "quarkus.arc.exclude-types", "com.aibusinessassistant.chat.TestGeminiAiService",
                    "app.knowledge-base.index-at-startup", "true",
                    "quarkus.log.file.enabled", "true",
                    "quarkus.log.file.path", LOG_FILE.toString());
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(ModelCallRecorder.class);
        }
    }

    // Keeps, per question, the tool requests, the chunk ids the model read, token usage and model errors;
    // enabled only by RealGemini. Questions run one at a time, so clear() separates them.
    @Alternative
    @ApplicationScoped
    public static class ModelCallRecorder implements ChatModelListener {

        private final List<ToolCall> toolCalls = new CopyOnWriteArrayList<>();
        private final Set<String> retrievedIds = new LinkedHashSet<>();
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private volatile boolean rateLimited;
        private volatile int modelCalls;
        private volatile long inputTokens;
        private volatile long outputTokens;

        @Override
        public synchronized void onRequest(ChatModelRequestContext requestContext) {
            for (ChatMessage message : requestContext.chatRequest().messages()) {
                String text = null;
                if (message instanceof ToolExecutionResultMessage result) {
                    text = result.text();
                } else if (message instanceof UserMessage user && user.hasSingleText()) {
                    text = user.singleText();
                }
                if (text != null) {
                    Matcher matcher = CHUNK_ID.matcher(text);
                    while (matcher.find()) {
                        retrievedIds.add(matcher.group(1));
                    }
                }
            }
        }

        @Override
        public synchronized void onResponse(ChatModelResponseContext responseContext) {
            modelCalls++;
            AiMessage message = responseContext.chatResponse().aiMessage();
            if (message != null && message.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : message.toolExecutionRequests()) {
                    toolCalls.add(new ToolCall(request.name(), request.arguments()));
                }
            }
            TokenUsage usage = responseContext.chatResponse().tokenUsage();
            if (usage != null) {
                inputTokens += usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
                outputTokens += usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
            }
        }

        @Override
        public synchronized void onError(ChatModelErrorContext errorContext) {
            Throwable error = errorContext.error();
            rateLimited = rateLimited || error instanceof RateLimitException;
            errors.add(redact(error.getClass().getName() + ": " + error.getMessage()));
        }

        public synchronized void clear() {
            toolCalls.clear();
            retrievedIds.clear();
            errors.clear();
            rateLimited = false;
            modelCalls = 0;
            inputTokens = 0;
            outputTokens = 0;
        }

        public synchronized List<ToolCall> toolCalls() {
            return List.copyOf(toolCalls);
        }

        public synchronized List<String> retrievedIds() {
            return List.copyOf(retrievedIds);
        }

        public synchronized List<String> errors() {
            return List.copyOf(errors);
        }

        public boolean rateLimited() {
            return rateLimited;
        }

        public int modelCalls() {
            return modelCalls;
        }

        public long inputTokens() {
            return inputTokens;
        }

        public long outputTokens() {
            return outputTokens;
        }
    }
}
