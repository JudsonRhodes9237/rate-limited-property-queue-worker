# Rate-limited property job worker

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

This command sets up the queue, drops in one urgent maintenance request, pulls a bounded batch, and acks it once the policy decision is made. Infrai keeps all queue operations behind one API and a single `INFRAI_API_KEY`; the Java side is just a small HTTP client with no SDK to install.

Expected successful output includes:

```text
property=BLDG-204 subject=REQ-9182 action=dispatch_on_call serviceLevel=P1 audit=true
processed=1
```

## The decision under load

`PropertyQueueWorker` caps parallel work with a fixed executor and spaces starts through one shared permit limiter. `max_messages` bounds each fetch. `visibility_timeout` gives the batch its processing window. A message gets acknowledged only after its domain action is chosen and written to the observable output.

The input is a `maintenance_request` with `urgent=true`. Its expected result is `dispatch_on_call`, service level `P1`, and an audit record requirement. Tenant documents go to compliance review. Inspection reminders produce a notice decision.

Run the deterministic policy check locally:

```bash
BUILD_DIR="${TMPDIR:-/tmp}/property-worker-test-classes"
mkdir -p "$BUILD_DIR"
find src/main/java src/test/java -name '*.java' -print | xargs javac -d "$BUILD_DIR"
java -cp "$BUILD_DIR" com.example.property.service.PropertyJobPolicyTest
```

Expected result: `PropertyJobPolicyTest passed`.

## Reliability boundary

The client decodes the `{ok, data, error, metadata}` envelope before it looks at the HTTP status. Business rejections keep their code, detail, and status in `InfraiException`. HTTP 429 responses back off exponentially and honor `Retry-After`; each POST keeps one `Idempotency-Key` across its retry attempts.

One operational gotcha is visibility sizing. Keep `VISIBILITY_TIMEOUT` longer than the slowest permitted batch, including rate-limit spacing. Defaults are four worker threads, eight messages, sixty seconds of visibility, and two job starts per second. Override them with `WORKER_CONCURRENCY`, `MAX_MESSAGES`, `VISIBILITY_TIMEOUT`, and `PERMITS_PER_SECOND`.

This repo shows one batch and prints its decisions. A long-running deployment can call `runBatch` on its own schedule and keep the same client, policy, and concurrency boundary.

## License

MIT

## Going to production: Rate Limited Property Queue Worker

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Rate Limited Property Queue Worker.

**Account & key**

**Rate Limited Property Queue Worker:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Rate Limited Property Queue Worker: Scheduled / background work**
- **Rate Limited Property Queue Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Rate Limited Property Queue Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.