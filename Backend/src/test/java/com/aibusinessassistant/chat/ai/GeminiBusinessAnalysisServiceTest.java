package com.aibusinessassistant.chat.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.knowledge.KnowledgeEvidence;
import com.aibusinessassistant.knowledge.RetrievedChunk;
import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;

// A fake GeminiAiService returns built results, so no request reaches Gemini and no database is needed.
// The fake is an inner class, so it is never a CDI bean.
class GeminiBusinessAnalysisServiceTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final String SUMMARY = "Four products violate the inventory policy.";
    private static final List<String> INSIGHTS = List.of("Keyboard: 50 in stock, minimum 10, OVERSTOCK (INV-6).");
    private static final List<String> RECOMMENDATIONS = List.of("Do not restock Keyboard (INV-10).");

    private static final RetrievedChunk INV_8 = chunk("Inventory policy violations and the low-stock list", "INV-8");
    private static final RetrievedChunk INV_10 = chunk("Inventory policy violations and the low-stock list", "INV-10");
    private static final RetrievedChunk INV_11 = chunk("Restock target and restock quantity", "INV-11");

    @Test
    void aCitedRetrievedChunkBecomesASource() {
        BusinessAnalysisDTO response = chat(List.of("INV-8"), search(INV_8, INV_10));

        assertEquals(List.of(source(INV_8)), response.sources());
    }

    @Test
    void anInventedIdIsDropped() {
        BusinessAnalysisDTO response = chat(List.of("INV-8", "INV-99"), search(INV_8));

        assertEquals(List.of(source(INV_8)), response.sources());
    }

    @Test
    void chunksOfSeveralSearchesMergeWithoutDuplicates() {
        BusinessAnalysisDTO response = chat(List.of("INV-11", "INV-8"), search(INV_8, INV_10), search(INV_11, INV_8));

        assertEquals(List.of(source(INV_8), source(INV_11)), response.sources());
    }

    @Test
    void noSearchGivesNoSourcesEvenWhenTheModelCitesIds() {
        BusinessAnalysisDTO response = chat(List.of("INV-8"));

        assertEquals(List.of(), response.sources());
    }

    @Test
    void anotherToolsResultIsIgnored() {
        ToolExecution snapshot = execution("getInventorySnapshot",
                ToolExecutionResult.builder().result(List.of()).resultText("[]").build());

        BusinessAnalysisDTO response = chat(List.of("INV-8"), snapshot);

        assertEquals(List.of(), response.sources());
    }

    @Test
    void theAnswerIsKeptWhenNoCitationIsValid() {
        assertEquals(new BusinessAnalysisDTO(SUMMARY, INSIGHTS, RECOMMENDATIONS, List.of()),
                chat(List.of("INV-99"), search(INV_8)));
        assertEquals(new BusinessAnalysisDTO(SUMMARY, INSIGHTS, RECOMMENDATIONS, List.of()), chat(null));
    }

    private static BusinessAnalysisDTO chat(List<String> citations, ToolExecution... executions) {
        BusinessAnalysisAiAnswer answer = new BusinessAnalysisAiAnswer(SUMMARY, INSIGHTS, RECOMMENDATIONS, citations);
        Result<BusinessAnalysisAiAnswer> result = Result.<BusinessAnalysisAiAnswer>builder()
                .content(answer)
                .toolExecutions(List.of(executions))
                .build();
        return new GeminiBusinessAnalysisService(new FakeGeminiAiService(result)).chat(CONVERSATION_ID, "question");
    }

    private static ToolExecution search(RetrievedChunk... chunks) {
        KnowledgeEvidence evidence = new KnowledgeEvidence(List.of(chunks));
        return execution("searchKnowledgeBase",
                ToolExecutionResult.builder().result(evidence).resultText(evidence.format()).build());
    }

    private static ToolExecution execution(String toolName, ToolExecutionResult result) {
        return ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name(toolName).arguments("{}").build())
                .result(result)
                .invocationContext(InvocationContext.builder().chatMemoryId(CONVERSATION_ID).build())
                .build();
    }

    private static RetrievedChunk chunk(String section, String ruleId) {
        return new RetrievedChunk("inventory-policy.md", section, ruleId, "**" + ruleId + "** Rule text.", 0.7);
    }

    private static KnowledgeSourceDTO source(RetrievedChunk chunk) {
        return new KnowledgeSourceDTO(chunk.source(), chunk.section(), chunk.ruleId());
    }

    private static class FakeGeminiAiService implements GeminiAiService {

        private final Result<BusinessAnalysisAiAnswer> result;

        FakeGeminiAiService(Result<BusinessAnalysisAiAnswer> result) {
            this.result = result;
        }

        @Override
        public Result<BusinessAnalysisAiAnswer> chat(UUID conversationId, String query) {
            return result;
        }

        @Override
        public Multi<ChatEvent> chatStream(UUID conversationId, String query) {
            return Multi.createFrom().empty();
        }
    }
}
