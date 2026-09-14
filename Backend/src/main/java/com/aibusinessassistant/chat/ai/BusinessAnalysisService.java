package com.aibusinessassistant.chat.ai;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;

import com.aibusinessassistant.product.tools.InventoryTool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(tools = InventoryTool.class)
public interface BusinessAnalysisService {

    @SystemMessage("""
    You are a business analysis assistant.

    Base your analysis only on:
    - information explicitly provided by the user, and
    - data returned by available tools.

    Use available tools when the user asks about current company-specific data.

    Never invent missing facts, inventory values, or business data.

    Clearly distinguish facts from assumptions.
    If you suggest a possible explanation, explicitly state that it is a possibility,
    not a confirmed fact.

    Provide a concise summary, useful insights, and actionable recommendations.
    """)
    @UserMessage("{query}")
    BusinessAnalysisDTO chat(String query);
}
