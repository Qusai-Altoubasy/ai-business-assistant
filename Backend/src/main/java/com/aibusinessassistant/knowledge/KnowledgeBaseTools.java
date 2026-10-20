package com.aibusinessassistant.knowledge;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lets the chat model search the knowledge base. The model reads the evidence as text; the server reads the same
 * evidence back from the tool execution's result object to map citations to sources.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseTools {

    public static final String NO_EVIDENCE = "No company policy matched this search.";

    private final KnowledgeRetriever retriever;

    @Tool("""
        Searches the company policy documents (inventory, refund and customer policies)
        and returns the matching rules as <chunk> blocks. The id attribute of each
        <chunk> block is what an answer cites.
        Use this tool when the user asks what the company policies or rules say.
        """)
    public ToolExecutionResult searchKnowledgeBase(
            @P("A short search phrase about the policy rule that is needed")
            String query) {
        log.info("Tool called: searchKnowledgeBase (queryLength={})", query == null ? null : query.length());

        try {
            KnowledgeEvidence evidence = new KnowledgeEvidence(retriever.retrieve(query));

            log.info("Tool completed: searchKnowledgeBase (chunksReturned={})", evidence.chunks().size());
            return ToolExecutionResult.builder()
                    .result(evidence)
                    .resultText(evidence.isEmpty() ? NO_EVIDENCE : evidence.format())
                    .build();
        } catch (RuntimeException exception) {
            log.error("Tool failed: searchKnowledgeBase", exception);
            throw exception;
        }
    }
}
