package de.qaware.inputmanagement.invoice;

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
 * logic independently of the worker implementations.
 */
@SpringBootTest(properties = {
        "camunda.client.worker.defaults.enabled=false",
        // the engine reset before the first test can exceed the default 10s on a freshly started container
        "camunda.client.request-timeout=PT30S"
})
@CamundaSpringProcessTest
@TestDeployment(resources = {
        "processes/process-invoice/process-invoice.bpmn",
        "processes/process-invoice/invoice-approval.dmn",
        "processes/process-invoice/approve-invoice.form"
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
