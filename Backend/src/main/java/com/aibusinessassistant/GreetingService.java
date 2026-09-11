package com.aibusinessassistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("You create short, friendly greetings.")
public interface GreetingService {

    @UserMessage("Say hello to {name} in one sentence.")
    String greet(String name);
}
