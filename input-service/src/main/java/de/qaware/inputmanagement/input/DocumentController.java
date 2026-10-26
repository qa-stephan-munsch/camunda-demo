package de.qaware.inputmanagement.input;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.CorrelateMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Accepts incoming documents and starts one {@code document-intake} instance per document.
 */
@RestController
public class DocumentController {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentController.class);

    private final CamundaClient camundaClient;

    public DocumentController(CamundaClient camundaClient) {
        this.camundaClient = camundaClient;
    }

    public record DocumentAccepted(String processInstanceKey, String correlationKey) {
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentAccepted receive(@RequestBody IncomingDocument document) {
        IncomingDocument.StartMessage message;
        try {
            message = document.toStartMessage();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }

        // Unique per document: Camunda starts at most one active instance per message name and correlation key.
        String correlationKey = "input-" + UUID.randomUUID();
        try {
            CorrelateMessageResponse response = camundaClient.newCorrelateMessageCommand()
                    .messageName(message.name())
                    .correlationKey(correlationKey)
                    .variables(message.variables())
                    .send()
                    .join();
            String processInstanceKey = String.valueOf(response.getProcessInstanceKey());
            LOG.info("Received {} from {}, started document-intake instance {}",
                    document.channel(), document.sender(), processInstanceKey);
            return new DocumentAccepted(processInstanceKey, correlationKey);
        } catch (ClientException e) {
            // Typically: document-intake is not deployed yet because the document-service has not been started.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not start document-intake: " + e.getMessage(), e);
        }
    }
}
