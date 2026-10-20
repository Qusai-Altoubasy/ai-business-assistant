package com.aibusinessassistant.chat.ai;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.knowledge.KnowledgeEvidence;
import com.aibusinessassistant.knowledge.dto.KnowledgeSourceDTO;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class GeminiBusinessAnalysisService implements BusinessAnalysisService {

    private final GeminiAiService delegate;

    // Only chunks retrieved by this request's searches can become sources, so an id the model invents or
    // remembers from an earlier turn is dropped. The answer itself is kept: data questions cite nothing.
    @Override
    public BusinessAnalysisDTO chat(UUID conversationId, String query) {
        Result<BusinessAnalysisAiAnswer> result = delegate.chat(conversationId, query);
        BusinessAnalysisAiAnswer answer = result.content();

        List<KnowledgeEvidence> searches = result.toolExecutions().stream()
                .map(ToolExecution::resultObject)
                .filter(KnowledgeEvidence.class::isInstance)
                .map(KnowledgeEvidence.class::cast)
                .toList();
        KnowledgeEvidence evidence = new KnowledgeEvidence(searches.stream()
                .flatMap(search -> search.chunks().stream())
                .toList());
        List<KnowledgeSourceDTO> sources = evidence.sourcesFor(answer.citations());

        int citedIds = answer.citations() == null ? 0 : new HashSet<>(answer.citations()).size();
        long retrievedChunks = evidence.chunks().stream().map(KnowledgeEvidence::chunkId).distinct().count();
        log.info("AI sources resolved: business-analysis (conversationId={}, knowledgeSearches={}, retrievedChunks={}, "
                        + "validCitations={}, droppedCitations={})",
                conversationId, searches.size(), retrievedChunks, sources.size(), citedIds - sources.size());

        return new BusinessAnalysisDTO(answer.summary(), answer.insights(), answer.recommendations(), sources);
    }

    @Override
    public Multi<ChatEvent> chatStream(UUID conversationId, String query) {
        return delegate.chatStream(conversationId, query);
    }
}
