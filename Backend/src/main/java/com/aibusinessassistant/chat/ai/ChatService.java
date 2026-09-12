package com.aibusinessassistant.chat.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
    You are an AI business assistant.

    Answer business questions clearly and concisely.
    If you do not know the answer, say that you do not know.
    Do not invent business data.
    """)
public interface ChatService {

    @UserMessage("{query}")
    String chat(String query);
}
