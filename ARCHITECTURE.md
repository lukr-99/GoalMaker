# GoalMaker architecture

## Context

Describe the system, users, important constraints, and source of truth.

## Modules and dependencies

```text
UI / host ---> application ---> domain
     |              ^
     v              |
 adapters ----------+
```

List each real module, its responsibility, interface, and allowed dependencies. Identify the
composition root.

## Data flow

Describe reads, writes, background work, sync, backup, import/export, and state ownership.

## Capability modules

List plugin-like capabilities, their stable IDs, injected interfaces, adapters, configuration, and
health/state reporting.

## Connections

Describe discovery, trust/authentication, protocol, connection state machine, retry/backoff,
cancellation, timeouts, and manual fallback.

## Delivery

Describe packaging, signing, installer/store, update source, artifact verification, upgrade data
safety, and recovery.

## Known constraints

Record operational constraints that code alone does not reveal. Put hard-to-reverse surprising
trade-offs in numbered ADRs.

