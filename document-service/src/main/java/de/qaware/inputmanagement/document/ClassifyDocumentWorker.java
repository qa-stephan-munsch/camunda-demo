package de.qaware.inputmanagement.document;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job worker for step A of the {@code document-routing} process, "Classify document".
 * The fields come from the Kafka message that started the instance; any of them may be missing.
 */
@Component
public class ClassifyDocumentWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ClassifyDocumentWorker.class);

    private final DocumentClassifier classifier = new DocumentClassifier();

    @JobWorker(type = "classify-document")
    public Map<String, Object> classifyDocument(
            @Variable(optional = true) String filename,
            @Variable(optional = true) String headline,
            @Variable(optional = true) String content) {
        DocumentClassifier.Classification result = classifier.classify(headline, content);
        LOG.info("Classified {} as {}{}", filename, result.category(), result.urgent() ? " (urgent)" : "");
        return Map.of("category", result.category(), "urgent", result.urgent());
    }
}
