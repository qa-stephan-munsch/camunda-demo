# Run the demo

Everything you need to try the demo by hand: a start script, sample documents and HTTP requests for
IntelliJ. All commands run from the **repository root**.

## Start

```bash
demo/run-demo.sh                # starts Camunda locally if needed, builds, starts all four services
demo/run-demo.sh --no-build     # skip the build
demo/run-demo.sh --verbose      # show every log line, not only the demo's own output
```

The script starts the services in the right order (the document and invoice services first, because
they deploy the processes) and shows one combined log with the service name in front of every line.
The full log of each service is in `demo/logs/<service>.log`.

**Ctrl+C** stops the services. The Camunda cluster keeps running; stop it with `c8ctl cluster stop`.

## Send documents

With curl:

```bash
curl -X POST localhost:8081/documents -H 'Content-Type: application/json' -d @demo/samples/invoice-medium.json
→ {"processInstanceKey":"2251799813703476","correlationKey":"input-7e57bc93-…"}
```

Or in IntelliJ: open [`documents.http`](documents.http), choose the `local` environment and run a
request. Besides one request per sample, it has requests that follow the last sent document through the
Camunda REST API: its current step, variables, child process, the routing decision, open user tasks,
and completing a task.

| Sample | What happens | Waits for |
|---|---|---|
| `invoice-small.json` | Invoice over 250 EUR, paid without approval | – |
| `invoice-medium.json` | Invoice over 5,400 EUR | Approval by `accounting` |
| `invoice-large.json` | Invoice over 24,990 EUR | Approval by `management` |
| `invoice-unknown-supplier.json` | Unknown supplier, invoice processing fails | "Handle failed document" |
| `complaint-letter.json` | Letter, classifier not confident, then manual triage | Manual classification, then triage |

### Request format

`POST /documents` with JSON; `channel` decides which start message is used:

| channel | Fields | Starts `document-intake` with message |
|---|---|---|
| `email` | `sender`, `subject`, `body` | `email-received` |
| `letter` | `sender`, `scannedText` | `letter-scanned` |

Responses: `202` with the process instance key, `400` for an unknown channel, `503` if
`document-intake` is not deployed (the document service isn't running).

What the simulated services recognise (keywords, invoice format, known suppliers) is described in
[architecture.md](../docs/architecture.md#simulated-behaviour).

## Watch and interact

| Where | What |
|---|---|
| The script's terminal | Every step of every document, one line per step, and a `RESULT` line at the end |
| **Tasklist**, http://localhost:8080/tasklist | Complete waiting tasks with their forms (login `demo` / `demo`) |
| **Operate**, http://localhost:8080/operate | Where each instance is, its variables, incidents and decisions. Click a business rule task, or open the *Decisions* tab, to see a decision's inputs and the matched rule |
| `c8ctl` | `c8ctl list pi --profile=local`, `c8ctl list ut --profile=local`, `c8ctl search inc --state=ACTIVE --profile=local` |

A medium invoice, for example, waits after "Supplier of … is Büro Schmidt KG" until you approve it in
Tasklist; then the payment and the `RESULT` line follow.

## When something doesn't work

| Symptom | Cause |
|---|---|
| `Port 8081 is in use` | The demo (or another app) is already running |
| `POST /documents` returns `503` | `document-intake` is not deployed: the document service isn't running |
| A step stays active for about 5 minutes after restarting the demo | A worker had fetched the job when it was stopped; the engine hands it out again after the job's lease expires ([details](../docs/pitfalls.md#a-step-hangs-for-5-minutes-after-a-restart)) |
| A service fails with `Connection refused` | The Camunda cluster isn't running: `c8ctl cluster start` |
