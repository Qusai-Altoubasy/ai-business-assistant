package com.aibusinessassistant.knowledge;

import java.util.HashSet;
import java.util.List;

import com.aibusinessassistant.knowledge.dto.KnowledgeAnswerDTO;
import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Answers a company-policy question from the knowledge base. An answer is returned only with the retrieved chunks
 * it cites; without evidence or without a valid citation the answer is INSUFFICIENT with no sources.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class KnowledgeAnswerService {

    public static final String INSUFFICIENT =
            "The company policies do not contain enough information to answer this question.";

    private final KnowledgeRetriever retriever;
    private final KnowledgeAiService aiService;

    public KnowledgeAnswerDTO answer(String question) {
        log.info("AI request received: knowledge-answer (questionLength={})", questionLength(question));
        long startedAtNanos = System.nanoTime();
        try {
            KnowledgeEvidence evidence = new KnowledgeEvidence(retriever.retrieve(question));
            if (evidence.isEmpty()) {
                // No evidence: the chat model is not called.
                logCompleted(0, 0, 0, true, startedAtNanos);
                return insufficient();
            }

            KnowledgeAiAnswer result = aiService.answer(evidence.format(), question);
            List<KnowledgeSourceDTO> sources = evidence.sourcesFor(result.citations());
            int citedIds = result.citations() == null ? 0 : new HashSet<>(result.citations()).size();
            logCompleted(evidence.chunks().size(), sources.size(), citedIds - sources.size(), false, startedAtNanos);

            // An answer that cites nothing valid is not supported by the evidence.
            return sources.isEmpty() ? insufficient() : new KnowledgeAnswerDTO(result.answer(), sources);
        } catch (RuntimeException exception) {
            log.error("AI request failed: knowledge-answer (durationMs={})", elapsedMilliseconds(startedAtNanos),
                    exception);
            throw exception;
        }
    }

    private static KnowledgeAnswerDTO insufficient() {
        return new KnowledgeAnswerDTO(INSUFFICIENT, List.of());
    }

    private static void logCompleted(int retrievedChunks, int validCitations, int droppedCitations,
                                     boolean modelSkipped, long startedAtNanos) {
        log.info("AI request completed: knowledge-answer (retrievedChunks={}, validCitations={}, droppedCitations={}, "
                        + "modelSkipped={}, durationMs={})",
                retrievedChunks, validCitations, droppedCitations, modelSkipped, elapsedMilliseconds(startedAtNanos));
    }

    private static Integer questionLength(String question) {
        return question == null ? null : question.length();
    }

    private static long elapsedMilliseconds(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
