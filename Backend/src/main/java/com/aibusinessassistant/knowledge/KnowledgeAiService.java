package com.aibusinessassistant.knowledge;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

// No tools, and no chat memory: without NoChatMemoryProviderSupplier every call would share the "default" memory.
@ApplicationScoped
@RegisterAiService(
        modelName = "business-gemini",
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface KnowledgeAiService {

    @SystemMessage(fromResource = "prompts/knowledge-answer-system.txt")
    @UserMessage("""
            <context>
            {context}
            </context>
            <question>
            {question}
            </question>""")
    KnowledgeAiAnswer answer(String context, String question);
}
