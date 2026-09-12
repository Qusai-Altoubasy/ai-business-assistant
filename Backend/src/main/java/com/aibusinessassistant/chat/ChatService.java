package com.aibusinessassistant.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("You are a helpful AI business assistant.")
public interface ChatService {

    @UserMessage("{query}")
    String chat(String query);
}
