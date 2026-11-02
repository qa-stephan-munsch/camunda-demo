package de.qaware.inputmanagement.document;

import java.util.List;
import java.util.Locale;

/**
 * Stand-in for a real classifier (ML model, LLM, IDP service).
 * Finds the category by keywords in headline and content (invoice before complaint, everything else
 * is "other") and whether the document is urgent. Where a document goes is decided by the
 * {@code routingDecision} table, not here.
 */
public class DocumentClassifier {

    public static final String INVOICE = "invoice";
    public static final String COMPLAINT = "complaint";
    public static final String OTHER = "other";

    private static final List<String> INVOICE_KEYWORDS = List.of("invoice", "rechnung");
    private static final List<String> COMPLAINT_KEYWORDS = List.of("complaint", "beschwerde", "reklamation");
    private static final List<String> URGENT_KEYWORDS = List.of("urgent", "dringend");

    public record Classification(String category, boolean urgent) {
    }

    public Classification classify(String headline, String content) {
        String text = (nullToEmpty(headline) + " " + nullToEmpty(content)).toLowerCase(Locale.ROOT);
        String category = containsAny(text, INVOICE_KEYWORDS) ? INVOICE
                : containsAny(text, COMPLAINT_KEYWORDS) ? COMPLAINT
                : OTHER;
        return new Classification(category, containsAny(text, URGENT_KEYWORDS));
    }

    private static boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
