package com.afshin.nlrag.a2a;

@FunctionalInterface
public interface A2aAgentTurn {

    String complete(String userMessage);
}
