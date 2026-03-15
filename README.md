# Mestri

[![Checks](https://github.com/gowda/mestri/actions/workflows/checks.yml/badge.svg)](https://github.com/gowda/mestri/actions/workflows/checks.yml)

Mestri is a Redis-backed background job system for Kotlin and Spring Boot with Sidekiq-style client/server middleware, worker architecture, and dashboard, plus JobRunr-compatible APIs.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the detailed architecture and delivery plan.

## Modules

- `mestri-core`: core job model, middleware contracts, worker engine, scheduling abstractions
- `mestri-redis`: Redis-backed storage, queues, promotion, leases, recovery
- `mestri-spring-boot-starter`: Spring Boot auto-configuration, health, dashboard embedding
- `mestri-dashboard`: dashboard API and UI
- `mestri-jobrunr-compat`: JobRunr-compatible facade for application migration

## Java Baseline

Mestri targets Java 17 to match the Spring Boot 3.3 applications in the current ecosystem.

## Spring Boot Support

Mestri is initially scoped for Spring Boot 3.3.x applications, matching the current `master-service` and dependent services.

## Requirements

- Java 17+
- Gradle 9.4.0 wrapper
- Redis

## Getting Started

Generate the Gradle wrapper if it is not already present:

```bash
gradle wrapper --gradle-version 9.4.0
```

List the modules in the project:

```bash
./gradlew projects
```

Build all modules:

```bash
./gradlew build
```

Run the test suite:

```bash
./gradlew test
```

Run formatting checks:

```bash
./gradlew ktlintCheck
```

Format the codebase:

```bash
./gradlew ktlintFormat
```

## Repository Layout

```text
mestri/
  mestri-core/
  mestri-redis/
  mestri-spring-boot-starter/
  mestri-dashboard/
  mestri-jobrunr-compat/
```

## Current Status

This repository currently contains:

- the multi-module Gradle/Kotlin scaffold
- the initial Spring Boot 3.3 / Java 17 baseline
- the architecture document in `ARCHITECTURE.md`
- completed work for core API and Redis foundation:
  - core contracts in `mestri-core`
  - Redis key schema and persistence primitives in `mestri-redis`
  - module-level tests for the implemented core and Redis layers
- real Redis-backed integration tests for:
  - immediate queue flows
  - scheduled and retry persistence
  - succeeded and deleted retention buckets
  - leases, heartbeats, and process registry behavior
  - worker execution flows for success, retry, dead-lettering, and shutdown
- completed work for the worker execution engine:
  - queue polling and reservation
  - server execution pipeline
  - success, retry, and dead-letter transitions
  - graceful worker shutdown coordination
- completed work for built-in orphan recovery and maintenance:
  - maintenance loop planning and execution
  - orphaned running-job recovery into `retry` or `queued`
  - stale worker and process registry cleanup
  - retention cleanup for `succeeded`, `deleted`, `retry`, and `dead`
- completed work for scheduling, recurring jobs, and deduplication:
  - scheduled and retry promotion into queue buckets
  - recurring job definitions with cron and timezone support
  - recurring scheduling and next-run calculation
  - deduplication for immediate and scheduled jobs
  - direct Redis-backed dedup acceptance, rejection, and expiry coverage
- GitHub Actions test automation
- Dependabot configuration for Gradle and GitHub Actions updates

Current implementation coverage includes:

- core domain model for jobs, invocation descriptors, retry and retention policies
- client and server middleware contracts
- worker lease, heartbeat, and maintenance contracts
- default serialization contracts and job payload model
- Redis primitives for:
  - job records
  - immediate queue enqueue
  - scheduled and retry persistence
  - scheduled and retry promotion
  - lease and heartbeat persistence
  - dead, succeeded, and deleted retention markers
  - recurring definition persistence
  - deduplication claims
- shared Testcontainers-based Redis integration test support for `mestri-redis`
- maintenance and recovery coverage for:
  - worker crash recovery
  - expired lease recovery
  - stale worker cleanup
  - retained-bucket trimming

The next major focus is the Spring Boot starter, health model, and JobRunr-compatible application facade.

## Automation

- GitHub Actions checks run:
  - `./gradlew --no-daemon ktlintCheck`
  - `./gradlew --no-daemon test`
  - `./gradlew --no-daemon :mestri-redis:integrationTest`
- Dependabot is configured for grouped updates for:
  - Gradle dependencies
  - GitHub Actions
