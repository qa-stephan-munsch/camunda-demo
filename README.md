# Camunda 8 Demo

[![Build](https://github.com/qa-stephan-munsch/camunda-demo/actions/workflows/build.yml/badge.svg)](https://github.com/qa-stephan-munsch/camunda-demo/actions/workflows/build.yml)

A hands-on introduction to [Camunda 8](https://docs.camunda.io/) for developers. It shows, with a small
but complete system, how process orchestration works in practice: processes modelled in BPMN, business
rules in DMN, human tasks with forms, and Spring Boot services that do the actual work.

## What this repository is about

The example is **input management**: a company receives letters and emails with unknown content. Each
document is stored, its text extracted and classified, and it is routed to a follow-up process that
handles it. Invoices are checked, approved depending on their amount and paid; everything the system
doesn't know yet goes to a person. Every document ends with a result.

```
  POST /documents ──▶ input-service ──starts──▶ document-intake ──calls──▶ process-invoice / manual-triage
                                                    │                            │
                                              document-service             invoice-service
                                                    │
                                      "Report result" ──▶ output-service ──▶ RESULT line in the log
```

Four small services share the work, and **they never call each other**: Camunda decides what happens
next, waits for people, handles errors and knows where every document is. That is the core idea of
orchestration and the main thing this demo wants to show.

Along the way it covers the Camunda concepts you meet in real projects: message start events, service
tasks and job workers, user tasks with forms, timers, DMN decisions, call activities, BPMN errors,
variable mappings, and automated process tests. All external systems (OCR, classifier, ERP, payment)
are simulated, so the demo runs on a laptop without any other infrastructure.

## Quickstart

Prerequisites: macOS with Java 21+ and Node.js 22.18+.

```bash
npm install -g @camunda8/cli      # c8ctl, the Camunda 8 CLI (once)
demo/run-demo.sh                  # starts Camunda locally if needed, builds, starts all four services
```

The script shows the log of all four services in one terminal. How to send documents, which samples
there are, and how to follow them in Operate and Tasklist: see the **[demo folder](demo/README.md)**.

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
