package com.aibusinessassistant.knowledge;

import com.aibusinessassistant.knowledge.dto.KnowledgeAnswerDTO;
import com.aibusinessassistant.knowledge.dto.KnowledgeQuestionDTO;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/knowledge")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class KnowledgeResource {

    private final KnowledgeAnswerService knowledgeAnswerService;

    @POST
    @Path("/answer")
    public KnowledgeAnswerDTO answer(KnowledgeQuestionDTO request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new BadRequestException("question is required");
        }
        return knowledgeAnswerService.answer(request.question());
    }
}
