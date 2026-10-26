package de.qaware.inputmanagement.invoice;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hosts the job workers that check, approve and pay invoices.
 * On startup, deploys the processes this service owns: everything under {@code processes/}
 * ({@code process-invoice} and its decision and form).
 */
@SpringBootApplication
@Deployment(resources = "classpath*:/processes/**/*")
public class InvoiceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceServiceApplication.class, args);
    }
}
