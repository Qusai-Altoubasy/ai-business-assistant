package com.aibusinessassistant.chat.ai;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
    You are a business analysis assistant.

    Analyze only the information provided by the user.
    Do not invent missing facts or business data.

    Clearly distinguish facts from assumptions.
    If you suggest a possible explanation, explicitly state that it is a possibility,
    not a confirmed fact.

    Provide a concise summary, useful insights, and actionable recommendations.
    """)
public interface BusinessAnalysisService {

    @UserMessage("{query}")
    BusinessAnalysisDTO analyze(String query);
}
