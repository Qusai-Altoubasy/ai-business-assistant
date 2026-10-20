package com.aibusinessassistant.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aibusinessassistant.knowledge.dto.KnowledgeAnswerDTO;
import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;

// Fakes replace the retriever and the chat model, so no request reaches Gemini and no database is needed.
// The fakes are inner classes, so they are never CDI beans.
class KnowledgeAnswerServiceTest {

    private static final String QUESTION = "How long does it take to receive a refund?";
    private static final RetrievedChunk REF_1 = new RetrievedChunk("refund-policy.md",
            "Return window and return requests", "REF-1",
            "Refund and Return Policy > Return window and return requests\n**REF-1** Returns within 30 days.", 0.71);
    private static final RetrievedChunk REF_10 = new RetrievedChunk("refund-policy.md", "Refund processing time",
            "REF-10", "Refund and Return Policy > Refund processing time\n**REF-10** Refunds within 5 business days.",
            0.77);

    private final List<String> retrievedQuestions = new ArrayList<>();
    private final List<String> modelQuestions = new ArrayList<>();
    private final List<String> modelContexts = new ArrayList<>();

    @Test
    void theModelReadsTheFormattedEvidence() {
        service(List.of(REF_10, REF_1), new KnowledgeAiAnswer("Within 5 business days.", List.of("REF-10")))
                .answer(QUESTION);

        assertEquals(List.of(QUESTION), retrievedQuestions);
        assertEquals(List.of(QUESTION), modelQuestions);
        assertEquals(List.of(new KnowledgeEvidence(List.of(REF_10, REF_1)).format()), modelContexts);
    }

    @Test
    void noEvidenceIsInsufficientWithoutCallingTheModel() {
        KnowledgeAnswerDTO answer = service(List.of(), new KnowledgeAiAnswer("Invented policy.", List.of("REF-1")))
                .answer("What is our shipping policy?");

        assertEquals(new KnowledgeAnswerDTO(KnowledgeAnswerService.INSUFFICIENT, List.of()), answer);
        assertEquals(List.of(), modelQuestions);
    }

    @Test
    void anAnswerWithoutAValidCitationIsInsufficient() {
        KnowledgeAnswerDTO invented = service(List.of(REF_10),
                new KnowledgeAiAnswer("Refunds take 2 days.", List.of("REF-99", "INV-3"))).answer(QUESTION);
        KnowledgeAnswerDTO uncited = service(List.of(REF_10),
                new KnowledgeAiAnswer("Refunds take 2 days.", List.of())).answer(QUESTION);
        KnowledgeAnswerDTO nullCitations = service(List.of(REF_10),
                new KnowledgeAiAnswer("Refunds take 2 days.", null)).answer(QUESTION);

        KnowledgeAnswerDTO insufficient = new KnowledgeAnswerDTO(KnowledgeAnswerService.INSUFFICIENT, List.of());
        assertEquals(insufficient, invented);
        assertEquals(insufficient, uncited);
        assertEquals(insufficient, nullCitations);
        assertEquals(3, modelQuestions.size());
    }

    @Test
    void aSupportedAnswerKeepsOnlyTheValidCitationsAsSources() {
        KnowledgeAnswerDTO answer = service(List.of(REF_10, REF_1),
                new KnowledgeAiAnswer("Within 5 business days.", List.of("REF-99", "REF-10"))).answer(QUESTION);

        assertEquals("Within 5 business days.", answer.answer());
        assertEquals(List.of(new KnowledgeSourceDTO("refund-policy.md", "Refund processing time", "REF-10")),
                answer.sources());
    }

    private KnowledgeAnswerService service(List<RetrievedChunk> chunks, KnowledgeAiAnswer modelAnswer) {
        return new KnowledgeAnswerService(new FakeRetriever(chunks), new FakeAiService(modelAnswer));
    }

    private class FakeRetriever extends KnowledgeRetriever {

        private final List<RetrievedChunk> chunks;

        FakeRetriever(List<RetrievedChunk> chunks) {
            super(null, null, 0, 0);
            this.chunks = chunks;
        }

        @Override
        public List<RetrievedChunk> retrieve(String question) {
            retrievedQuestions.add(question);
            return chunks;
        }
    }

    private class FakeAiService implements KnowledgeAiService {

        private final KnowledgeAiAnswer modelAnswer;

        FakeAiService(KnowledgeAiAnswer modelAnswer) {
            this.modelAnswer = modelAnswer;
        }

        @Override
        public KnowledgeAiAnswer answer(String context, String question) {
            modelQuestions.add(question);
            modelContexts.add(context);
            return modelAnswer;
        }
    }
}
