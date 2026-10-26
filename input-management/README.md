# Input management

Spring Boot application for the [Camunda 8 demo](../README.md): incoming letters and emails are stored,
their text is extracted, they are classified, and each document is routed to a follow-up process.
The application deploys all models on startup and hosts the job workers for every service task.

All external systems (document store, OCR, classifier, supplier master data, payment) are simulated
inside the workers.

## Project layout

```
input-management/
├── pom.xml
├── samples/                                  sample input documents (JSON)
└── src/
    ├── main/java/de/qaware/inputmanagement/
    │   ├── InputManagementApplication.java   deploys processes/** on startup
    │   ├── intake/                           workers for document-intake
    │   └── invoice/                          workers for process-invoice
    ├── main/resources/
    │   ├── application.yaml                  connection to the local cluster
    │   └── processes/                        one folder per process, with its decisions and forms
    │       ├── document-intake/
    │       ├── manual-triage/
    │       └── process-invoice/
    └── test/
        ├── java/…/ProcessTest.java           runs the process scenarios
        └── resources/scenarios/              Camunda Process Test scenarios (*.test.json)
```

## Processes

### document-intake

| Step | Type | What happens |
|---|---|---|
| Letter scanned / Email received | Message start events | Start an instance per incoming document and set `channel` |
| Store document | Service task `store-document` | Stores the original, returns `documentId` |
| Extract text | Service task `extract-text` | OCR text (letters) or subject + body (emails), returns `textRef` and `textPreview` |
| Classify document | Service task `classify-document` | Returns `docType` and `confidence` |
| Classification confident? | Exclusive gateway | `confidence >= 0.85`, otherwise manual classification |
| Classify document manually | User task, group `intake-clerks` | A clerk confirms or corrects `docType`; after 4 hours a non-interrupting timer notifies the team lead |
| Determine routing | Business rule task → `routingDecision` | Result in `routing.targetProcess` and `routing.priority` |
| Process document | Call activity | Starts the process named in `routing.targetProcess` |
| Handle failed document | User task, group `intake-clerks` | Reached when the follow-up process ends with `DOCUMENT_PROCESSING_FAILED` |

`routing-decision.dmn` (hit policy FIRST):

| docType | → targetProcess |
|---|---|
| `"invoice"` | `process-invoice` |
| anything else | `manual-triage` |

### process-invoice

| Step | Type | What happens |
|---|---|---|
| Check invoice | Embedded subprocess | Error scope for the two steps below |
| Extract invoice data | Service task `extract-invoice-data` | Returns `invoiceNumber`, `amount`, `currency`; throws `INVOICE_INVALID` if not found |
| Find supplier | Service task `find-supplier` | Returns `supplierId`, `supplierName`; throws `INVOICE_INVALID` for unknown senders |
| Determine approval | Business rule task → `invoiceApproval` | Result in `approval.approvalRequired` and `approval.approverGroup` |
| Approve invoice | User task, group from the DMN result | Sets `approvalDecision` (`approve` / `reject`) and `approvalComment` |
| Schedule payment | Service task `schedule-payment` | Returns `paymentId` |
| Send rejection to supplier | Service task `send-invoice-rejection` | Informs the supplier |

`INVOICE_INVALID` is caught on "Check invoice" and translated into `DOCUMENT_PROCESSING_FAILED`.

`invoice-approval.dmn` (hit policy UNIQUE):

| amount | → approvalRequired | → approverGroup |
|---|---|---|
| ≤ 1,000 | `false` | – |
| > 1,000 and ≤ 10,000 | `true` | `accounting` |
| > 10,000 | `true` | `management` |

### manual-triage

The catch-all for document types without automated processing. A clerk (`intake-clerks`) handles the
document and records `triageOutcome`: `handled` ends normally, `unprocessable` ends with
`DOCUMENT_PROCESSING_FAILED`.

## Error contract

A follow-up process reports a document it cannot process by ending with an error end event with code
`DOCUMENT_PROCESSING_FAILED`. `document-intake` catches it on the call activity and creates
"Handle failed document". Internal errors of a follow-up process (such as `INVOICE_INVALID`) are
translated into this code inside that process, so the intake process only knows one error.

## Input messages

| Message | Variables |
|---|---|
| `letter-scanned` | `sender`, `scannedText` |
| `email-received` | `sender`, `subject`, `body` |

Only these variables reach the process: the start events map them explicitly (when an event has output
mappings, unmapped message variables are dropped).

The correlation key must be unique per document: Camunda starts at most one active instance per
message name and correlation key.

## Simulated behaviour

| Worker logic | Behaviour |
|---|---|
| Classification (`KeywordClassifier`) | Keywords per type (e.g. *Rechnung*, *Beschwerde*, *Kündigung*, *Umzug*). One match: confidence 0.93 for emails, 0.74 for letters (OCR penalty). Several matches: 0.5. None: `other` with 0.3 |
| Invoice data (`InvoiceTextParser`) | Invoice number after *Rechnung* / *Invoice* (optionally *Nr.* / *No.*), amount in German format followed by *EUR* or *€*, e.g. `1.250,00 EUR` |
| Suppliers (`SupplierRegistry`) | By email domain: `example.com` → Muster GmbH, `buero-schmidt.de` → Büro Schmidt KG. Other senders, including all letters, are unknown |
| Document and payment IDs | Derived from the process instance or document, so a redelivered job produces the same result (idempotent) |

## Run locally

```bash
c8ctl cluster start                 # local Camunda 8 on http://localhost:8080 (demo/demo)
mvn spring-boot:run                 # deploys all models, starts the workers
```

Send a sample document (use a new correlation key for every run):

```bash
c8ctl publish msg email-received --correlationKey=demo-1 --profile=local --variables=@samples/invoice-medium.json
c8ctl publish msg letter-scanned --correlationKey=demo-2 --profile=local --variables=@samples/complaint-letter.json
```

| Sample | Message | Expected path |
|---|---|---|
| `invoice-small.json` | `email-received` | Invoice over 250 EUR, paid without approval |
| `invoice-medium.json` | `email-received` | Invoice over 5,400 EUR, approval task for `accounting` |
| `invoice-large.json` | `email-received` | Invoice over 24,990 EUR, approval task for `management` |
| `invoice-unknown-supplier.json` | `email-received` | Unknown supplier, "Handle failed document" |
| `complaint-letter.json` | `letter-scanned` | Not confident (letter), manual classification, then triage |

### Follow a run

- **Operate** (http://localhost:8080/operate): where each instance is, its variables and incidents. Click a
  business rule task, or open the *Decisions* tab, to see a decision's inputs and the matched rule.
- **Tasklist** (http://localhost:8080/tasklist): complete the user tasks with their forms.
- **Logs** of this application: every worker logs what it did, e.g.
  `Classified doc-… as invoice (confidence 0.93)`.
- **CLI**: `c8ctl list pi --profile=local`, `c8ctl list ut --profile=local`,
  `c8ctl search inc --state=ACTIVE --profile=local`.

## Tests

```bash
mvn test
```

| Test | What it covers |
|---|---|
| `KeywordClassifierTest`, `InvoiceTextParserTest`, `SupplierRegistryTest` | Unit tests for the simulated worker logic |
| `ProcessTest` + `scenarios/*.test.json` | Every path through the processes and every DMN rule, against an engine in Docker |

The process tests switch the job workers off and complete the jobs themselves, so they test the
process logic independently of the worker implementations. The coverage report is written to
`target/coverage-report/report.html`. Error end events never count as completed in the report, so
`manual-triage` and `process-invoice` stay just below 100%.

With Colima, `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` must be set (see the
[repository README](../README.md#test-it)).

## Adding a follow-up process

1. Model the new process in its own folder, e.g. `processes/process-complaint/process-complaint.bpmn`,
   with its forms and decisions next to it. To report a failure, end with an error end event with code
   `DOCUMENT_PROCESSING_FAILED`.
2. Add a rule **above** the catch-all in `processes/document-intake/routing-decision.dmn`,
   e.g. `docType = "complaint"` → `"process-complaint"`.
3. Implement the workers for its service tasks and add a scenario file to `src/test/resources/scenarios/`
   (and the new resources to `@TestDeployment` in `ProcessTest`).

`document-intake.bpmn` does not change.

## Troubleshooting

| Symptom | Cause |
|---|---|
| A service task stays active for about 5 minutes after restarting the app | A worker had fetched the job and stopped before completing it. The engine hands the job out again when its lease (job timeout, 5 minutes by default) expires |
| Second message with the same correlation key starts no instance | Only one active instance per message name and correlation key |
| App fails with `Connection refused` on startup | The local cluster isn't running: `c8ctl cluster start` |
