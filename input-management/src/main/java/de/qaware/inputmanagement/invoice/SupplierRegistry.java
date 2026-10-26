package de.qaware.inputmanagement.invoice;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Stand-in for the supplier master data (e.g. an ERP system).
 * Identifies the supplier by the domain of the sender's email address.
 */
public class SupplierRegistry {

    public record Supplier(String id, String name) {
    }

    private static final Map<String, Supplier> SUPPLIERS_BY_DOMAIN = Map.of(
            "example.com", new Supplier("S-1001", "Muster GmbH"),
            "buero-schmidt.de", new Supplier("S-1002", "Büro Schmidt KG"));

    public Optional<Supplier> findBySender(String sender) {
        if (sender == null || !sender.contains("@")) {
            return Optional.empty();
        }
        String domain = sender.substring(sender.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return Optional.ofNullable(SUPPLIERS_BY_DOMAIN.get(domain));
    }
}
