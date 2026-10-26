# Camunda 8 Demo

A hands-on demo for learning [Camunda 8](https://docs.camunda.io/): process orchestration with BPMN,
business rules with DMN, human tasks with forms, and Java/Spring Boot job workers.

The example domain is **input management**: incoming letters and emails with unknown content are
stored, their text is extracted, they are classified, and each document is routed to a follow-up
process that handles it.

## What's in the repository

| Folder | Content |
|---|---|
| [`input-management/`](input-management/) | Spring Boot application with the BPMN/DMN models, forms, job workers and tests. See its [README](input-management/README.md) for details. |
| `.claude/` | Claude Code project settings (enables the Camunda skills plugin) |

## The processes

```
 Letter / Email ──▶ document-intake ──▶ routing-decision (DMN) ──▶ follow-up process
                    store, extract,       which process handles     ├─ process-invoice   check, approve by amount, pay or reject
                    classify, manual      this document type?       └─ manual-triage     catch-all: a person handles it
                    classification
                    if unsure
```

- **document-intake**: the main process. Two message start events (letter, email), service tasks for
  storage, text extraction and classification, a user task with a reminder timer when the classifier
  is unsure, and a call activity that starts the follow-up process chosen by the routing decision.
- **process-invoice**: checks the invoice in an embedded subprocess, lets a DMN decide whether and by
  whom it must be approved, then schedules the payment or informs the supplier of the rejection.
- **manual-triage**: the catch-all for every document type that has no automated processing yet.

A follow-up process that cannot handle a document ends with the error `DOCUMENT_PROCESSING_FAILED`;
the intake process catches it and creates a task for a clerk. New document types are added with a new
follow-up process and one row in the routing table, without changing the intake process.

All external systems (document store, OCR, classifier, supplier master data, payment) are simulated
inside the job workers.

## Camunda concepts covered

| Concept | Where |
|---|---|
| Message start events, output mappings | `document-intake`: letter and email channels |
| Service tasks and job workers, idempotency | All automated steps (`*Workers.java`) |
| Exclusive gateways with default flows | Confidence check, approval decisions |
| User tasks with Camunda forms, candidate groups | Manual classification, triage, invoice approval |
| Non-interrupting timer boundary event | Reminder to the team lead after 4 hours |
| Business rule tasks with DMN (hit policies FIRST and UNIQUE) | `routing-decision.dmn`, `invoice-approval.dmn` |
| Call activity with a dynamic target | `document-intake` → follow-up process chosen by DMN |
| Embedded subprocess as an error scope | `process-invoice`: "Check invoice" |
| BPMN errors: thrown by workers, caught, translated, propagated | `INVOICE_INVALID` → `DOCUMENT_PROCESSING_FAILED` |
| Dynamic task assignment from a DMN result | Invoice approval by `accounting` or `management` |
| Automated process tests with coverage | Camunda Process Test scenarios (`*.test.json`) |

## Getting started

### Prerequisites

- Java 21+ and Maven
- Node.js 22.18+ (for the `c8ctl` CLI)
- Docker (only for the process tests)

### Run it

```bash
npm install -g @camunda8/cli          # c8ctl, the Camunda 8 CLI
c8ctl cluster start                   # local Camunda 8 on http://localhost:8080 (login demo/demo)

cd input-management
mvn spring-boot:run                   # deploys the models and starts the job workers
```

Send a document:

```bash
c8ctl publish msg email-received --correlationKey=demo-1 --profile=local \
  --variables=@samples/invoice-medium.json
```

Then follow it in **Operate** (http://localhost:8080/operate) and complete the open tasks in
**Tasklist** (http://localhost:8080/tasklist). More sample documents are in
[`input-management/samples/`](input-management/samples/).

### Test it

```bash
cd input-management
mvn test
```

Runs the unit tests and the process scenarios against a Camunda engine in Docker (Testcontainers).
The coverage report is written to `input-management/target/coverage-report/report.html`.

With Colima instead of Docker Desktop, Testcontainers needs the socket location:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Versions

Camunda 8.10, Spring Boot 4.1, Java 21. The Camunda client version in `input-management/pom.xml`
should match the cluster version.
