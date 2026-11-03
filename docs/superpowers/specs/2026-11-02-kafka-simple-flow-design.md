# Design: simplify the demo to Kafka in, one decision, Kafka out

Date: 2026-11-02 · Status: draft for review

## Intent

The current demo (four services, three processes, user tasks, DMN, call activities, BPMN errors)
shows a lot of Camunda, but it is too much to grasp quickly. The goal is a drastically simpler
example whose flow you **understand at a glance**. The business content of the steps is invented
and unimportant; it only exists so the flow means something.

Input and output change: documents arrive on a **Kafka topic** and results leave on a Kafka topic.
Kafka is attached with the **Camunda Kafka connectors**, so the example also shows how Camunda talks
to Kafka without custom integration code.

The new example **replaces** the current demo in this repository.

## Goals

- One process: `Kafka in → A → decision → B or C → Kafka out`.
- Input message on topic `documents-in`, as simple as possible:
  `{ "filename": "...", "headline": "...", "content": "..." }`. Each message starts one process instance.
- B and C both publish a result message to topic `documents-out`.
- Runs on a laptop: local Camunda (c8run via `c8ctl cluster start`) plus Kafka in Docker,
  started by `demo/run-demo.sh`.
- You can send a document and read the result without Kafka CLI tools.
- Automated process tests cover both paths and run without Kafka.

## Non-goals

- No manual work: no user tasks, forms, Tasklist, timers, escalation.
- No DMN, no call activities, no BPMN errors, no REST input, no invoice handling.
- No realistic classification, no persistence of documents, no auth/TLS on Kafka, no schema registry.
- No production readiness (scaling, dead-letter topics, idempotency).

## Anti-goals

- Anything that makes the flow harder to follow than the five boxes above: extra steps, extra
  services, clever abstractions.
- Kafka handling written in Java when a connector can do it (the user chose connectors).

## Decided by the user

- Kafka topic as input, document = filename, headline, content.
- One process with one decision: A, then B or C; B and C both send to outbound Kafka.
- No manual process.
- Content of A, B, C may be invented; understanding the example matters.
- Use the **Camunda Kafka connectors** (inbound start event, outbound tasks), not Spring Kafka code.
- **Replace** the current demo (old services, processes, docs, samples are removed or rewritten).

## The process (invented content, Claude's call)

Process id `document-routing`, owned and deployed by one service.

```
 Kafka "documents-in"                                         ┌─▶ B: Forward to accounting ──┐
 ──▶ (Document received) ──▶ A: Classify document ──▶ <invoice?>                              ├─▶ (Document routed)
     Kafka message start      job worker, Java          │     └─▶ C: Forward to general inbox ┘
     event (inbound connector)                          │         Kafka outbound connector tasks → "documents-out"
```

| Element | Type | What happens |
|---|---|---|
| Document received | Message start event with the **Kafka inbound connector** template | Consumes `documents-in`, maps `filename`, `headline`, `content` into process variables |
| A: Classify document | Service task, job type `classify-document`, Java worker | Sets `category` = `"invoice"` if headline or content contains "invoice" or "Rechnung" (case-insensitive), otherwise `"other"` |
| Invoice? | Exclusive gateway | `category = "invoice"` → B; default flow → C |
| B: Forward to accounting | Service task with the **Kafka outbound connector** template | Publishes to `documents-out` |
| C: Forward to general inbox | Service task with the **Kafka outbound connector** template | Publishes to `documents-out` |
| Document routed | End event | |

Result message on `documents-out` (same shape from B and C, Claude's call):

```json
{ "filename": "invoice-acme.txt", "headline": "...", "category": "invoice", "routedTo": "accounting" }
```

`routedTo` is `"accounting"` (B) or `"general-inbox"` (C). Message key: `filename`.

Why a gateway and not DMN: one condition reads more easily on the diagram than a decision table.

## Services (Claude's call)

One Spring Boot module, `document-service`, replaces all four services:

- deploys `document-routing.bpmn` on startup (`@Deployment`), as today;
- runs the `classify-document` job worker (logic in a small, unit-tested class, like today's
  `KeywordClassifier`);
- contains the process test.

`input-service`, `invoice-service`, `output-service` and their processes, forms and DMN files are
deleted. The parent `pom.xml` lists only `document-service`.

## Kafka and the connectors

- c8run already runs a connectors runtime (port 8086) that bundles the Kafka connector
  (`connector-kafka` 8.10.x). No extra connector deployment is needed.
- The connectors runtime runs on the host, so Kafka must be reachable as `localhost:9092`.
  Kafka runs as a single-node KRaft container (e.g. `apache/kafka`) via a `docker-compose.yml`,
  advertised listener `localhost:9092`. No auth.
- Bootstrap servers and topic names are written directly in the BPMN connector properties
  (`localhost:9092`, `documents-in`, `documents-out`). No connector secrets, to keep it visible.
- The inbound start event needs a unique consumer group id and a correlation/deduplication setting
  per message; the builder picks values that start exactly one instance per Kafka message.
- Topics are created up front (compose init step or auto-create); builder's choice.
- A **Kafka UI** container (e.g. `provectuslabs/kafka-ui` or `kafbat/kafka-ui`) next to Kafka, so
  messages can be produced to `documents-in` and read from `documents-out` in the browser.
- Additionally a tiny script `demo/send-document.sh <sample.json>` that produces a sample to
  `documents-in` via `docker exec` into the Kafka container (no local Kafka CLI needed).

## Running the demo

`demo/run-demo.sh` (rewritten, keeps today's style: preconditions, colored log, Ctrl+C stops):

1. checks Java, Docker; starts Camunda via `c8ctl cluster start` if not running (as today);
2. `docker compose up -d` for Kafka + Kafka UI, waits until Kafka is ready;
3. builds and starts `document-service`, shows its log;
4. prints where to look: Operate, Kafka UI, how to send a sample.

Samples in `demo/samples/`: at least one invoice (→ B) and one other document (→ C).

## Tests

- Unit test for the classifier.
- Process test with Camunda Process Test (Testcontainers, as today): both paths A → B and A → C.
  The test starts the process **by message/instance creation instead of Kafka** and completes the
  outbound connector jobs with a mock, so neither Kafka nor the connectors runtime are needed.
  It asserts the variables passed to B/C.
- CI workflow stays (`./mvnw verify`).

## Documentation

Rewrite to match the new example, keep it short:

- `README.md`: new picture, new quickstart, new layout.
- `docs/architecture.md`: the one process, the message contracts on both topics, the service.
- `docs/camunda-basics.md`: trim to the concepts the demo uses (BPMN events/tasks/gateway,
  variables and mappings, FEEL in conditions, jobs and workers, connectors inbound/outbound).
- `docs/development.md`: build, test, run the service alone, change the process.
- `docs/pitfalls.md`: keep only entries that still apply (e.g. Testcontainers/Colima, worker
  logging); add Kafka/connector pitfalls found while building.
- `demo/README.md`: start, send a document, watch in Operate and Kafka UI, read the result.
- Remove `demo/documents.http` and `demo/http-client.env.json` (no REST input anymore); remove
  `.dmnlintrc` if no DMN remains. Fix the stale `input-management/run-demo.sh` comment in
  `.gitignore`.

## Settled after review (Claude's calls)

- The process test starts `document-routing` by **publishing the start message** (the start event
  is a message start event, so a plain create-instance call doesn't apply) and mocks the job type of
  the Kafka outbound connector for B and C.
- Kafka gets two listeners: `localhost:9092` for the host (connectors runtime) and an internal one
  for the Kafka UI container.
- Deduplication on the inbound start event is per Kafka message (e.g. topic/partition/offset), not
  per `filename`: sending the same file twice starts two instances.
- The Maven artifact `input-management` and the package `de.qaware.inputmanagement` are kept, to
  keep the diff focused on the flow.

## Left to the builder

- Exact connector property names and template versions (take them from the 8.10 element templates).
- Kafka/Kafka UI image versions, compose details, topic creation.
- Package names, class names, Java details, sample texts.
- How the process test mocks the connector jobs.
- Wording of the docs.

## Change 2026-11-03: routing decision as DMN

Decided by the user after the first version: the routing rule becomes a DMN decision table instead of
a plain gateway condition. This replaces "No DMN" under Non-goals and "Why a gateway and not DMN".

- Business rule task "Determine route" (`DetermineRoute`) after A, decision `routingDecision` in
  `routing-decision.dmn`, hit policy FIRST: `"invoice"` → `"accounting"`, `-` → `"general-inbox"`;
  result variable `route` (Claude's call).
- Gateway "Which route?" (`Gateway_Route`): `=route = "accounting"` → B, default → C.
- B and C publish `routedTo: route`, so the route name lives only in the table.
- The classifier stays in Java.

## Change 2026-11-03: urgent and priority

Decided by the user to make the decision table worth having: two inputs, two outputs, a third route.

- Classifier: `category` = `invoice` / `complaint` / `other` (invoice keywords checked first) and
  `urgent` = true/false ("urgent", "dringend") (Claude's call on keywords).
- `routingDecision`, hit policy FIRST, inputs `category`, `urgent`, outputs `route`, `priority`:
  invoice+urgent → accounting/high; invoice → accounting/normal; complaint+urgent →
  customer-service/high; complaint → customer-service/normal; `-` → general-inbox/normal.
  Result variable `routing` (object).
- New task D "Forward to customer service" (`ForwardToCustomerService`), Kafka outbound to
  `documents-out`. Gateway: `routing.route = "accounting"` → B, `= "customer-service"` → D, default → C.
- Result message gains `priority`.
