# Mestri

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
- Gradle 8.10.2 wrapper
- Redis

## Getting Started

Generate the Gradle wrapper if it is not already present:

```bash
gradle wrapper --gradle-version 8.10.2
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
  - lease and heartbeat persistence
  - succeeded and deleted retention markers

The next major focus is the worker execution engine, starting with queue polling, reservation, execution flow, and state transitions.

## Automation

- GitHub Actions runs `./gradlew test` on pushes to `main` and on pull requests
- Dependabot is configured for grouped updates for:
  - Gradle dependencies
  - GitHub Actions
