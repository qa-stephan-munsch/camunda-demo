# Camunda 8 basics

What a developer needs to know to understand this demo, with pointers to where the demo uses each
concept. The official documentation goes deeper: https://docs.camunda.io/

## The idea

You model a process as a **BPMN** diagram and deploy it to the Camunda engine. The engine executes the
diagram: it creates an instance per case (here: per document), remembers where each instance is, waits
for systems, handles retries and errors. The diagram *is* the executable artifact, and business and
developers talk about the same picture.

Your code does not drive the process. It provides the **work** for individual steps, and the engine
decides what happens next.

## The building blocks of Camunda 8

| Component | Purpose | In this demo |
|---|---|---|
| **Zeebe** | The workflow engine: executes BPMN and DMN, stores state | Runs in the local cluster (`c8ctl cluster start`) |
| **Job workers** | Your code; they fetch and complete jobs for service tasks | `ClassifyDocumentWorker` in `document-service` |
| **Connectors** | Prebuilt integrations (Kafka, REST, Slack, …), configured in the model instead of coded | Kafka in (start event) and Kafka out (B and C) |
| **Operate** | Monitoring: instances, variables, incidents, decisions | http://localhost:8080/operate |
| **Modeler** | Desktop or web tool to draw BPMN and DMN | Open `document-routing.bpmn` or `routing-decision.dmn` |

The previous generation, Camunda 7, embedded the engine in your Java application. Camunda 8 runs the
engine as a separate, scalable cluster; your applications talk to it over REST or gRPC. Everything in
this repository is Camunda 8.

## BPMN essentials

**Think in tokens.** A process instance starts with a token at a start event. The engine moves the
token along the sequence flows; where the token waits is the instance's current state.

| Element | Meaning | In this demo |
|---|---|---|
| **Message start event** | An instance starts when a matching message arrives | "Document received", fed by the Kafka inbound connector |
| **Service task** | Automated work, done by a job worker or a connector | "Classify document" (worker), "Forward to accounting" / "Forward to general inbox" (connector) |
| **Business rule task** | Evaluates a DMN decision and stores the result in a variable | "Determine route" → `routing` |
| **Exclusive gateway** (XOR) | Exactly one path: the first condition that is true, otherwise the default flow | "Which route?"; the second, unnamed one merges the three paths again |
| **End event** | The path is done; the instance completes when all its tokens are done | "Document routed" |

Always give an exclusive gateway a default flow: if no condition matches and there is no default, the
instance gets an incident. "Which route?" sends everything that isn't `accounting` or
`customer-service` to C by default.

BPMN has much more (user tasks, timers, error events, call activities, parallel gateways). The demo leaves them out on purpose; the
[BPMN reference](https://docs.camunda.io/docs/components/modeler/bpmn/) covers them.

## Variables

Every instance has **variables** (JSON values). Here, the start event sets `filename`, `headline` and
`content` from the Kafka message; the worker of step A adds `category` and `urgent`; the decision adds `routing`;
B and C read all of them to build the result message. Workers receive the variables they ask for and return new ones.

## FEEL

The expression language for conditions, mappings and DMN cells. In BPMN fields an expression starts
with `=`:

```
=routing.route = "accounting"                                          condition of "Which route?" → B
={filename: value.filename, headline: value.headline, content: value.content}   start event: Kafka message → variables
={filename: filename, headline: headline, category: category,
  routedTo: routing.route, priority: routing.priority}                 B, C, D: variables → Kafka message
```

Try expressions with `c8ctl feel evaluate '…' --var name=value --profile=local`.

## DMN

Decisions as **decision tables**: input columns, output columns, one rule per row. The engine
evaluates them; no code involved. The **hit policy** says what happens when several rules match:

| Hit policy | Result | In this demo |
|---|---|---|
| **FIRST** | The first matching rule wins, top to bottom; good for a catch-all last row | `routing-decision.dmn` |
| **UNIQUE** | Exactly one rule may match; overlaps are an error | Not used |
| ANY, RULE ORDER, COLLECT | Several matches with the same output / all outputs as a list / aggregated | Not used |

Cells use FEEL unary tests: `"invoice"`, `true`, `-` (anything, also a missing value), `<= 1000`,
`(1000..10000]`. A business rule task stores the result in a variable (`resultVariable`): with one
output column the plain value, with several an object, here `routing` with `routing.route` and
`routing.priority`.

`routing-decision.dmn` has two inputs (`category`, `urgent`) and two outputs (`route`, `priority`).
With FIRST, the urgent rows come before the general rows of the same category, and the catch-all comes
last. As gateway conditions, the same five rules would be hard to read; as a table, each rule is one
row ([the table](architecture.md#decision-routingdecision)).

The point of DMN: rules change without touching the process or the code. Sending urgent complaints to
accounting, or giving every invoice high priority, is one cell in `routing-decision.dmn`. Operate shows each evaluation: click "Determine route" in
an instance, or open the *Decisions* tab, to see the input and the matched rule.

## Messages

A **message** has a name, an optional correlation key and variables. It either starts an instance
(message start event) or continues a waiting one. The Kafka inbound connector turns every Kafka record
into such a message for "Document received" (message name `document-received`). The process tests
publish the same message directly, without Kafka.

## Jobs, retries, errors

When a token reaches a service task, the engine creates a **job** of the task's type. A worker
subscribed to that type activates it (with a **lease**, 5 minutes by default), does the work and
completes it with result variables.

- `ClassifyDocument` has the type `classify-document`; `ClassifyDocumentWorker` handles it.
- B and C have the type `io.camunda:connector-kafka:1`; the connectors runtime handles them, with the
  inputs from the model (server, topic, key, value).

If a job fails (Kafka down, an exception in the worker), the engine retries it; B and C retry 3 times,
30 seconds apart. After the last retry, the instance gets an **incident** that you resolve in Operate.

Jobs are delivered **at least once**: after a timeout or a crash, the same job can be handled again.
Workers must therefore be **idempotent**. Classifying a document twice gives the same result, so step A
is.

## Connectors

A connector is a ready-made integration that the **connectors runtime** executes; c8run starts one
next to the engine (port 8086). You don't write code for it, you apply an **element template** to a
BPMN element in the Modeler and fill in its fields.

| Kind | Element | What it does | In this demo |
|---|---|---|---|
| **Inbound** | Start event, intermediate event, … | Listens to an external system and starts or continues instances | Kafka Message Start Event on `documents-in` |
| **Outbound** | Service task | Does one call when the token arrives, like a job worker would | Kafka Producer on `documents-out` in B and C |

The connectors runtime reads the inbound configuration from every deployed model and opens a Kafka
consumer as soon as `document-routing` is deployed. That's why documents are picked up even while
`document-service` is stopped: Kafka is read by the connectors runtime, not by our service.

## Deployment and versions

Deploying a model creates a new **version** if its content changed. New instances use the latest
version; running instances stay on the version they started with. `document-service` deploys its model
on every start (`@Deployment`); an unchanged model doesn't create a new version.

## Further reading

- Camunda docs: https://docs.camunda.io/
- Camunda Academy (free courses): https://academy.camunda.com/
- Kafka connector: https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/
- FEEL reference: https://docs.camunda.io/docs/components/modeler/feel/what-is-feel/
- Bernd Rücker, *Practical Process Automation* (O'Reilly): why orchestration, and how to design it
