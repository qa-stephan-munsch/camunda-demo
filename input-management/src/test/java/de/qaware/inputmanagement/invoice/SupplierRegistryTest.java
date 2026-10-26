package de.qaware.inputmanagement.invoice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierRegistryTest {

    private final SupplierRegistry registry = new SupplierRegistry();

    @Test
    void findsSupplierByEmailDomain() {
        assertThat(registry.findBySender("Max.Mustermann@Example.com"))
                .map(SupplierRegistry.Supplier::id)
                .contains("S-1001");
    }

    @Test
    void unknownDomainHasNoSupplier() {
        assertThat(registry.findBySender("someone@unknown.org")).isEmpty();
    }

    @Test
    void postalSenderHasNoSupplier() {
        assertThat(registry.findBySender("Erika Musterfrau, Hauptstr. 1, Berlin")).isEmpty();
    }

    @Test
    void missingSenderHasNoSupplier() {
        assertThat(registry.findBySender(null)).isEmpty();
    }
}
