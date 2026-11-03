package de.qaware.inputmanagement.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentClassifierTest {

    private final DocumentClassifier classifier = new DocumentClassifier();

    @Test
    void classifiesInvoiceKeywordInHeadline() {
        assertThat(classifier.classify("Invoice 4711", "Please pay.").category()).isEqualTo("invoice");
    }

    @Test
    void classifiesGermanKeywordInContentIgnoringCase() {
        assertThat(classifier.classify("Hallo", "anbei unsere RECHNUNG Nr. 12").category()).isEqualTo("invoice");
    }

    @Test
    void classifiesComplaintKeywords() {
        assertThat(classifier.classify("Complaint", "I am unhappy.").category()).isEqualTo("complaint");
        assertThat(classifier.classify("Beschwerde über die Lieferung", "").category()).isEqualTo("complaint");
        assertThat(classifier.classify("Hallo", "Ich möchte eine Reklamation einreichen").category()).isEqualTo("complaint");
    }

    @Test
    void invoiceWinsOverComplaint() {
        assertThat(classifier.classify("Beschwerde über die letzte Rechnung", "").category()).isEqualTo("invoice");
    }

    @Test
    void classifiesEverythingElseAsOther() {
        assertThat(classifier.classify("New address", "We have moved.").category()).isEqualTo("other");
    }

    @Test
    void detectsUrgencyIgnoringCase() {
        assertThat(classifier.classify("DRINGEND: Rechnung 4711", "").urgent()).isTrue();
        assertThat(classifier.classify("Complaint", "This is urgent!").urgent()).isTrue();
        assertThat(classifier.classify("Invoice 4711", "Please pay.").urgent()).isFalse();
    }

    @Test
    void classifiesMissingFieldsAsOtherAndNotUrgent() {
        var result = classifier.classify(null, null);

        assertThat(result.category()).isEqualTo("other");
        assertThat(result.urgent()).isFalse();
    }
}
