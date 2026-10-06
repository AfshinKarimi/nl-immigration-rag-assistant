package com.afshin.nlrag.a2a;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pulls plain text out of A2A {@code Message.parts} (v0.2 {@code kind:text}
 * and v1 parts that use {@code text} as the member name).
 */
final class A2aTextExtractor {

    private A2aTextExtractor() {
    }

    static String fromMessage(JsonNode message) {
        if (message == null || message.isNull()) {
            return "";
        }
        JsonNode parts = message.get("parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            return textField(message.get("text"));
        }
        List<String> chunks = new ArrayList<>();
        for (JsonNode part : parts) {
            String piece = fromPart(part);
            if (!piece.isBlank()) {
                chunks.add(piece);
            }
        }
        return String.join("\n", chunks).trim();
    }

    private static String fromPart(JsonNode part) {
        if (part == null || part.isNull()) {
            return "";
        }
        if (part.hasNonNull("text")) {
            String kind = textField(part.get("kind"));
            if (!kind.isBlank() && !isTextKind(kind)) {
                return "";
            }
            return textField(part.get("text"));
        }
        if (part.has("text") && part.get("text").isObject()) {
            return textField(part.get("text").get("value"));
        }
        return "";
    }

    private static boolean isTextKind(String kind) {
        String k = kind.toLowerCase(Locale.ROOT);
        return k.equals("text") || k.equals("textpart");
    }

    private static String textField(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }
}
