package de.qaware.inputmanagement.invoice;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Job workers for the service tasks of the {@code process-invoice} process.
 * An invoice that cannot be read or matched to a supplier is a business outcome, not a technical failure:
 * the workers throw the BPMN error {@value #INVOICE_INVALID} instead of failing the job.
 */
@Component
public class InvoiceWorkers {

    static final String INVOICE_INVALID = "INVOICE_INVALID";

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceWorkers.class);

    private final InvoiceTextParser parser = new InvoiceTextParser();
    private final SupplierRegistry supplierRegistry = new SupplierRegistry();

    @JobWorker(type = "extract-invoice-data")
    public Map<String, Object> extractInvoiceData(@Variable String documentId, @Variable String textPreview) {
        Optional<String> invoiceNumber = parser.findInvoiceNumber(textPreview);
        Optional<BigDecimal> amount = parser.findAmount(textPreview);
        if (invoiceNumber.isEmpty() || amount.isEmpty()) {
            throw CamundaError.bpmnError(INVOICE_INVALID, "No invoice number or amount found in " + documentId);
        }
        LOG.info("Invoice {} over {} EUR found in {}", invoiceNumber.get(), amount.get(), documentId);
        return Map.of("invoiceNumber", invoiceNumber.get(), "amount", amount.get(), "currency", "EUR");
    }

    @JobWorker(type = "find-supplier")
    public Map<String, Object> findSupplier(@Variable String documentId, @Variable(optional = true) String sender) {
        SupplierRegistry.Supplier supplier = supplierRegistry.findBySender(sender)
                .orElseThrow(() -> CamundaError.bpmnError(INVOICE_INVALID,
                        "Unknown supplier '" + sender + "' for " + documentId));
        LOG.info("Supplier of {} is {} ({})", documentId, supplier.name(), supplier.id());
        return Map.of("supplierId", supplier.id(), "supplierName", supplier.name());
    }

    @JobWorker(type = "schedule-payment")
    public Map<String, Object> schedulePayment(
            @Variable String documentId,
            @Variable String supplierId,
            @Variable BigDecimal amount) {
        // Derived from the document, so a redelivered job does not schedule a second payment (idempotent).
        String paymentId = "pay-" + documentId;
        LOG.info("Scheduled payment {} of {} EUR to supplier {}", paymentId, amount, supplierId);
        return Map.of("paymentId", paymentId);
    }

    @JobWorker(type = "send-invoice-rejection")
    public void sendInvoiceRejection(
            @Variable String supplierId,
            @Variable String invoiceNumber,
            @Variable(optional = true) String approvalComment) {
        // Simulated notification; a real implementation would send a letter or email to the supplier.
        LOG.info("Informed supplier {} that invoice {} was rejected: {}", supplierId, invoiceNumber, approvalComment);
    }
}
