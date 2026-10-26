# Input management

Document intake with Camunda 8: incoming letters and emails are stored, their text extracted,
classified, and routed to a follow-up process. Storage, OCR, classification, invoice extraction and
supplier lookup are simulated. Invoices go to `process-invoice`, everything else to `manual-triage`.

## Contents

Models live in `src/main/resources/processes/`, one folder per process with the decisions and forms it uses:

| File | Purpose |
|---|---|
| `document-intake/document-intake.bpmn` | Main process: intake → classify → (manual classification) → route → follow-up process |
| `document-intake/routing-decision.dmn` | Which follow-up process handles which document type (hit policy FIRST, catch-all last) |
| `manual-triage/manual-triage.bpmn` | Follow-up process for everything not yet automated; can throw `DOCUMENT_PROCESSING_FAILED` |
| `process-invoice/process-invoice.bpmn` | Follow-up process for invoices: check → approve (if needed) → pay or reject |
| `process-invoice/invoice-approval.dmn` | Whether an invoice needs approval, and by which group (≤ 1,000 none, ≤ 10,000 accounting, above management) |
| `*/*.form` | Tasklist forms for the user tasks, next to the process that uses them |

Workers:

| Class | Job types |
|---|---|
| `intake.DocumentIntakeWorkers` | `store-document`, `extract-text`, `classify-document`, `notify-team-lead` |
| `invoice.InvoiceWorkers` | `extract-invoice-data`, `find-supplier`, `schedule-payment`, `send-invoice-rejection` |

Known suppliers (simulated, by email domain): `example.com`, `buero-schmidt.de`. Invoices from other senders,
including all letters, fail with `INVOICE_INVALID` and end up in "Handle failed document".

## Error contract

A follow-up process reports a document it cannot process by ending with an error end event with code
`DOCUMENT_PROCESSING_FAILED`. `document-intake` catches it on the call activity and creates "Handle failed document".
Internal errors (e.g. `INVOICE_INVALID` in `process-invoice`) are translated into this code inside the follow-up process.

## Run locally

```bash
c8ctl cluster start                 # local Camunda 8 on http://localhost:8080 (demo/demo)
mvn spring-boot:run                 # deploys all models, starts the workers
```

Send documents:

```bash
c8ctl publish msg email-received --correlationKey=mail-1 --profile=local \
  --variables='{"sender":"max@example.com","subject":"Rechnung 4711","body":"Anbei die Rechnung."}'

c8ctl publish msg letter-scanned --correlationKey=scan-1 --profile=local \
  --variables='{"sender":"Erika Musterfrau","scannedText":"Hiermit reiche ich eine Beschwerde ein."}'
```

The correlation key must be unique per document: Camunda starts only one active instance per key.

## Tests

```bash
mvn test
```

Process tests (`ProcessTest`, scenarios in `src/test/resources/scenarios/`) run the engine in Docker via
Testcontainers. With Colima, `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` must be set.
The coverage report is written to `target/coverage-report/report.html`.

Watch instances in Operate (http://localhost:8080/operate) and complete tasks in
Tasklist (http://localhost:8080/tasklist).

## Adding a follow-up process

1. Model a new process in its own folder, e.g. `processes/process-complaint/process-complaint.bpmn`, with its forms and decisions next to it. To report a failure back, end with an error end event
   using error code `DOCUMENT_PROCESSING_FAILED`.
2. Add a rule **above** the catch-all in `document-intake/routing-decision.dmn`, e.g. `docType = "complaint"` → `"process-complaint"`.

`document-intake.bpmn` does not change.
