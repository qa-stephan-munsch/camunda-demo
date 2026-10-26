package de.qaware.inputmanagement.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordClassifierTest {

    private final KeywordClassifier classifier = new KeywordClassifier();

    @Test
    void emailWithSingleKeywordIsConfident() {
        var result = classifier.classify("Rechnung 4711 für März", "email");

        assertThat(result.docType()).isEqualTo("invoice");
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void letterIsLessConfidentThanEmail() {
        var result = classifier.classify("Hiermit reiche ich eine Beschwerde ein", "letter");

        assertThat(result.docType()).isEqualTo("complaint");
        assertThat(result.confidence()).isEqualTo(0.74);
    }

    @Test
    void severalMatchingTypesAreAmbiguous() {
        var result = classifier.classify("Beschwerde über die letzte Rechnung", "email");

        assertThat(result.confidence()).isLessThan(0.85);
    }

    @Test
    void textWithoutKeywordsIsUnknown() {
        var result = classifier.classify("Bewerbung als Sachbearbeiter", "email");

        assertThat(result.docType()).isEqualTo(KeywordClassifier.UNKNOWN_TYPE);
        assertThat(result.confidence()).isLessThan(0.85);
    }

    @Test
    void missingTextIsUnknown() {
        var result = classifier.classify(null, "letter");

        assertThat(result.docType()).isEqualTo(KeywordClassifier.UNKNOWN_TYPE);
    }
}
