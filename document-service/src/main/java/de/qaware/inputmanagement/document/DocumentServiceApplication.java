package de.qaware.inputmanagement.document;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hosts the job workers that store, read and classify incoming documents.
 * On startup, deploys the processes this service owns: everything under {@code processes/}
 * ({@code document-intake}, {@code manual-triage} and their decisions and forms).
 */
@SpringBootApplication
@Deployment(resources = "classpath*:/processes/**/*")
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
