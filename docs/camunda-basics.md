# Camunda 8 basics

What a developer needs to know to understand this demo, with pointers to where the demo uses each
concept. The official documentation goes deeper: https://docs.camunda.io/

## The idea

You model a process as a **BPMN** diagram and deploy it to the Camunda engine. The engine executes the
diagram: it creates an instance per case (here: per document), remembers where each instance is, waits
for people or systems, handles timers, retries and errors. The diagram *is* the executable artifact,
and business and developers talk about the same picture.

Your code does not drive the process. It provides the **work** for individual steps, and the engine
decides what happens next.

## The building blocks of Camunda 8

| Component | Purpose | In this demo |
|---|---|---|
| **Zeebe** | The workflow engine: executes BPMN and DMN, stores state | Runs in the local cluster (`c8ctl cluster start`) |
| **Job workers** | Your code; they fetch and complete jobs for service tasks | `*Workers.java` in the services |
| **Operate** | Monitoring: instances, variables, incidents, decisions | http://localhost:8080/operate |
| **Tasklist** | UI for user tasks, renders Camunda forms | http://localhost:8080/tasklist |
| **Connectors** | Prebuilt integrations (REST, Kafka, Slack, …) instead of own workers | Not used; all steps are own workers |
| **Modeler** | Desktop or web tool to draw BPMN, DMN and forms | Open any `.bpmn` / `.dmn` / `.form` file |

The previous generation, Camunda 7, embedded the engine in your Java application. Camunda 8 runs the
engine as a separate, scalable cluster; your applications talk to it over REST or gRPC. Everything in
this repository is Camunda 8.

## BPMN essentials

**Think in tokens.** A process instance starts with a token at a start event. The engine moves the
token along the sequence flows; where the token waits is the instance's current state. Parallel
gateways split it, joining gateways merge it again.

### Events (circles)

| Event | Meaning | In this demo |
|---|---|---|
| **Message start event** | An instance starts when a matching message arrives | `document-intake`: "Email received", "Letter scanned" |
| **Timer boundary event**, non-interrupting | After a duration, start an extra path; the task stays active | "4 hours" on "Classify document manually" → notify team lead |
| **Error boundary event** | Catches a BPMN error thrown inside the activity it's attached to | "Processing failed" on the call activity, "Invoice invalid" on "Check invoice" |
| **Error end event** | Ends the path by throwing a BPMN error to the enclosing scope | `manual-triage` and `process-invoice`: `DOCUMENT_PROCESSING_FAILED` |
| **End event** | The path is done; the instance completes when all its tokens are done | "Document done", "Payment scheduled" |

### Activities (rounded rectangles)

| Activity | Meaning | In this demo |
|---|---|---|
| **Service task** | Automated work, done by a job worker (or a connector) | "Store document", "Find supplier", … |
| **User task** | Work for a person, shown in Tasklist with a form | "Classify document manually", "Approve invoice" |
| **Business rule task** | Evaluates a DMN decision | "Determine routing", "Determine approval" |
| **Embedded subprocess** | Groups steps, e.g. as a scope for one boundary event | "Check invoice" |
| **Call activity** | Starts another, separately deployed process and waits for it | "Process document", target chosen by DMN |

### Gateways (diamonds)

| Gateway | Splits into | In this demo |
|---|---|---|
| **Exclusive (XOR)** | Exactly one path: the first condition that is true, otherwise the default flow | "Classification confident?", "Approval required?" |
| **Parallel (AND)** | All paths at once; the joining gateway waits for all | Not used |

Always give an exclusive gateway a default flow: if no condition matches and there is no default, the
instance gets an incident.

## Variables and mappings

Every instance has **variables** (JSON values). Workers receive the variables they ask for and return
new ones; user tasks write their form fields back as variables.

**Input/output mappings** (FEEL expressions on an element) control what goes into and out of a scope.
Important: once an element has output mappings, *only* the mapped variables are passed on. This
tripped us up three times ([pitfalls](pitfalls.md#output-mappings-drop-every-other-variable)).

In this demo: the message start events map the message fields, the call activity takes over only
`outcome` from the child process, "Handle failed document" turns the clerk's notes into the `outcome`.

## FEEL

The expression language for conditions, mappings and DMN cells. In BPMN fields an expression starts
with `=`:

```
=confidence != null and confidence >= 0.85
=if resolutionNotes = null then "failed, resolved by clerk" else "failed, resolved by clerk: " + resolutionNotes
=[approval.approverGroup]
```

Try expressions with `c8ctl feel evaluate '…' --var name=value --profile=local`.

## DMN

Decisions as **decision tables**: input columns, output columns, one rule per row. The **hit policy**
says what happens when several rules match:

| Hit policy | Result | In this demo |
|---|---|---|
| **UNIQUE** | Exactly one rule may match; overlaps are an error | `invoice-approval.dmn` (amount ranges) |
| **FIRST** | The first matching rule wins, top to bottom; good for a catch-all last row | `routing-decision.dmn` |
| ANY, RULE ORDER, COLLECT | Several matches with the same output / all outputs as a list / aggregated | Not used |

Cells use FEEL unary tests: `<= 1000`, `(1000..10000]`, `"invoice"`, `-` (anything).
A business rule task stores the result in a variable (`resultVariable`); with several output columns
the result is an object, e.g. `routing.targetProcess`.

The point of DMN: rules change without touching the process or the code. Changing the approval limit
is one cell in `invoice-approval.dmn`.

## Messages and correlation

A **message** has a name, a correlation key and variables. It either starts an instance (message start
event) or continues a waiting one (intermediate message event). Camunda starts at most one active
instance per message name and correlation key, so keys must be unique per case.

In this demo the input service correlates `email-received` / `letter-scanned` with a fresh correlation
key per document and gets the new process instance key back.

## Jobs, retries, errors

When a token reaches a service task, the engine creates a **job** of the task's type. A worker
subscribed to that type activates it (with a **lease**, 5 minutes by default), does the work and
completes it with result variables. The worker has three ways to fail:

| Situation | What the worker does | What the engine does | In this demo |
|---|---|---|---|
| Technical problem (system down, timeout) | Fail the job | Retries with back-off; after the last retry an **incident** that you resolve in Operate | Not simulated |
| Business outcome (invoice unreadable, supplier unknown) | Throw a **BPMN error** with a code | No retry; takes the error boundary path that catches the code | `INVOICE_INVALID` |
| Programming bug (exception) | Nothing, it just throws | Treated like a failed job with zero back-off | – |

Jobs are delivered **at least once**: after a timeout or a crash, the same job can be handled again.
Workers must therefore be **idempotent**; here, IDs are derived from the instance or document instead
of being generated randomly.

## Deployment and versions

Deploying a model creates a new **version** if its content changed. New instances use the latest
version; running instances stay on the version they started with. The demo's services deploy their
models on every start (`@Deployment`); unchanged models don't create new versions.

## Further reading

- Camunda docs: https://docs.camunda.io/
- Camunda Academy (free courses): https://academy.camunda.com/
- BPMN element reference: https://docs.camunda.io/docs/components/modeler/bpmn/
- FEEL reference: https://docs.camunda.io/docs/components/modeler/feel/what-is-feel/
- Bernd Rücker, *Practical Process Automation* (O'Reilly): why orchestration, and how to design it
