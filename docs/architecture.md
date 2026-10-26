# Architecture

## Services and Camunda

```
                         ┌──────────────────── Camunda 8 ────────────────────┐
 curl POST /documents    │                                                    │
 ────────────────▶ input-service ──starts──▶ document-intake ──calls──▶ process-invoice / manual-triage
   (port 8081)           │                      │      ▲                 │      ▲
                         │                jobs  │      │ results   jobs  │      │
                         │                      ▼      │                 ▼      │
                         │               document-service            invoice-service
                         │                                                    │
                         │  "Report result" ──job──▶ output-service ── logs one RESULT line per document
                         └────────────────────────────────────────────────────┘
```

**The services never call each other.** The input service starts processes; the other services run
job workers that pick up the jobs of "their" steps. The order of steps, the waiting for people, retries
and error paths live in the process models, not in the code. This is orchestration: Camunda knows
where every document is and what comes next.

| Module | Role | Job types | Owns (deploys on startup) |
|---|---|---|---|
| `input-service` | REST `POST /documents` on port 8081, starts `document-intake` by message correlation | – | – |
| `document-service` | Stores, reads and classifies documents | `store-document`, `extract-text`, `classify-document`, `notify-team-lead` | `document-intake`, `manual-triage`, `routing-decision.dmn` and their forms |
| `invoice-service` | Checks, approves and pays invoices | `extract-invoice-data`, `find-supplier`, `schedule-payment`, `send-invoice-rejection` | `process-invoice`, `invoice-approval.dmn` and its form |
| `output-service` | Reports the result of every document | `report-result` | – |

**Process ownership:** each service deploys the processes it owns on startup
(`@Deployment(resources = "classpath*:/processes/**/*")`), kept in
`src/main/resources/processes/<process>/` next to the decisions and forms they use. A team changes
its processes and its workers together and tests them in its own module. The price: the models are
spread over the services, and the start order matters (a process must be deployed before it is used).

All external systems (document store, OCR, classifier, supplier master data, payment) are simulated
inside the workers.

## Processes

### document-intake (document-service)

| Step | Type | What happens |
|---|---|---|
| Letter scanned / Email received | Message start events | One instance per document; the events map the message fields and set `channel` |
| Store document | Service task `store-document` | Returns `documentId` |
| Extract text | Service task `extract-text` | Returns `textRef` and `textPreview` (only references and a preview travel as variables, never the document) |
| Classify document | Service task `classify-document` | Returns `docType` and `confidence` |
| Classification confident? | Exclusive gateway | `confidence >= 0.85`, otherwise manual classification |
| Classify document manually | User task, group `intake-clerks` | Confirms or corrects `docType`; after 4 hours a non-interrupting timer notifies the team lead |
| Determine routing | Business rule task → `routingDecision` | `routing.targetProcess`, `routing.priority` |
| Process document | Call activity | Starts `routing.targetProcess`; takes over only the child's `outcome` |
| Handle failed document | User task, group `intake-clerks` | On `DOCUMENT_PROCESSING_FAILED`; the resolution becomes the `outcome` |
| Report result | Service task `report-result` | Handled by the output service |

`routing-decision.dmn`, hit policy FIRST:

| docType | → targetProcess | → priority |
|---|---|---|
| `"invoice"` | `process-invoice` | `normal` |
| anything else | `manual-triage` | `normal` |

### process-invoice (invoice-service)

| Step | Type | What happens |
|---|---|---|
| Check invoice | Embedded subprocess | Error scope for the two steps below |
| Extract invoice data | Service task `extract-invoice-data` | `invoiceNumber`, `amount`, `currency`; throws `INVOICE_INVALID` if not found |
| Find supplier | Service task `find-supplier` | `supplierId`, `supplierName`; throws `INVOICE_INVALID` for unknown senders |
| Determine approval | Business rule task → `invoiceApproval` | `approval.approvalRequired`, `approval.approverGroup` |
| Approve invoice | User task, group from the DMN result | `approvalDecision` (`approve` / `reject`), `approvalComment` |
| Schedule payment / Send rejection to supplier | Service tasks | Set the `outcome` |

`INVOICE_INVALID` is caught on "Check invoice" and translated into `DOCUMENT_PROCESSING_FAILED`.

`invoice-approval.dmn`, hit policy UNIQUE:

| amount | → approvalRequired | → approverGroup |
|---|---|---|
| ≤ 1,000 | `false` | – |
| > 1,000 and ≤ 10,000 | `true` | `accounting` |
| > 10,000 | `true` | `management` |

### manual-triage (document-service)

The catch-all for document types without automated processing. A clerk records `triageOutcome`:
`handled` ends normally and sets the `outcome`, `unprocessable` ends with `DOCUMENT_PROCESSING_FAILED`.

## Contracts between the processes

Follow-up processes are owned by different services and are added without changing
`document-intake`, as long as they keep two rules:

| Contract | Rule |
|---|---|
| **Result** | Set a variable `outcome` (short text). The call activity hands only this variable back (`propagateAllChildVariables="false"` plus an output mapping), so a child's internal variables don't leak into the parent |
| **Failure** | End with an error end event with code `DOCUMENT_PROCESSING_FAILED`. Internal errors (like `INVOICE_INVALID`) are translated inside the follow-up process, so the intake process only knows one error |

The start messages are a contract too, between the input service and `document-intake`:

| Message | Variables |
|---|---|
| `email-received` | `sender`, `subject`, `body` |
| `letter-scanned` | `sender`, `scannedText` |

The message start events map exactly these variables; anything else in a message is dropped.

## Simulated behaviour

| Logic | Behaviour |
|---|---|
| Classification (`KeywordClassifier`) | Keywords per type (e.g. *Rechnung*, *Beschwerde*, *Kündigung*, *Umzug*). One match: confidence 0.93 for emails, 0.74 for letters (OCR penalty). Several matches: 0.5. None: `other` with 0.3 |
| Invoice data (`InvoiceTextParser`) | Invoice number after *Rechnung* / *Invoice* (optionally *Nr.* / *No.*), amount in German format followed by *EUR* or *€*, e.g. `1.250,00 EUR` |
| Suppliers (`SupplierRegistry`) | By email domain: `example.com` → Muster GmbH, `buero-schmidt.de` → Büro Schmidt KG. Other senders, including all letters, are unknown |
| Document and payment IDs | Derived from the process instance or document, so a redelivered job produces the same result (idempotent) |
