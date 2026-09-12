package com.aibusinessassistant.chat;

import com.aibusinessassistant.chat.ai.BusinessAnalysisService;
import com.aibusinessassistant.chat.ai.ChatService;
import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.chat.dto.ChatRequestDTO;
import com.aibusinessassistant.chat.dto.ChatResponseDTO;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class ChatResource {

    private final ChatService chatService;
    private final BusinessAnalysisService businessAnalysisService;

    @POST
    public ChatResponseDTO chat(ChatRequestDTO request) {
        return new ChatResponseDTO(chatService.chat(request.query()));
    }

    @POST
    @Path("/business-analysis")
    public BusinessAnalysisDTO businessAnalysisChat(ChatRequestDTO request) {
        return businessAnalysisService.analyze(request.query());
    }
}
