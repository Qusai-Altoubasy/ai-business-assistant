package com.aibusinessassistant.chat;

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
    public ChatResponseDTO chat(ChatRequestDTO request) {
        return new ChatResponseDTO(chatService.chat(request.query()));
    }
}
