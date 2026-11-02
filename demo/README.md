# Run the demo

Everything you need to try the demo by hand: a start script, Kafka in Docker, a script to send
documents, and sample documents. All commands run from the **repository root**.

## Start

```bash
demo/run-demo.sh                # Camunda (if needed), Kafka + Kafka UI, build, document-service
demo/run-demo.sh --no-build     # skip the build
demo/run-demo.sh --verbose      # show every log line, not only the demo's own output
```

The script

1. starts the local Camunda cluster with `c8ctl cluster start` if it isn't running, and waits for its
   **connectors runtime** (http://localhost:8086), which runs the Kafka connectors of the process;
2. starts **Kafka** (`localhost:9092`) and **Kafka UI** (http://localhost:8090) from
   [`docker-compose.yml`](docker-compose.yml) and creates the topics `documents-in` and `documents-out`;
3. builds and starts `document-service`, which deploys `document-routing` and runs the
   `classify-document` worker, and shows its log. The full log is in `demo/logs/document-service.log`.

**Ctrl+C** stops `document-service`. The Camunda cluster and the Kafka containers keep running; stop
them with `c8ctl cluster stop` and `docker compose -f demo/docker-compose.yml down` (or
`docker-compose …` if you use the standalone Compose).

## Send documents

```bash
demo/send-document.sh demo/samples/invoice.json
demo/send-document.sh demo/samples/complaint.json
```

The script puts the file as one message on `documents-in`, using the Kafka CLI inside the `kafka`
container (no local Kafka tools needed). Alternatively, in Kafka UI: *Topics → documents-in →
Produce Message*, value = the JSON of a sample.

| Sample | Classified as | Decision (rule) | Path |
|---|---|---|---|
| `urgent-invoice.json` | `invoice`, urgent ("DRINGEND", "Rechnung") | `accounting`, `high` (1) | B: Forward to accounting |
| `invoice.json` | `invoice` ("Invoice") | `accounting`, `normal` (2) | B: Forward to accounting |
| `complaint.json` | `complaint` ("Beschwerde") | `customer-service`, `normal` (4) | D: Forward to customer service |
| `address-change.json` | `other` | `general-inbox`, `normal` (5) | C: Forward to general inbox |

Your own documents only need the three fields:

```json
{ "filename": "letter-42.txt", "headline": "Rechnung März", "content": "…" }
```

Keywords in headline or content (any case): "invoice" / "Rechnung" → `invoice`, "complaint" /
"Beschwerde" / "Reklamation" → `complaint`, anything else → `other`; "urgent" / "dringend" → urgent.
Where each document goes and with which priority is decided by the table `routing-decision.dmn`
([rules](../docs/architecture.md#decision-routingdecision)). Try an urgent complaint: put "dringend" and
"Beschwerde" in the headline.

## Watch

| Where | What |
|---|---|
| The script's terminal | One line per classified document: `Classified invoice-acme.txt as invoice` |
| **Operate**, http://localhost:8080/operate | Every instance of `document-routing`, the path it took, its variables (login `demo` / `demo`). Click "Determine route", or open the *Decisions* tab, to see which rule of the table matched. Tick *Finished Instances* in the filter: instances complete in under a second |
| **Kafka UI**, http://localhost:8090 | *Topics → documents-out → Messages*: one result per document, key = filename |
| `c8ctl` | `c8ctl list pi --profile=local`, `c8ctl search inc --state=ACTIVE --profile=local` |

Result message, e.g.:

```json
{ "filename": "invoice-acme-reminder.txt", "headline": "DRINGEND: Rechnung 4711, zweite Mahnung",
  "category": "invoice", "routedTo": "accounting", "priority": "high" }
```

Things to try:

- **Stop `document-service`** (Ctrl+C) and send a document. The instance still starts (the connectors
  runtime reads Kafka, not the service) and waits at "Classify document" in Operate. Start the demo
  again with `--no-build`: the instance continues.
- **Send the same file twice:** two instances, two results. Every Kafka message is its own document.
- **Send something that isn't JSON** (Kafka UI): the instance starts with empty fields and goes to C
  with normal priority.
  A JSON array, on the other hand, ends in an incident at "Classify document"
  ([why](../docs/pitfalls.md#messages-that-arent-json-objects)).

## When something doesn't work

| Symptom | Cause |
|---|---|
| `Docker is not running` / `Docker Compose not found` | Start Docker Desktop or Colima; install Compose (`brew install docker-compose`) |
| `Connectors runtime not reachable` | The cluster runs without connectors; restart it: `c8ctl cluster stop && c8ctl cluster start` |
| A document is sent, but no instance appears | `document-routing` isn't deployed yet (start the demo once), or the connectors runtime can't reach Kafka: see [pitfalls](../docs/pitfalls.md#the-connectors-runtime-cant-reach-kafka) |
| An instance waits at "Classify document" | `document-service` isn't running |
| An instance has an incident at B or C | Kafka was down when the result was published; start it and retry the incident in Operate |
| A step stays active for about 5 minutes after restarting the demo | The worker had fetched the job when it was stopped ([details](../docs/pitfalls.md#a-step-hangs-for-5-minutes-after-a-restart)) |
