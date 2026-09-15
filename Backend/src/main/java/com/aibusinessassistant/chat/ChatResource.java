package com.aibusinessassistant.chat;

import com.aibusinessassistant.chat.ai.BusinessAnalysisService;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.chat.dto.ChatRequestDTO;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/api/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Slf4j
public class ChatResource {

    private final BusinessAnalysisService businessAnalysisService;

    @POST
    public BusinessAnalysisDTO chat(ChatRequestDTO request) {
        String query = request.query();
        log.info("AI request received: business-analysis (queryLength={})", queryLength(query));
        long startedAt = System.nanoTime();

        try {
            BusinessAnalysisDTO response = businessAnalysisService.chat(query);
            log.info("AI request completed: business-analysis (durationMs={})", elapsedMilliseconds(startedAt));
            return response;
        } catch (RuntimeException exception) {
            log.error("AI request failed: business-analysis (durationMs={})", elapsedMilliseconds(startedAt), exception);
            throw exception;
        }
    }

    private static Integer queryLength(String query) {
        return query == null ? null : query.length();
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
