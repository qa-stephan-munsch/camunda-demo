package de.qaware.inputmanagement.input;

import org.junit.jupiter.api.Test;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncomingDocumentTest {

    @Test
    void emailStartsWithEmailMessage() {
        var document = new IncomingDocument("email", "max@example.com", "Rechnung 4711", "Anbei die Rechnung.", null);

        var message = document.toStartMessage();

        assertThat(message.name()).isEqualTo("email-received");
        assertThat(message.variables()).containsOnly(
                entry("sender", "max@example.com"),
                entry("subject", "Rechnung 4711"),
                entry("body", "Anbei die Rechnung."));
    }

    @Test
    void letterStartsWithLetterMessage() {
        var document = new IncomingDocument("letter", "Erika Musterfrau", null, null, "Hiermit kündige ich.");

        var message = document.toStartMessage();

        assertThat(message.name()).isEqualTo("letter-scanned");
        assertThat(message.variables()).containsOnly(
                entry("sender", "Erika Musterfrau"),
                entry("scannedText", "Hiermit kündige ich."));
    }

    @Test
    void fieldsOfTheOtherChannelAreIgnored() {
        var document = new IncomingDocument("letter", "Erika Musterfrau", "Subject", "Body", "Text");

        assertThat(document.toStartMessage().variables()).containsOnlyKeys("sender", "scannedText");
    }

    @Test
    void unknownChannelIsRejected() {
        var document = new IncomingDocument("fax", "someone", null, null, null);

        assertThatThrownBy(document::toStartMessage)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fax");
    }

    @Test
    void missingChannelIsRejected() {
        var document = new IncomingDocument(null, "someone", null, null, null);

        assertThatThrownBy(document::toStartMessage).isInstanceOf(IllegalArgumentException.class);
    }
}
