package de.qaware.inputmanagement.input;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A document as it arrives at {@code POST /documents}.
 * Emails carry {@code subject} and {@code body}, scanned letters carry {@code scannedText}.
 */
public record IncomingDocument(String channel, String sender, String subject, String body, String scannedText) {

    /** The message that starts {@code document-intake} for this document. */
    public record StartMessage(String name, Map<String, Object> variables) {
    }

    /**
     * Maps the document to the start message of its channel, with the variables that message's start event accepts.
     *
     * @throws IllegalArgumentException if the channel is neither {@code email} nor {@code letter}
     */
    public StartMessage toStartMessage() {
        Map<String, Object> variables = new LinkedHashMap<>();
        putIfPresent(variables, "sender", sender);
        return switch (channel == null ? "" : channel) {
            case "email" -> {
                putIfPresent(variables, "subject", subject);
                putIfPresent(variables, "body", body);
                yield new StartMessage("email-received", variables);
            }
            case "letter" -> {
                putIfPresent(variables, "scannedText", scannedText);
                yield new StartMessage("letter-scanned", variables);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown channel '" + channel + "', expected 'email' or 'letter'");
        };
    }

    private static void putIfPresent(Map<String, Object> variables, String name, String value) {
        if (value != null) {
            variables.put(name, value);
        }
    }
}
