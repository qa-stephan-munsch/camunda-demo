package de.qaware.inputmanagement.document;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hosts the job worker that classifies incoming documents.
 * On startup, deploys the process this service owns: everything under {@code processes/}
 * ({@code document-routing}). Kafka in and out is handled by the connectors runtime, not by this service.
 */
@SpringBootApplication
@Deployment(resources = "classpath*:/processes/**/*")
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
