package de.qaware.inputmanagement.output;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job worker for the "Report result" step of {@code document-intake}.
 * The output is a log line; a real system might send a notification or write to an archive.
 */
@Component
public class ResultReportWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ResultReportWorker.class);

    @JobWorker(type = "report-result")
    public void reportResult(
            @Variable(optional = true) String documentId,
            @Variable(optional = true) String channel,
            @Variable(optional = true) String sender,
            @Variable(optional = true) String docType,
            @Variable(optional = true) String outcome) {
        LOG.info(new DocumentResult(documentId, channel, sender, docType, outcome).toLogLine());
    }
}
