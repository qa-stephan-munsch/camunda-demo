# Development

## Setup

- **Java 21+**. Maven is not needed: use the wrapper `./mvnw`.
- **Docker** for the process tests: Docker Desktop, or Colima with these variables (e.g. in `~/.zshrc`),
  because Testcontainers does not read Docker contexts:
  ```bash
  export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  ```
  Give Colima enough resources if other containers run next to the tests
  (`colima start --cpu 4 --memory 8`).
- **Node.js 22.18+ and c8ctl** (`npm install -g @camunda8/cli`) for the local cluster and model linting.
- **Camunda Desktop Modeler** (optional) to view and edit the models. Connect it to the local cluster
  with *Camunda 8 Self-Managed*, cluster endpoint `http://localhost:8080/v2`, authentication *None*.

## Build and test

```bash
./mvnw verify
```

| Module | Tests |
|---|---|
| `input-service` | `IncomingDocumentTest`: mapping of requests to start messages |
| `document-service` | `KeywordClassifierTest`; `ProcessTest` with scenarios for `document-intake`, `manual-triage` and the routing table |
| `invoice-service` | `InvoiceTextParserTest`, `SupplierRegistryTest`; `ProcessTest` with scenarios for `process-invoice` |
| `output-service` | `DocumentResultTest`: format of the result line |

### Process tests

`ProcessTest` in each service runs the scenarios in `src/test/resources/scenarios/*.test.json`
([Camunda Process Test](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)) against an
engine in Docker:

- **Job workers are switched off.** The scenarios complete the jobs themselves, so they test the
  process logic (routing, gateways, timers, errors) independently of the worker code. The worker
  logic has its own unit tests.
- **Processes of other services are mocked** (`MOCK_CHILD_PROCESS`), so each service tests its own
  processes and its side of the contracts.
- **Coverage report**: `<module>/target/coverage-report/report.html`, the models with the covered
  paths highlighted. Error end events never count as completed, so `manual-triage` and
  `process-invoice` stay just below 100% ([why](pitfalls.md#error-end-events-never-complete)).

A scenario is a list of instructions, for example:

```json
{ "type": "COMPLETE_JOB", "jobSelector": { "elementId": "ClassifyDocument" },
  "variables": { "docType": "invoice", "confidence": 0.93 } }
```

### CI

`.github/workflows/build.yml` runs `./mvnw verify` on every push and pull request and keeps the
coverage reports as a build artifact.

## Lint the models

```bash
c8ctl bpmn lint document-service/src/main/resources/processes/document-intake/document-intake.bpmn
npx --yes dmnlint document-service/src/main/resources/processes/document-intake/routing-decision.dmn
```

`c8ctl bpmn lint` checks structure and Camunda compatibility (missing task definitions, gateways
without default flow, overlapping shapes, …). `dmnlint` uses `.dmnlintrc` in the repository root.
FEEL expressions can be tried out against the local cluster:

```bash
c8ctl feel evaluate 'confidence >= 0.85' --var confidence=0.74 --profile=local
```

## Run the services individually

`demo/run-demo.sh` starts everything at once. To run one service from the IDE or in its own terminal:

```bash
./mvnw install -DskipTests
./mvnw -pl document-service spring-boot:run      # deploys document-intake and manual-triage
./mvnw -pl invoice-service spring-boot:run       # deploys process-invoice
./mvnw -pl output-service spring-boot:run
./mvnw -pl input-service spring-boot:run         # REST on port 8081
```

Every service connects to the local cluster (`application.yaml`: `http://localhost:8080`,
gRPC `localhost:26500`, no authentication).

## Add a follow-up process

New document types get their own follow-up process. `document-intake` stays unchanged.

1. **Decide which service owns it**, or create a new service module (add it to `<modules>` in the
   parent `pom.xml` and to `SERVICES` in `demo/run-demo.sh`).
2. **Model it** in `src/main/resources/processes/<process>/` of that service, with its forms and
   decisions next to it.
3. **Keep the contracts** ([architecture](architecture.md#contracts-between-the-processes)): set
   `outcome` on every successful path; report failures with an error end event
   `DOCUMENT_PROCESSING_FAILED`.
4. **Route to it:** add a rule above the catch-all in
   `document-service/src/main/resources/processes/document-intake/routing-decision.dmn`, e.g.
   `docType = "complaint"` → `"process-complaint"`, and a scenario in `routing-decision.test.json`.
5. **Implement the workers** (`@JobWorker(type = "…")`, the type must match the model exactly) and
   unit-test their logic.
6. **Test the process**: a scenario file in `src/test/resources/scenarios/` and the new resources in
   `@TestDeployment` of that service's `ProcessTest`.

Suggested exercise: a `process-complaint` that acknowledges receipt automatically, lets a clerk
decide on compensation (DMN by customer segment?), and escalates after two days.

## Conventions

| Thing | Convention | Example |
|---|---|---|
| Process IDs, job types, message names | kebab-case | `process-invoice`, `extract-invoice-data`, `email-received` |
| BPMN element IDs | PascalCase, descriptive | `ClassifyManually`, `Gateway_Confident`, `Flow_ToReportResult` |
| Decision IDs, DMN output names | camelCase, no `-` (it's a FEEL operator) | `routingDecision`, `approverGroup` |
| BPMN error codes | UPPER_SNAKE_CASE | `DOCUMENT_PROCESSING_FAILED` |
| Task names | verb + object | "Classify document", "Approve invoice" |
| Gateway names | a question | "Classification confident?" |
