package de.qaware.inputmanagement.output;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hosts the job worker that reports the result of every processed document.
 * Owns no processes: it only works on the "Report result" step of {@code document-intake}.
 */
@SpringBootApplication
public class OutputServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutputServiceApplication.class, args);
    }
}
