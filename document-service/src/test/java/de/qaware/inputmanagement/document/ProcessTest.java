package de.qaware.inputmanagement.document;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.TestCaseSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the scenarios in {@code src/test/resources/scenarios/} against an engine in Docker.
 * The job workers are disabled: the scenarios complete jobs themselves, so they test the process
 * logic independently of the worker implementations. Processes owned by other services
 * ({@code process-invoice}) are mocked in the scenarios.
 */
@SpringBootTest(properties = {
        "camunda.client.worker.defaults.enabled=false",
        // the engine reset before the first test can exceed the default 10s on a freshly started container
        "camunda.client.request-timeout=PT30S"
})
@CamundaSpringProcessTest
@TestDeployment(resources = {
        "processes/document-intake/document-intake.bpmn",
        "processes/document-intake/routing-decision.dmn",
        "processes/document-intake/classify-document-manually.form",
        "processes/document-intake/handle-failed-document.form",
        "processes/manual-triage/manual-triage.bpmn",
        "processes/manual-triage/triage-document.form"
})
class ProcessTest {

    @Autowired
    private TestCaseRunner testCaseRunner;

    @ParameterizedTest(name = "{0}")
    @TestCaseSource(directory = "/scenarios")
    void shouldPass(TestCase testCase, String fileName) {
        testCaseRunner.run(testCase);
    }
}
