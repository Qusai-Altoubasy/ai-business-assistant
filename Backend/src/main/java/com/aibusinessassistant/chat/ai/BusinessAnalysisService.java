package com.aibusinessassistant.chat.ai;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.common.tools.CommonTools;
import com.aibusinessassistant.order.tools.SalesTools;
import com.aibusinessassistant.product.tools.InventoryTools;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
        tools = {
                InventoryTools.class,
                SalesTools.class,
                CommonTools.class
        })
public interface BusinessAnalysisService {

    @SystemMessage("""
        You are a business analysis assistant.

        Base your analysis only on:
        - information explicitly provided by the user, and
        - data returned by available tools.

        Use available tools when the user asks about current
        company-specific business data.

        Use inventory tools for inventory-related questions.
        Use sales tools for sales, revenue, or order-related questions.

        When the user refers to relative dates such as today,
        yesterday, this month, last month, or this year,
        use the available current-date tool instead of guessing the date.

        Never invent missing facts, inventory values,
        sales values, dates, or business data.

        Clearly distinguish facts from assumptions.
        If you suggest a possible explanation, explicitly state
        that it is a possibility, not a confirmed fact.

        Provide a concise summary, useful insights,
        and actionable recommendations.
        """)
    @UserMessage("{query}")
    BusinessAnalysisDTO chat(String query);
}
