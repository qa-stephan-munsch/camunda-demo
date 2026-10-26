package de.qaware.inputmanagement;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hosts the job workers of the input management processes.
 * On startup, all BPMN, DMN and form files under {@code processes/} are deployed to the cluster.
 */
@SpringBootApplication
@Deployment(resources = "classpath*:/processes/**/*")
public class InputManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(InputManagementApplication.class, args);
    }
}
