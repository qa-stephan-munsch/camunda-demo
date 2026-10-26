package de.qaware.inputmanagement.invoice;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stand-in for real invoice data extraction (e.g. an IDP service).
 * Finds the invoice number and a euro amount in German number format ("1.250,00 EUR").
 */
public class InvoiceTextParser {

    private static final Pattern INVOICE_NUMBER = Pattern.compile(
            "(?i)\\b(?:rechnung|invoice)\\s*(?:nr\\.?|no\\.?|number)?\\s*:?\\s*(\\d[\\w-]*)");
    private static final Pattern EURO_AMOUNT = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{3})+|\\d+),(\\d{2})\\s*(?:EUR|€)");

    public Optional<String> findInvoiceNumber(String text) {
        Matcher matcher = INVOICE_NUMBER.matcher(nullToEmpty(text));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public Optional<BigDecimal> findAmount(String text) {
        Matcher matcher = EURO_AMOUNT.matcher(nullToEmpty(text));
        if (!matcher.find()) {
            return Optional.empty();
        }
        String euros = matcher.group(1).replace(".", "");
        return Optional.of(new BigDecimal(euros + "." + matcher.group(2)));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
