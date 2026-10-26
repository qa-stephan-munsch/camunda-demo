package de.qaware.inputmanagement.intake;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job workers for the service tasks of the {@code document-intake} process.
 * Document store and OCR are simulated; only references and a short preview travel as process variables.
 */
@Component
public class DocumentIntakeWorkers {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentIntakeWorkers.class);
    private static final int PREVIEW_LENGTH = 500;

    private final KeywordClassifier classifier = new KeywordClassifier();

    @JobWorker(type = "store-document")
    public Map<String, Object> storeDocument(ActivatedJob job, @Variable String channel) {
        // Derived from the process instance, so a redelivered job produces the same ID (idempotent).
        String documentId = "doc-" + job.getProcessInstanceKey();
        LOG.info("Stored {} document as {}", channel, documentId);
        return Map.of("documentId", documentId);
    }

    @JobWorker(type = "extract-text")
    public Map<String, Object> extractText(
            @Variable String documentId,
            @Variable String channel,
            @Variable(optional = true) String subject,
            @Variable(optional = true) String body,
            @Variable(optional = true) String scannedText) {
        // Letters: the scanning service delivers OCR text. Emails: subject and body are already text.
        String text = "letter".equals(channel)
                ? nullToEmpty(scannedText)
                : nullToEmpty(subject) + "\n\n" + nullToEmpty(body);
        String preview = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) : text;
        LOG.info("Extracted {} characters of text from {}", text.length(), documentId);
        return Map.of("textRef", "txt-" + documentId, "textPreview", preview);
    }

    @JobWorker(type = "classify-document")
    public Map<String, Object> classifyDocument(
            @Variable String documentId,
            @Variable String channel,
            @Variable String textPreview) {
        KeywordClassifier.Classification result = classifier.classify(textPreview, channel);
        LOG.info("Classified {} as {} (confidence {})", documentId, result.docType(), result.confidence());
        return Map.of("docType", result.docType(), "confidence", result.confidence());
    }

    @JobWorker(type = "notify-team-lead")
    public void notifyTeamLead(@Variable String documentId) {
        // Simulated notification; a real implementation would send an email or chat message.
        LOG.warn("Reminder to team lead: document {} still awaits manual classification", documentId);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
