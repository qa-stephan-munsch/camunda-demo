package de.qaware.inputmanagement.output;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentResultTest {

    @Test
    void formatsCompleteResult() {
        var result = new DocumentResult("doc-1", "email", "info@buero-schmidt.de", "invoice",
                "invoice 2026-0815 over 5400.00 EUR, approved by accounting, payment pay-doc-1 scheduled");

        assertThat(result.toLogLine()).isEqualTo(
                "RESULT doc-1 | email from info@buero-schmidt.de | invoice"
                        + " | invoice 2026-0815 over 5400.00 EUR, approved by accounting, payment pay-doc-1 scheduled");
    }

    @Test
    void marksMissingFieldsAsUnknown() {
        var result = new DocumentResult("doc-2", "letter", null, " ", null);

        assertThat(result.toLogLine()).isEqualTo("RESULT doc-2 | letter from ? | ? | ?");
    }
}
