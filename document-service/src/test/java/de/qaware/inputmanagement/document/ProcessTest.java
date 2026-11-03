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
 * The job workers are disabled: the scenarios complete jobs themselves, including the jobs of the
 * Kafka connector tasks, so the process logic is tested without Kafka and without a connectors runtime.
 */
@SpringBootTest(properties = {
        "camunda.client.worker.defaults.enabled=false",
        // the engine reset before the first test can exceed the default 10s on a freshly started container
        "camunda.client.request-timeout=PT30S"
})
@CamundaSpringProcessTest
@TestDeployment(resources = {
        "processes/document-routing/document-routing.bpmn",
        "processes/document-routing/routing-decision.dmn"
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
