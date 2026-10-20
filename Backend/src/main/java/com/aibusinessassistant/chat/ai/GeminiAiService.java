package com.aibusinessassistant.chat.ai;

import java.util.UUID;

import com.aibusinessassistant.common.tools.CommonTools;
import com.aibusinessassistant.customer.tools.CustomerTools;
import com.aibusinessassistant.knowledge.KnowledgeBaseTools;
import com.aibusinessassistant.order.tools.SalesTools;
import com.aibusinessassistant.product.tools.InventoryTools;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(
        modelName = "business-gemini",
        chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class,
        tools = {InventoryTools.class, SalesTools.class, CustomerTools.class, CommonTools.class,
                KnowledgeBaseTools.class})
public interface GeminiAiService {

    // Returns Result so the adapter can read the knowledge-base evidence from the tool executions.
    @SystemMessage(fromResource = "prompts/business-analysis-gemini-system.txt")
    @UserMessage("{query}")
    Result<BusinessAnalysisAiAnswer> chat(@MemoryId UUID conversationId, String query);

    @SystemMessage(fromResource = "prompts/business-analysis-gemini-system.txt")
    @UserMessage("{query}")
    Multi<ChatEvent> chatStream(@MemoryId UUID conversationId, String query);
}
