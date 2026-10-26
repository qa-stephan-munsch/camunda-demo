# Camunda 8 Demo

[![Build](https://github.com/qa-stephan-munsch/camunda-demo/actions/workflows/build.yml/badge.svg)](https://github.com/qa-stephan-munsch/camunda-demo/actions/workflows/build.yml)

A hands-on introduction to [Camunda 8](https://docs.camunda.io/) for developers: process orchestration
with BPMN, business rules with DMN, human tasks with forms, and Spring Boot services that do the work.

The example is **input management**: incoming letters and emails with unknown content are stored,
read, classified and routed to a follow-up process that handles them. Four small services share the
work, and Camunda orchestrates them. The services never call each other.

```
 curl POST /documents ──▶ input-service ──starts──▶ document-intake ──calls──▶ process-invoice / manual-triage
                                                        │                            │
                                                  document-service             invoice-service
                                                        │
                                          "Report result" ──▶ output-service ──▶ RESULT line in the log
```

## Quickstart

Prerequisites: macOS with Java 21+, Node.js 22.18+, and Docker (Docker Desktop or Colima) for the tests.

```bash
npm install -g @camunda8/cli      # c8ctl, the Camunda 8 CLI (once)
demo/run-demo.sh                  # starts Camunda locally if needed, builds, starts all four services
```

In a second terminal, from the repository root:

```bash
curl -X POST localhost:8081/documents -H 'Content-Type: application/json' -d @demo/samples/invoice-small.json
```

The first terminal shows the document travelling through the services:

```
[input-service]    Received email from buchhaltung@example.com, started document-intake instance 2251799813706782
[document-service] Classified doc-2251799813706782 as invoice (confidence 0.93)
[invoice-service]  Invoice 1001 over 250.00 EUR found in doc-2251799813706782
[invoice-service]  Scheduled payment pay-doc-2251799813706782 of 250.0 EUR to supplier S-1001
[output-service]   RESULT doc-2251799813706782 | email from buchhaltung@example.com | invoice | invoice 1001 over 250.00 EUR, no approval needed, payment pay-doc-2251799813706782 scheduled
```

More samples, the IntelliJ HTTP requests and how to follow a document in Operate and Tasklist:
[`demo/README.md`](demo/README.md).

## Learning path

1. **[Camunda basics](docs/camunda-basics.md):** the concepts (BPMN, DMN, FEEL, jobs, messages),
   each with a pointer to where the demo uses it.
2. **[Run the demo](demo/README.md):** send the sample documents, complete the tasks in Tasklist,
   watch the instances in Operate.
3. **Open the models** in [Camunda Desktop Modeler](https://camunda.com/download/modeler/):
   `*-service/src/main/resources/processes/`.
4. **[Architecture](docs/architecture.md):** services, processes and the contracts between them.
5. **[Development](docs/development.md):** build, test, lint, and add a follow-up process yourself.
6. **[Pitfalls](docs/pitfalls.md):** what tripped us up while building this, and why.

## Repository layout

```
camunda-demo/
├── README.md                this file
├── docs/                    camunda-basics, architecture, development, pitfalls
├── demo/                    run-demo.sh, sample documents, IntelliJ HTTP requests
├── pom.xml, mvnw            Maven parent project and wrapper
├── input-service/           REST entry point, starts document-intake
├── document-service/        store, read, classify; owns document-intake and manual-triage
├── invoice-service/         check, approve, pay; owns process-invoice
├── output-service/          logs the result of every document
└── .github/workflows/       build and tests on every push
```

## Versions

Camunda 8.10, Spring Boot 4.1, Java 21. The Camunda version in `pom.xml` should match the cluster
(`c8ctl cluster status`).
