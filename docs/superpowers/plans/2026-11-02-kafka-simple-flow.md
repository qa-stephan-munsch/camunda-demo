# Kafka Simple Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the four-service demo with one process `Kafka in → A: Classify → invoice? → B or C → Kafka out`, attached to Kafka with the Camunda Kafka connectors.

**Architecture:** One Spring Boot module `document-service` deploys `document-routing.bpmn` and runs the only Java job worker (`classify-document`). The Kafka inbound connector on the message start event consumes `documents-in`; B and C are Kafka outbound connector tasks publishing to `documents-out`. The connectors run in the connectors runtime that c8run already starts (port 8086); Kafka and Kafka UI run in Docker.

**Tech Stack:** Camunda 8.10.6 (c8run, connectors runtime 8.10.2), Spring Boot 4.1.1, Java 21, Camunda Process Test (JSON test cases), Docker Compose, Apache Kafka (KRaft, single node), Kafka UI.

**Spec:** `docs/superpowers/specs/2026-11-02-kafka-simple-flow-design.md`

## Global Constraints

- Keep Maven artifact `input-management`, package `de.qaware.inputmanagement.document`, version `0.1.0-SNAPSHOT`.
- Topics: `documents-in`, `documents-out`. Bootstrap server in the BPMN: `localhost:9092`, written literally, no connector secrets, no auth.
- Input message: `{ "filename", "headline", "content" }`. Output message: `{ "filename", "headline", "category", "routedTo" }`, key `filename`; `routedTo` is `"accounting"` (B) or `"general-inbox"` (C).
- `category` is `"invoice"` if headline or content contains "invoice" or "rechnung" (case-insensitive), else `"other"`.
- No user tasks, forms, DMN, timers, call activities, BPMN errors, REST input.
- Each Kafka message starts exactly one instance; the same file sent twice starts two instances.
- Naming conventions from `docs/development.md` stay: kebab-case process ids/job types/message names, PascalCase element ids, task names verb + object, gateway names a question.
- Docker image tags are pinned to an exact version, never `latest`.

## Review Focus

1. **Headline or content missing / null** in the Kafka message → classified as `"other"`, goes to C, no incident. Pinned in Task 2 (`classifiesMissingFieldsAsOther`).
2. **Keyword only in the content, or in different case** ("RECHNUNG Nr. 12") → `"invoice"`. Pinned in Task 2.
3. **Documents sent before the service has deployed the process** → still processed once it is deployed (inbound `autoOffsetReset = earliest`, fixed `groupId`). Checked manually in Task 4, step 6.
4. **Same sample sent twice** → two instances, two result messages (no `messageIdExpression`, deduplication AUTO). Checked manually in Task 4, step 6.
5. **Message that is not valid JSON** on `documents-in` → instance still starts with null fields and ends in C (because of 1), no stuck instance. Checked manually in Task 4, step 6; documented in `docs/pitfalls.md` (Task 5).

---

### Task 1: Remove the services that are no longer needed

**Files:**
- Delete: `input-service/`, `invoice-service/`, `output-service/` (whole directories)
- Delete: `demo/documents.http`, `demo/http-client.env.json`, `demo/samples/*.json`, `.dmnlintrc`
- Modify: `pom.xml` (`<modules>`, `<description>`)
- Modify: `.gitignore` (stale comment `input-management/run-demo.sh` → `demo/run-demo.sh`)

**Interfaces:**
- Produces: a parent POM with the single module `document-service`.

- [ ] **Step 1:** `git rm -r input-service invoice-service output-service demo/documents.http demo/http-client.env.json demo/samples .dmnlintrc`
- [ ] **Step 2:** In `pom.xml`, keep only `<module>document-service</module>`; description: `Camunda 8 demo: documents from Kafka, one decision, results to Kafka`.
- [ ] **Step 3:** Fix the `.gitignore` comment.
- [ ] **Step 4:** Run `./mvnw -q verify`. Expected: BUILD SUCCESS (document-service still has its old code and tests at this point).
- [ ] **Step 5: Commit** — `git commit -m "Remove input, invoice and output services"`

---

### Task 2: Classifier and classify-document worker

**Files:**
- Delete: `document-service/src/main/java/.../document/KeywordClassifier.java`, `DocumentIntakeWorkers.java`, `document-service/src/test/java/.../document/KeywordClassifierTest.java`
- Create: `document-service/src/main/java/de/qaware/inputmanagement/document/DocumentClassifier.java`
- Create: `document-service/src/main/java/de/qaware/inputmanagement/document/ClassifyDocumentWorker.java`
- Test: `document-service/src/test/java/de/qaware/inputmanagement/document/DocumentClassifierTest.java`

**Interfaces:**
- Produces: `DocumentClassifier` with `public static final String INVOICE = "invoice"`, `public static final String OTHER = "other"`, `public String classify(String headline, String content)` (null-safe).
- Produces: job type `classify-document`; reads variables `filename`, `headline`, `content` (all `@Variable(optional = true)`); returns `Map.of("category", <category>)`; logs `Classified {filename} as {category}` (logger in `de.qaware.inputmanagement…` so `run-demo.sh` shows it).

- [ ] **Step 1: Write the failing test** `DocumentClassifierTest` (JUnit 5, AssertJ, style of the deleted `KeywordClassifierTest`):

```java
@Test void classifiesInvoiceKeywordInHeadline()   { assertThat(classifier.classify("Invoice 4711", "Please pay.")).isEqualTo("invoice"); }
@Test void classifiesGermanKeywordInContentIgnoringCase() { assertThat(classifier.classify("Hallo", "anbei unsere RECHNUNG Nr. 12")).isEqualTo("invoice"); }
@Test void classifiesEverythingElseAsOther()       { assertThat(classifier.classify("Complaint", "I am unhappy.")).isEqualTo("other"); }
@Test void classifiesMissingFieldsAsOther()        { assertThat(classifier.classify(null, null)).isEqualTo("other"); }
```

- [ ] **Step 2:** `./mvnw -q -pl document-service test -Dtest=DocumentClassifierTest` → FAIL (class does not exist).
- [ ] **Step 3:** Implement `DocumentClassifier.classify` (lower-case with `Locale.ROOT`, keywords `"invoice"`, `"rechnung"`), and `ClassifyDocumentWorker` (`@Component`, `@JobWorker(type = "classify-document")`). Delete the old classes and test. Short Javadoc like today's classes, saying it is a stand-in for a real classifier.
- [ ] **Step 4:** `./mvnw -q -pl document-service test -Dtest=DocumentClassifierTest` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add keyword classifier and classify-document worker"`

(`ProcessTest` still references the old models and passes; it is replaced in Task 3.)

---

### Task 3: document-routing process and process tests

**Files:**
- Delete: `document-service/src/main/resources/processes/document-intake/`, `.../processes/manual-triage/`, `document-service/src/test/resources/scenarios/document-intake.test.json`, `.../routing-decision.test.json`
- Create: `document-service/src/main/resources/processes/document-routing/document-routing.bpmn`
- Create: `document-service/src/test/resources/scenarios/document-routing.test.json`
- Modify: `document-service/src/test/java/.../document/ProcessTest.java` (`@TestDeployment`, Javadoc)
- Modify: `document-service/src/main/java/.../document/DocumentServiceApplication.java` (Javadoc only), `document-service/pom.xml` (`<description>`: `Classifies incoming documents; owns document-routing`)

**Interfaces:**
- Consumes: job type `classify-document`, variable `category` (Task 2).
- Produces: process id `document-routing`, start message name `document-received`, element ids below (used by tests and docs).

**Model** (process id `document-routing`, name "Document routing", `isExecutable="true"`, with BPMN DI so it opens cleanly in the Modeler; layout left to right):

| Element id | Type | Name | Configuration |
|---|---|---|---|
| `StartEvent_DocumentReceived` | Message start event, `zeebe:modelerTemplate="io.camunda.connectors.inbound.KafkaMessageStart.v1"`, `zeebe:modelerTemplateVersion="8"` | Document received | Message `Message_DocumentReceived` with name `document-received`. `zeebe:properties`: `inbound.type=io.camunda:connector-kafka-inbound:1`, `authenticationType=credentials` (empty username/password), `topic.bootstrapServers=localhost:9092`, `topic.topicName=documents-in`, `groupId=document-routing`, `autoOffsetReset=earliest`, `schemaStrategy.type=noSchema`, `consumeUnmatchedEvents=true`, `correlationRequired=notRequired`, `deduplicationModeManualFlag=false`, `deduplicationMode=AUTO`, `resultExpression={filename: value.filename, headline: value.headline, content: value.content}` (FEEL, leading `=`). No `messageIdExpression`. |
| `ClassifyDocument` | Service task, `zeebe:taskDefinition type="classify-document"` | Classify document | |
| `Gateway_Invoice` | Exclusive gateway, `default="Flow_ToGeneralInbox"` | Invoice? | `Flow_ToAccounting` (name "yes"): `=category = "invoice"`; `Flow_ToGeneralInbox` (name "no"): default |
| `ForwardToAccounting` | Service task, Kafka outbound template `io.camunda.connectors.KAFKA.v1` version 7, `zeebe:taskDefinition type="io.camunda:connector-kafka:1" retries="3"` | Forward to accounting | `zeebe:input`: `authentication.username`/`password` empty, `topic.bootstrapServers=localhost:9092`, `topic.topicName=documents-out`, `schemaStrategy.type=noSchema`, `message.key==filename`, `message.value=={filename: filename, headline: headline, category: category, routedTo: "accounting"}`. Task headers `elementTemplateVersion=7`, `elementTemplateId=io.camunda.connectors.KAFKA.v1`, `retryBackoff=PT30S`. |
| `ForwardToGeneralInbox` | same as above | Forward to general inbox | same, `routedTo: "general-inbox"` |
| `Gateway_Routed` | Exclusive gateway (merge) | | |
| `EndEvent_DocumentRouted` | End event | Document routed | |

Take the exact property XML shape from the 8.10 templates (`https://raw.githubusercontent.com/camunda/connectors/8.10.2/connectors/kafka/element-templates/kafka-inbound-connector-start-message.json` and `kafka-outbound-connector.json`).

- [ ] **Step 1: Write the failing test scenarios** `document-routing.test.json` (schema `https://camunda.com/json-schema/cpt-test-cases/8.9/schema.json`, like the deleted files). Two test cases:
  - "invoice — forwarded to accounting": `PUBLISH_MESSAGE` name `document-received`, variables `{ "filename": "invoice-acme.txt", "headline": "Invoice 4711", "content": "Please pay 250 EUR." }` → `COMPLETE_JOB` `ClassifyDocument` with `{ "category": "invoice" }` → `COMPLETE_JOB` `ForwardToAccounting` with `{}` → `ASSERT_ELEMENT_INSTANCES` `ForwardToGeneralInbox` `IS_NOT_ACTIVATED` (use the state name the schema offers for "never entered") → `ASSERT_VARIABLES` `{ "category": "invoice" }` → `ASSERT_PROCESS_INSTANCE` `IS_COMPLETED`.
  - "other document — forwarded to general inbox": same with `complaint.txt`, `"category": "other"`, `ForwardToGeneralInbox` completed, `ForwardToAccounting` not activated.
- [ ] **Step 2:** Update `ProcessTest`: `@TestDeployment(resources = "processes/document-routing/document-routing.bpmn")`; Javadoc says workers are off and the scenarios complete the Kafka connector jobs themselves, so neither Kafka nor the connectors runtime is needed. Delete the old models and scenarios. Run `./mvnw -q -pl document-service verify` → FAIL (`document-routing.bpmn` missing).
- [ ] **Step 3:** Write `document-routing.bpmn` as in the table.
- [ ] **Step 4:** `c8ctl bpmn lint document-service/src/main/resources/processes/document-routing/document-routing.bpmn` → no errors. `./mvnw -q verify` → BUILD SUCCESS, both scenarios pass; `document-service/target/coverage-report/report.html` shows 100%.
- [ ] **Step 5: Commit** — `git commit -m "Add document-routing process with Kafka connectors"`

---

### Task 4: Kafka in Docker, run script, samples (end to end)

**Files:**
- Create: `demo/docker-compose.yml`
- Create: `demo/send-document.sh` (executable)
- Create: `demo/samples/invoice.json`, `demo/samples/complaint.json`
- Modify: `demo/run-demo.sh`

**Interfaces:**
- Consumes: process from Task 3, topics and bootstrap server from Global Constraints.
- Produces: `demo/run-demo.sh [--no-build] [--verbose]`, `demo/send-document.sh <sample.json>`, Kafka UI on `http://localhost:8090`.

- [ ] **Step 1:** `demo/docker-compose.yml`, project name `camunda-demo`:
  - `kafka`: `apache/kafka:<pinned>`, `container_name: kafka`, KRaft single node; listeners `HOST://:9092` advertised as `localhost:9092` (for the connectors runtime on the host) and `DOCKER://kafka:29092` (for Kafka UI); port `9092:9092`; healthcheck with `kafka-topics.sh --bootstrap-server localhost:9092 --list`.
  - `kafka-init`: same image, runs once after `kafka` is healthy, creates `documents-in` and `documents-out` with `--if-not-exists`.
  - `kafka-ui`: `kafbat/kafka-ui:<pinned>`, port `8090:8080`, cluster `local` at `kafka:29092`.
- [ ] **Step 2:** `demo/send-document.sh <file>`: fails with usage if no readable file; compacts the JSON to one line (`tr -d '\n'`) and pipes it into `docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic documents-in`; prints `Sent <file> to documents-in`. bash 3.2 compatible.
- [ ] **Step 3:** Samples: `invoice.json` (`filename: "invoice-acme.txt"`, headline `"Invoice 4711"`, content mentions an amount) and `complaint.json` (`filename: "complaint-meier.txt"`, German or English complaint, no keyword).
- [ ] **Step 4:** Rewrite `demo/run-demo.sh`, keeping today's structure and style (preconditions, `info`/`fail`, colored log via `format_log`, Ctrl+C stops the service, cluster keeps running):
  - preconditions: `java`, `curl`, `docker`; no running `document-service` jar;
  - Camunda cluster block unchanged; additionally wait until the connectors runtime answers on `http://localhost:8086/actuator/health`;
  - `docker compose -f "$DEMO_DIR/docker-compose.yml" up -d --wait`;
  - build, then start only `document-service` (`SERVICES=(document-service)`);
  - final banner: Operate `http://localhost:8080/operate`, Kafka UI `http://localhost:8090`, `demo/send-document.sh demo/samples/invoice.json`, results in Kafka UI topic `documents-out`;
  - update the usage comment at the top.
- [ ] **Step 5:** Run `demo/run-demo.sh`. Expected: cluster, Kafka, Kafka UI up; `document-service` started; banner shown.
- [ ] **Step 6: End-to-end check** (in a second terminal) — all must hold:
  - `demo/send-document.sh demo/samples/invoice.json` → log shows `Classified invoice-acme.txt as invoice`; `documents-out` (Kafka UI) has `{"filename":"invoice-acme.txt",…,"category":"invoice","routedTo":"accounting"}` with key `invoice-acme.txt`; Operate shows the instance completed via "Forward to accounting".
  - `complaint.json` → `routedTo: "general-inbox"`.
  - Send `invoice.json` twice → two instances, two result messages.
  - Stop the service (Ctrl+C), send `complaint.json`, restart with `--no-build` → it is processed.
  - Produce `not json` to `documents-in` via Kafka UI → instance ends in C, no incident. If an incident or stuck instance appears instead, record the actual behavior for `docs/pitfalls.md`.
- [ ] **Step 7: Commit** — `git commit -m "Run the demo with Kafka and Kafka UI in Docker"`

---

### Task 5: Documentation

**Files:**
- Modify: `README.md`, `demo/README.md`, `docs/architecture.md`, `docs/camunda-basics.md`, `docs/development.md`, `docs/pitfalls.md`

**Interfaces:**
- Consumes: names, ports and commands from Tasks 1–4 exactly as built.

- [ ] **Step 1:** `README.md`: new intro (example = documents from Kafka, classified, routed to one of two destinations), new ASCII picture of the flow, quickstart (Java 21+, Node 22.18+, c8ctl, Docker; `demo/run-demo.sh`), learning path, layout, versions. Keep the build badge.
- [ ] **Step 2:** `docs/architecture.md`: the service and what it deploys; the process table (from Task 3); both message contracts with examples; what the connectors runtime does vs. what Java does; simulated behaviour (classifier).
- [ ] **Step 3:** `docs/camunda-basics.md`: trim to concepts the demo uses (BPMN events/tasks/gateways, variables and mappings, FEEL in conditions and connector expressions, jobs and workers, messages, connectors inbound/outbound, deployment). Remove DMN, user tasks, timers, call activities, BPMN errors; drop "where the demo uses it" pointers that no longer exist.
- [ ] **Step 4:** `docs/development.md`: setup (+ Docker for Kafka), build and test (one module), process tests (connector jobs completed by the scenarios), lint (`c8ctl bpmn lint` only), run the service alone, "Change the process" exercise (e.g. a third route), conventions (drop DMN/error-code rows).
- [ ] **Step 5:** `docs/pitfalls.md`: keep entries that still apply (exclusive gateway without default flow, step hangs after restart, process not deployed yet, Testcontainers/Colima, engine container health, first test timeout, harmless warnings, business vs. technical failure, logging starter); remove the rest; add what Task 4 revealed (at least: connectors runtime on the host needs `localhost:9092` advertised, `autoOffsetReset` latest vs. earliest, non-JSON messages).
- [ ] **Step 6:** `demo/README.md`: start, send a document (script and Kafka UI), watch in Operate, read results in Kafka UI, troubleshooting.
- [ ] **Step 7:** `grep -rn -i "input-service\|invoice-service\|output-service\|tasklist\|manual-triage\|document-intake\|dmn" README.md docs demo` → only hits in `docs/superpowers/` or intended mentions (e.g. "no DMN").
- [ ] **Step 8: Commit** — `git commit -m "Rewrite docs for the Kafka example"`
