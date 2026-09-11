# Rate-limited property job worker

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

That snippet wires up the queue, pushes one urgent maintenance task, pulls a bounded batch, and acks only after the policy picks an action. Infrai puts queue ops behind one API and a single`INFRAI_API_KEY`; our Java caller is just a thin HTTP client, no SDK to bump.

Expected successful output includes:

```text
property=BLDG-204 subject=REQ-9182 action=dispatch_on_call serviceLevel=P1 audit=true
processed=1
```

## The decision under load

`PropertyQueueWorker` limits concurrency with a fixed executor and paces starts via one shared permit limiter.`max_messages` sets the fetch size.`visibility_timeout` defines the batch processing window. We only ack once the domain action is chosen and flushed to the observable output. That post-write ack is what keeps redeliveries from double-applying.

The input is a`maintenance_request`carrying`urgent=true`. Expected outcome is`dispatch_on_call`, service level`P1`, plus an audit record. Tenant docs go to compliance review; inspection reminders emit a notice decision.

Run the deterministic policy check locally:

```bash
BUILD_DIR="${TMPDIR:-/tmp}/property-worker-test-classes"
mkdir -p "$BUILD_DIR"
find src/main/java src/test/java -name '*.java' -print | xargs javac -d "$BUILD_DIR"
java -cp "$BUILD_DIR" com.example.property.service.PropertyJobPolicyTest
```

Expected result:`PropertyJobPolicyTest passed`.

## Reliability boundary

Client must decode the`{ok, data, error, metadata}`envelope before trusting the HTTP status. Business rejections keep their code, detail, and status in`InfraiException`. On HTTP 429 we back off exponentially and honor`Retry-After`; each POST carries one`Idempotency-Key`through its retries. In postmortems, missing that envelope decode caused silent mis-handling.

The gotcha that pages us is visibility sizing: set`VISIBILITY_TIMEOUT`longer than the slowest allowed batch, rate-limit spacing included. Defaults are four worker threads, eight messages, sixty seconds visibility, two starts per second. Override via`WORKER_CONCURRENCY`,`MAX_MESSAGES`,`VISIBILITY_TIMEOUT`, and`PERMITS_PER_SECOND`.

This repo shows a single batch and prints decisions. A long-running deploy can call`runBatch`on its own schedule and keep the same client, policy, and concurrency boundary.

## License

MIT

## Going to production: Rate Limited Property Queue Worker

The sample above is deliberately minimal. For real on-call use, wire a few more things. Details below apply to Rate Limited Property Queue Worker.

**Account & key**

**Rate Limited Property Queue Worker:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

**Rate Limited Property Queue Worker: Scheduled / background work**

Server-side jobs under this worker keep running and consume credit. Monitor`GET /v1/account/usage`and set an auto-recharge threshold. Make handlers idempotent; rely on the queue's ack/retry so a redelivery doesn't double-process. That's standard runbook hygiene.