package com.afshin.nlrag.controller;

import com.afshin.nlrag.agent.AgentController;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyConstraintTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void askRequestRejectsBlankQuestion() {
        assertThat(validator.validate(new AssistantController.AskRequest("")))
                .isNotEmpty();
        assertThat(validator.validate(new AssistantController.AskRequest("How long is the 30% ruling?")))
                .isEmpty();
    }

    @Test
    void chatRequestRejectsBlankMessage() {
        assertThat(validator.validate(new AgentController.ChatRequest("   ")))
                .isNotEmpty();
    }
}
