package de.qaware.inputmanagement.output;

/**
 * The final result of one document, as collected by {@code document-intake}.
 * Any field may be missing, e.g. {@code docType} when classification never happened.
 */
public record DocumentResult(String documentId, String channel, String sender, String docType, String outcome) {

    private static final String UNKNOWN = "?";

    /** One line per document, e.g. {@code RESULT doc-1 | email from a@b.de | invoice | paid}. */
    public String toLogLine() {
        return "RESULT %s | %s from %s | %s | %s".formatted(
                orUnknown(documentId), orUnknown(channel), orUnknown(sender), orUnknown(docType), orUnknown(outcome));
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
