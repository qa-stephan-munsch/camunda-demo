# Development

## Setup

- **Java 21+**. Maven is not needed: use the wrapper `./mvnw`.
- **Docker** for Kafka (`demo/docker-compose.yml`) and for the process tests: Docker Desktop, or Colima
  with these variables (e.g. in `~/.zshrc`), because Testcontainers does not read Docker contexts:
  ```bash
  export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  ```
  Give Colima enough resources if other containers run next to the tests
  (`colima start --cpu 4 --memory 8`). With Colima, install Compose with `brew install docker-compose`;
  `demo/run-demo.sh` uses `docker compose` or `docker-compose`, whichever exists.
- **Node.js 22.18+ and c8ctl** (`npm install -g @camunda8/cli`) for the local cluster and model linting.
- **Camunda Desktop Modeler** (optional) to view and edit the model. Connect it to the local cluster
  with *Camunda 8 Self-Managed*, cluster endpoint `http://localhost:8080/v2`, authentication *None*.

## Build and test

```bash
./mvnw verify
```

| Test | What it checks |
|---|---|
| `DocumentClassifierTest` | The keyword rules: category (invoice before complaint, else `other`) and `urgent`, in headline or content, any case; missing fields → `other`, not urgent |
| `ProcessTest` | The scenarios in `src/test/resources/scenarios/`: `routing-decision.test.json` evaluates the decision table directly, one case per rule (`EVALUATE_DECISION`, `ASSERT_DECISION` with the matched rule); `document-routing.test.json` runs the process for each path (B, C, D), with the decision table evaluated for real |

### Process tests

`ProcessTest` runs the scenarios ([Camunda Process Test](https://docs.camunda.io/docs/apis-tools/testing/getting-started/))
against an engine in Docker:

- **No Kafka, no connectors runtime.** A scenario publishes the start message `document-received`
  itself, with the variables the Kafka connector would set.
- **Job workers are switched off.** The scenarios complete the jobs themselves, including the Kafka
  connector jobs of B and C, so they test the process logic (decision table and gateway) independently
  of the worker code. The worker logic has its own unit test.
- **What B and C would publish is checked.** Before completing the connector job, a scenario asserts
  the task's local variables, i.e. the evaluated connector inputs: `topic` (server, topic name) and
  `message` (key, value with `routedTo`). A typo in the model's message value fails the test.
- **Coverage report**: `document-service/target/coverage-report/report.html`, the model with the
  covered paths highlighted (100%).

A scenario is a list of instructions, for example:

```json
{ "type": "PUBLISH_MESSAGE", "name": "document-received",
  "variables": { "filename": "invoice-acme.txt", "headline": "Invoice 4711", "content": "Please pay 250 EUR." } },
{ "type": "COMPLETE_JOB", "jobSelector": { "elementId": "ClassifyDocument" },
  "variables": { "category": "invoice", "urgent": true } }
```

### CI

`.github/workflows/build.yml` runs `./mvnw verify` on every push and pull request and keeps the
coverage report as a build artifact.

## Lint the models

```bash
c8ctl bpmn lint document-service/src/main/resources/processes/document-routing/document-routing.bpmn
npx --yes dmnlint document-service/src/main/resources/processes/document-routing/routing-decision.dmn
```

`c8ctl bpmn lint` checks structure and Camunda compatibility (missing task definitions, gateways
without default flow, overlapping shapes, …). `dmnlint` uses `.dmnlintrc` in the repository root.
FEEL expressions can be tried out against the local cluster:

```bash
c8ctl feel evaluate 'category = "invoice" and urgent' --var category=invoice --var urgent=true --profile=local
```

## Run the service on its own

`demo/run-demo.sh` starts everything at once. To run the service from the IDE or in its own terminal,
start the cluster (`c8ctl cluster start`) and Kafka (`docker compose -f demo/docker-compose.yml up -d`)
first, then:

```bash
./mvnw -pl document-service spring-boot:run      # deploys document-routing and routingDecision, runs classify-document
```

The service connects to the local cluster (`application.yaml`: `http://localhost:8080`,
gRPC `localhost:26500`, no authentication). It doesn't talk to Kafka itself.

## Change the process

Start small, with the decision table only: give every invoice high priority. In
`routing-decision.dmn`, change the priority of rule 2 to `"high"`, adapt the expected output in
`routing-decision.test.json`, run the tests, restart the service and send `invoice.json`.

Then a new route, e.g. cancellations ("Kündigung", "cancel") to the contracts team:

1. **Classify:** let `DocumentClassifier` return `cancellation`; test first in `DocumentClassifierTest`.
2. **Decide:** in `routing-decision.dmn`, add rules above the catch-all: `"cancellation"`, `true` →
   `"contracts"`, `"high"` and `"cancellation"`, `-` → `"contracts"`, `"normal"`. Add one case per
   rule to `routing-decision.test.json`; the matched rule numbers of the catch-all move down.
3. **Model:** in the Modeler, copy "Forward to customer service" (the Kafka template comes along), name
   it "Forward to contracts", and add a flow from "Which route?" with
   `=routing.route = "contracts"`. Keep the default flow to C. The message value already uses
   `routing.route` and `routing.priority`.
4. **Test:** a fourth scenario in `document-routing.test.json`; the coverage report shows whether every
   path is covered.
5. **Try it:** `demo/run-demo.sh`, then send a document with "Kündigung" in the headline.

## Conventions

| Thing | Convention | Example |
|---|---|---|
| Process IDs, job types, message names | kebab-case | `document-routing`, `classify-document`, `document-received` |
| BPMN element IDs | PascalCase, descriptive | `ClassifyDocument`, `Gateway_Route`, `Flow_ToAccounting` |
| Decision IDs, DMN output names | camelCase, no `-` (it's a FEEL operator) | `routingDecision`, `route`, `priority` |
| Process variables, message fields | camelCase | `filename`, `category`, `urgent`, `routing`, `routedTo` |
| Kafka topics | kebab-case, direction last | `documents-in`, `documents-out` |
| Task names | verb + object | "Classify document", "Forward to accounting" |
| Gateway names | a question | "Which route?" |
