# Pitfalls

Things that tripped us up while building this demo, with the cause and the fix. Most of them are not
obvious from the documentation.

## Modeling

### Output mappings drop every other variable

**Symptom:** a variable is suddenly missing after a step, e.g. the email text after the start event,
or `triageOutcome` at the gateway after the triage task.

**Cause:** as soon as an element has *any* output mapping, only the mapped variables are passed on.
We added `channel` to the message start events, and `subject`, `body` and `scannedText` silently
disappeared.

**Fix:** map every variable you need explicitly. Bonus: the mapping documents the element's contract
(see the start events, "Triage document" and "Handle failed document").

### Error end events never complete

**Symptom:** a test asserts that an error end event *completed* and fails with `terminated`; the
coverage report shows the event as not covered although the path ran.

**Cause:** throwing the error terminates the scope, including the end event itself.

**Fix:** assert that the catching **boundary event** completed instead. Accept that these events stay
uncovered in the coverage report.

### Exclusive gateway without a default flow

If no condition is true and there is no default flow, the instance gets an incident. Also guard
conditions against missing variables: `=confidence != null and confidence >= 0.85`.

### Variables of a child process don't come back

A call activity with `propagateAllChildVariables="false"` returns nothing unless you map it. We use
exactly one output mapping, `outcome`, as the contract.

## Running

### A step hangs for 5 minutes after a restart

**Symptom:** after restarting the services, one instance stays at a service task for about 5 minutes,
then continues on its own.

**Cause:** a worker had activated the job (taken its lease) and was stopped before completing it. The
engine only hands the job out again when the lease (job timeout, 5 minutes by default) expires. That's
the at-least-once guarantee working, just slowly.

**Fix:** nothing to fix for the demo. In production, choose job timeouts that match the work, and keep
workers idempotent.

### Process not deployed yet

The domain services deploy their processes on startup. If the input service runs without the
document service, `POST /documents` returns `503`; if the invoice service is missing, an invoice gets
an incident at "Process document". `demo/run-demo.sh` starts the services in the right order.

### Same correlation key twice

Camunda starts at most one active instance per message name and correlation key; a second message
with the same key starts nothing. The input service generates a unique key per document.

### Trailing zeros disappear from amounts

Variables are JSON, so `1250.00` arrives as `1250.0`. Format for display in the worker
(`InvoiceWorkers.inEuros`).

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
other containers (SonarQube, databases) running next to it.

**Fix:** stop the other containers, or `colima start --cpu 4 --memory 8`.

### The first test times out

**Symptom:** `SocketTimeoutException: 10000 MILLISECONDS` before the first scenario.

**Cause:** the engine reset before the first test takes longer than the client's default 10 s on a
freshly started container.

**Fix:** `camunda.client.request-timeout=PT30S` in the `ProcessTest` properties (already set).

### Harmless warnings in the test log

`No license detected` (the test engine runs without a license) and
`'amount' should not be negative` (internal engine metrics after the test clock was moved) can be
ignored. The second one has nothing to do with invoice amounts.

## Workers

### Business outcome or technical failure?

An unreadable invoice is an expected outcome, not a crash: throw a **BPMN error**
(`CamundaError.bpmnError`), so the process takes the modeled error path without retries. Use a job
failure (`CamundaError.jobError`) for technical problems that might go away on retry. Don't let
exceptions escape on purpose: the engine retries them immediately with zero back-off.

### Logging needs `spring-boot-starter`

The Camunda Spring Boot starter doesn't bring a logging backend. Without `spring-boot-starter`, the
services start but log nothing (`No SLF4J providers were found`).
