package de.qaware.inputmanagement.document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stand-in for a real classifier (ML model, LLM, IDP service).
 * Matches keywords and simulates a confidence: OCR text from letters is less reliable than emails,
 * and documents matching several types are ambiguous.
 */
public class KeywordClassifier {

    public static final String UNKNOWN_TYPE = "other";

    private static final double EMAIL_CONFIDENCE = 0.93;
    private static final double OCR_PENALTY = 0.8;
    private static final double AMBIGUOUS_CONFIDENCE = 0.5;
    private static final double UNKNOWN_CONFIDENCE = 0.3;

    private static final Map<String, List<String>> KEYWORDS_BY_TYPE = new LinkedHashMap<>();

    static {
        KEYWORDS_BY_TYPE.put("invoice", List.of("rechnung", "invoice"));
        KEYWORDS_BY_TYPE.put("complaint", List.of("beschwerde", "reklamation", "complaint"));
        KEYWORDS_BY_TYPE.put("cancellation", List.of("kündigung", "kuendigung", "cancel"));
        KEYWORDS_BY_TYPE.put("address-change", List.of("adressänderung", "umzug", "change of address", "new address"));
    }

    public record Classification(String docType, double confidence) {
    }

    public Classification classify(String text, String channel) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> matchingTypes = KEYWORDS_BY_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalized::contains))
                .map(Map.Entry::getKey)
                .toList();

        if (matchingTypes.isEmpty()) {
            return new Classification(UNKNOWN_TYPE, UNKNOWN_CONFIDENCE);
        }
        if (matchingTypes.size() > 1) {
            return new Classification(matchingTypes.getFirst(), AMBIGUOUS_CONFIDENCE);
        }
        double confidence = "letter".equals(channel) ? EMAIL_CONFIDENCE * OCR_PENALTY : EMAIL_CONFIDENCE;
        return new Classification(matchingTypes.getFirst(), Math.round(confidence * 100) / 100.0);
    }
}
