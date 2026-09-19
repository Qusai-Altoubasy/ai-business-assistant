package com.aibusinessassistant.chat.ai;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.common.tools.CommonTools;
import com.aibusinessassistant.customer.tools.CustomerTools;
import com.aibusinessassistant.order.tools.SalesTools;
import com.aibusinessassistant.product.tools.InventoryTools;
import java.util.UUID;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(
        tools = {
                InventoryTools.class,
                SalesTools.class,
                CustomerTools.class,
                CommonTools.class
        })
public interface BusinessAnalysisService {

    @SystemMessage(fromResource = "prompts/business-analysis-system.txt")
    @UserMessage("{query}")
    BusinessAnalysisDTO chat(@MemoryId UUID conversationId, String query);
}
