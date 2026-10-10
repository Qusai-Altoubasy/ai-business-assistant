package com.aibusinessassistant.chat.ai;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import java.util.UUID;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;

public interface BusinessAnalysisService {

    BusinessAnalysisDTO chat(UUID conversationId, String query);

    Multi<ChatEvent> chatStream(UUID conversationId, String query);
}
