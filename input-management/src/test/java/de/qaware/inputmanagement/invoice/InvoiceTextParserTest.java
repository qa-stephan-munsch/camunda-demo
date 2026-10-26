package de.qaware.inputmanagement.invoice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTextParserTest {

    private final InvoiceTextParser parser = new InvoiceTextParser();

    @Test
    void findsInvoiceNumberAndAmountInEmailText() {
        String text = "Rechnung 4711\n\nAnbei unsere Rechnung für März über 1.250,00 EUR.";

        assertThat(parser.findInvoiceNumber(text)).contains("4711");
        assertThat(parser.findAmount(text)).contains(new BigDecimal("1250.00"));
    }

    @Test
    void findsInvoiceNumberWithPrefixAndAmountWithEuroSign() {
        String text = "Invoice No. 2026-0815: total 99,90 €";

        assertThat(parser.findInvoiceNumber(text)).contains("2026-0815");
        assertThat(parser.findAmount(text)).contains(new BigDecimal("99.90"));
    }

    @Test
    void findsLargeAmountWithSeveralThousandsSeparators() {
        assertThat(parser.findAmount("Summe: 1.234.567,89 EUR")).contains(new BigDecimal("1234567.89"));
    }

    @Test
    void findsNothingInUnrelatedText() {
        String text = "Bitte senden Sie mir Informationen zu Ihren Produkten.";

        assertThat(parser.findInvoiceNumber(text)).isEmpty();
        assertThat(parser.findAmount(text)).isEmpty();
    }

    @Test
    void handlesMissingText() {
        assertThat(parser.findInvoiceNumber(null)).isEmpty();
        assertThat(parser.findAmount(null)).isEmpty();
    }
}
