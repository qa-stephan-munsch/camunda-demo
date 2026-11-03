# Pitfalls

Things that tripped us up while building this demo, with the cause and the fix. Most of them are not
obvious from the documentation.

## Kafka and connectors

### The connectors runtime can't reach Kafka

**Symptom:** documents are sent, but no instance starts; the connectors log
(`~/Library/Caches/c8run/c8run-*/log/connectors.log`) shows connection errors to `kafka:29092` or
similar.

**Cause:** the connectors runtime of c8run runs on the host, not in Docker. Kafka gives its clients
the *advertised* address, and a container-internal name like `kafka:29092` doesn't resolve on the host.

**Fix:** two listeners in `demo/docker-compose.yml`: `localhost:9092` for the host (connectors
runtime, `send-document.sh`) and `kafka:29092` for containers (Kafka UI).

### Documents sent before the first deployment

The Kafka consumer only exists once `document-routing` is deployed. The start event uses a fixed
consumer group (`document-routing`) and `autoOffsetReset = earliest`, so on its very first start it
reads `documents-in` from the beginning and nothing sent earlier is lost. With the template's default
`latest`, those documents would be skipped. Once the group has committed offsets, the setting no longer
matters: the consumer continues where it stopped.

### Messages that aren't JSON objects

The Kafka inbound connector logs `Cannot parse value to json object -> use the raw value` and starts
the instance anyway. `value.filename` etc. are then `null`: the document is classified as `other` and
goes to C, the result message has `null` fields and a `null` key.

Valid JSON of the wrong shape is different. For an array, `value.filename` gives a list
(`[1,2].filename` is `[null, null]` in FEEL); for `{"headline": {"de": "Rechnung"}}`, `headline` is an
object. The worker expects text, the job fails, and the instance gets an **incident** at "Classify
document". Cancel it in Operate.

That's acceptable for a demo; a real process would check the input (in the start event's result
expression or in a first step) and route invalid documents somewhere visible.

### Same document twice

Every Kafka record starts its own instance, even with identical content: the start event has no
`messageIdExpression`, so there is no deduplication across records. If you need "this file only once",
set a message ID from the content (e.g. `=value.filename`) and keep in mind it only deduplicates while
an earlier message with that ID is still buffered.

### Docker Compose isn't `docker compose`

With Colima and Homebrew, Compose is the standalone `docker-compose` binary; `docker compose` fails
with `unknown shorthand flag: 'f'`. `demo/run-demo.sh` uses whichever exists.

## Modeling

### Exclusive gateway without a default flow

If no condition is true and there is no default flow, the instance gets an incident. "Which route?" has
conditions for B and D and the default flow for C, so an unexpected route still goes somewhere.

### A decision table without a catch-all

If no rule matches, the decision result is `null`: here `routing` would be `null`, the gateway's
default flow would still send the document to C, but the result message would say
`"routedTo": null, "priority": null`.
`routing-decision.dmn` therefore ends with a `-` rule that matches anything, including a missing
`category`. With hit policy FIRST, new rules go above it, and the more specific row of a category
(`urgent = true`) above the general one (`-`): the other way round, the general row would always win.

## Running

### A step hangs for 5 minutes after a restart

**Symptom:** after restarting `document-service`, one instance stays at "Classify document" for about
5 minutes, then continues on its own.

**Cause:** the worker had activated the job (taken its lease) and was stopped before completing it.
The engine only hands the job out again when the lease (job timeout, 5 minutes by default) expires.
That's the at-least-once guarantee working, just slowly.

**Fix:** nothing to fix for the demo. In production, choose job timeouts that match the work, and keep
workers idempotent.

### Process not deployed yet

`document-service` deploys `document-routing` on startup. Until then the connectors runtime has no
inbound configuration and doesn't read `documents-in` at all. Start the demo once; afterwards the
model stays deployed even while the service is stopped.

## Testing

### Testcontainers doesn't find Colima

**Symptom:** `Could not find a valid Docker environment`, although `docker ps` works.

**Cause:** `docker` uses Colima's Docker context; Testcontainers ignores contexts and looks for
`/var/run/docker.sock`.

**Fix:** set `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`
([development.md](development.md#setup)).

### The engine container doesn't become healthy

**Symptom:** `Container startup failed for image camunda/camunda`, logs show
`Partition-default-1 failed … Services not installed`.

**Cause:** not enough CPU or memory in the Docker VM, e.g. Colima's default of 2 CPUs / 2 GiB with
other containers (Kafka, databases) running next to it.

**Fix:** stop the other containers, or `colima start --cpu 4 --memory 8`.

### Kafka stops while the tests run

**Symptom:** after `./mvnw verify`, the `kafka` container has exited with code 137;
`send-document.sh` says `container … is not running`.

**Cause:** the same memory limit. When the test engine container starts next to Kafka, the Docker
VM runs out of memory and the kernel kills a process (Colima: `colima ssh -- sudo dmesg | grep -i oom`).

**Fix:** `docker start kafka` (or run the demo again), or give the VM more memory as above.

### The first test times out

**Symptom:** `SocketTimeoutException: 10000 MILLISECONDS` before the first scenario.

**Cause:** the engine reset before the first test takes longer than the client's default 10 s on a
freshly started container.

**Fix:** `camunda.client.request-timeout=PT30S` in the `ProcessTest` properties (already set).

### Deleted scenarios still run

**Symptom:** after deleting or renaming a scenario file, `ProcessTest` still runs the old scenarios
and fails.

**Cause:** the old files are still in `target/test-classes`.

**Fix:** `./mvnw clean verify`.

### Harmless warnings in the test log

`No license detected` (the test engine runs without a license) can be ignored.

## Workers

### Business outcome or technical failure?

If a step can end in an expected business outcome (e.g. "document unreadable"), throw a **BPMN error**
(`CamundaError.bpmnError`) and model the path for it; there are no retries. Use a job failure
(`CamundaError.jobError`) for technical problems that might go away on retry. Don't let exceptions
escape on purpose: the engine retries them immediately with zero back-off. The demo's single worker
has neither case: a document without fields is simply `other`.

### Logging needs `spring-boot-starter`

The Camunda Spring Boot starter doesn't bring a logging backend. Without `spring-boot-starter`, the
service starts but logs nothing (`No SLF4J providers were found`).
