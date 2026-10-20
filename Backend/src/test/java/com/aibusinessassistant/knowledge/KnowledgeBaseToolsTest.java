package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.service.tool.ToolExecutionResult;

// A fake retriever replaces the embedding model and the database, so no request reaches Gemini.
// The fake is an inner class, so it is never a CDI bean.
class KnowledgeBaseToolsTest {

    private static final String QUERY = "inventory policy violations";
    private static final RetrievedChunk INV_8 = new RetrievedChunk("inventory-policy.md",
            "Inventory policy violations and the low-stock list", "INV-8",
            "Inventory Policy > Inventory policy violations and the low-stock list\n**INV-8** A product violates "
                    + "the inventory policy unless it is HEALTHY.", 0.74);
    private static final RetrievedChunk PREAMBLE = new RetrievedChunk("inventory-policy.md", "Inventory Policy", null,
            "Inventory Policy\n- Policy ID: POL-INV", 0.68);

    private final List<String> retrievedQueries = new ArrayList<>();

    @Test
    void evidenceIsReturnedAsFormattedTextAndAsTheResultObject() {
        ToolExecutionResult result = tools(List.of(INV_8, PREAMBLE)).searchKnowledgeBase(QUERY);

        KnowledgeEvidence expected = new KnowledgeEvidence(List.of(INV_8, PREAMBLE));
        assertEquals(expected.format(), result.resultText());
        assertEquals(expected, result.result());
    }

    @Test
    void emptyRetrievalReturnsNoEvidenceTextAndEmptyEvidence() {
        ToolExecutionResult result = tools(List.of()).searchKnowledgeBase(QUERY);

        assertEquals(KnowledgeBaseTools.NO_EVIDENCE, result.resultText());
        KnowledgeEvidence evidence = (KnowledgeEvidence) result.result();
        assertTrue(evidence.isEmpty());
    }

    @Test
    void theRetrieverReceivesTheToolQuery() {
        tools(List.of(INV_8)).searchKnowledgeBase(QUERY);

        assertEquals(List.of(QUERY), retrievedQueries);
    }

    private KnowledgeBaseTools tools(List<RetrievedChunk> chunks) {
        return new KnowledgeBaseTools(new FakeRetriever(chunks));
    }

    private class FakeRetriever extends KnowledgeRetriever {

        private final List<RetrievedChunk> chunks;

        FakeRetriever(List<RetrievedChunk> chunks) {
            super(null, null, 0, 0);
            this.chunks = chunks;
        }

        @Override
        public List<RetrievedChunk> retrieve(String question) {
            retrievedQueries.add(question);
            return chunks;
        }
    }
}
