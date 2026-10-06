package com.afshin.nlrag.controller;

import com.afshin.nlrag.model.AssistantAnswer;
import com.afshin.nlrag.retrieval.RagQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final RagQueryService ragQueryService;

    public AssistantController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @PostMapping("/ask")
    public AssistantAnswer ask(@Valid @RequestBody AskRequest request) {
        return ragQueryService.answer(request.question());
    }

    public record AskRequest(
            @NotBlank @Size(max = 4000) String question
    ) {
    }
}
