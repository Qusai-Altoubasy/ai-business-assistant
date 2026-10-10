package com.aibusinessassistant.chat;

import java.util.UUID;

import com.aibusinessassistant.chat.dto.BusinessAnalysisDTO;
import com.aibusinessassistant.chat.dto.ChatRequestDTO;
import com.aibusinessassistant.chat.dto.ChatStreamEventDTO;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import org.jboss.resteasy.reactive.RestStreamElementType;

import jakarta.ws.rs.BadRequestException;
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

    @POST
    public BusinessAnalysisDTO chat(ChatRequestDTO request) {
        UUID conversationId = validateAndParseConversationId(request);
        return chatService.chat(conversationId, request.query(), request.provider());
    }

    @POST
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Blocking // Memory and history persistence use blocking JDBC.
    public Multi<ChatStreamEventDTO> chatStream(ChatRequestDTO request) {
        UUID conversationId = validateAndParseConversationId(request);
        return chatService.chatStream(conversationId, request.query(), request.provider());
    }

    private static UUID validateAndParseConversationId(ChatRequestDTO request) {
        if (request == null || request.conversationId() == null || request.conversationId().isBlank()) {
            throw new BadRequestException("conversationId is required");
        }
        try {
            return UUID.fromString(request.conversationId());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("conversationId must be a UUID", exception);
        }
    }
}
