package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;

class KnowledgeEvidenceTest {

    private static final RetrievedChunk REF_1 = new RetrievedChunk("refund-policy.md",
            "Return window and return requests", "REF-1",
            "Refund and Return Policy > Return window and return requests\n**REF-1** Returns within 30 days.", 0.75);
    private static final RetrievedChunk PREAMBLE = new RetrievedChunk("refund-policy.md", "Refund and Return Policy",
            null, "Refund and Return Policy > Refund and Return Policy\n- Policy ID: POL-REF", 0.70);
    private static final RetrievedChunk REF_10 = new RetrievedChunk("refund-policy.md", "Refund processing time",
            "REF-10", "Refund and Return Policy > Refund processing time\n**REF-10** Refunds within 5 business days.",
            0.68);
    private static final RetrievedChunk INV_3 = new RetrievedChunk("inventory-policy.md", "Stock status", "INV-3",
            "Inventory Policy > Stock status\n**INV-3** A product is critical when stock is very low.", 0.72);

    private final KnowledgeEvidence evidence = new KnowledgeEvidence(List.of(REF_1, PREAMBLE, REF_10));

    @Test
    void formatGivesOneChunkBlockPerChunkInRetrievalOrder() {
        assertEquals("""
                <chunk id="REF-1" source="refund-policy.md" section="Return window and return requests">
                Refund and Return Policy > Return window and return requests
                **REF-1** Returns within 30 days.
                </chunk>

                <chunk id="refund-policy.md#preamble" source="refund-policy.md" section="Refund and Return Policy">
                Refund and Return Policy > Refund and Return Policy
                - Policy ID: POL-REF
                </chunk>

                <chunk id="REF-10" source="refund-policy.md" section="Refund processing time">
                Refund and Return Policy > Refund processing time
                **REF-10** Refunds within 5 business days.
                </chunk>""", evidence.format());
    }

    @Test
    void chunkIdIsTheRuleIdOrThePreambleOfTheSource() {
        assertEquals("REF-1", KnowledgeEvidence.chunkId(REF_1));
        assertEquals("refund-policy.md#preamble", KnowledgeEvidence.chunkId(PREAMBLE));
    }

    @Test
    void sourcesFollowRetrievalOrderWithoutDuplicates() {
        assertEquals(List.of(source(REF_1), source(PREAMBLE), source(REF_10)),
                evidence.sourcesFor(List.of("REF-10", "refund-policy.md#preamble", "REF-1", "REF-10")));
    }

    @Test
    void unknownIdsAreDropped() {
        // INV-3 is a real chunk id, but of another evidence.
        assertEquals(List.of(source(REF_10)), evidence.sourcesFor(List.of("REF-99", "INV-3", "REF-10")));
        assertEquals(List.of(), evidence.sourcesFor(List.of("REF-99", "INV-3")));
        assertEquals(List.of(), evidence.sourcesFor(Arrays.asList("ref-1", null)));
    }

    @Test
    void preambleSourceHasNoRuleId() {
        assertEquals(List.of(new KnowledgeSourceDTO("refund-policy.md", "Refund and Return Policy", null)),
                evidence.sourcesFor(List.of("refund-policy.md#preamble")));
    }

    @Test
    void noOrNullCitationsGiveNoSources() {
        assertEquals(List.of(), evidence.sourcesFor(List.of()));
        assertEquals(List.of(), evidence.sourcesFor(null));
    }

    @Test
    void emptyRetrievalIsEmptyEvidence() {
        assertTrue(new KnowledgeEvidence(List.of()).isEmpty());
        assertFalse(new KnowledgeEvidence(List.of(INV_3)).isEmpty());
        assertEquals("", new KnowledgeEvidence(List.of()).format());
    }

    private static KnowledgeSourceDTO source(RetrievedChunk chunk) {
        return new KnowledgeSourceDTO(chunk.source(), chunk.section(), chunk.ruleId());
    }
}
