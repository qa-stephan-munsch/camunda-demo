package de.qaware.inputmanagement.input;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * REST entry point of the system: accepts incoming documents and starts {@code document-intake}.
 * Owns no processes and runs no job workers.
 */
@SpringBootApplication
public class InputServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InputServiceApplication.class, args);
    }
}
