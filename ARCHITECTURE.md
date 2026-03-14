# Mestri Architecture

## Overview

Mestri is a Redis-backed background job system for Kotlin with first-class Spring Boot 3.3 support and standalone JVM support. It is JobRunr-compatible at the application API layer, while its runtime architecture is intentionally Sidekiq-inspired: Redis-native buckets, client and server middleware chains, worker heartbeats, lease-based execution, retry handling, recurring jobs, and an operational dashboard.

The design goal is to preserve the ergonomics used in the existing applications while moving to a more Redis-native, operationally robust runtime.

## Goals

- Support the common JobRunr-facing APIs currently used in the ecosystem:
  - `JobScheduler.enqueue`
  - `JobScheduler.schedule`
  - `BackgroundJob.enqueue`
  - `@Job`
  - `JobId`
  - `JobContext`
- Use Redis as the primary and only storage backend for v1.
- Adopt Sidekiq-style architecture internally:
  - client middleware before enqueue
  - server middleware around execution
  - queue, scheduled, retry, dead, succeeded, and deleted buckets
  - worker/process heartbeats and lease recovery
- Support both Spring Boot 3.3 and standalone Java/Kotlin applications.
- Include a built-in operational dashboard.
- Include first-class health reporting.
- Build orphan recovery and maintenance into the runtime rather than requiring application jobs.

## Non-Goals

- Full compatibility with JobRunr storage and admin APIs.
- Binary compatibility with all JobRunr extension points.
- Multi-backend storage in v1.
- A relational-state abstraction over Redis.

## Platform Baseline

- Java 17 minimum
- Spring Boot 3.3.x support baseline

The initial build and starter are aligned with Spring Boot 3.3.x because the existing `master-service`, `fahras-service`, `orders-service`, `promotions-service`, and `sync-service` ecosystem already targets that generation.

## Runtime Model

Mestri uses Redis-native operational buckets rather than a single persisted state machine table.

### Primary Buckets

- `queue:<name>`: ready jobs waiting for workers
- `scheduled`: delayed jobs waiting for promotion
- `retry`: failed jobs scheduled for another attempt
- `dead`: exhausted jobs retained for inspection
- `succeeded`: completed jobs retained for audit/debugging
- `deleted`: deleted/cancelled jobs retained for audit/debugging
- `running`: represented through active leases and worker/job metadata
- `recurring`: recurring job definitions and next-run scheduling metadata

### Job Record

Each job is stored as a Redis hash containing:

- job id
- current bucket
- serialized job payload
- payload format
- created timestamp
- updated timestamp

The default job payload contains:

- job id
- invocation descriptor
- queue
- retry policy
- labels
- metadata
- deduplication key

This keeps the Redis job record flat and easy to query while allowing payload evolution through a versioned mapper.

## Invocation Model

Jobs are not persisted as opaque lambdas. Mestri stores an invocation descriptor:

- target class or handler id
- Spring bean name when applicable
- method name
- parameter type names
- serialized arguments
- queue and labels
- retry configuration
- deduplication information

### Execution Modes

- Spring Boot mode:
  - resolve target beans from the application context
  - invoke via reflection or cached method handles
- Standalone mode:
  - invoke handlers from an explicit registry

This enables JobRunr-style API compatibility without inheriting JobRunr internals.

## Middleware Architecture

Mestri includes separate client and server middleware chains, following the Sidekiq model.

### Client Middleware

Client middleware runs before a job is written to Redis.

Responsibilities may include:

- setting queue names
- propagating tracing or correlation ids
- injecting tenant/user metadata
- setting or normalizing retry policies
- deduplication
- client-side metrics
- policy-based rejection of enqueue attempts

### Server Middleware

Server middleware wraps execution after a worker reserves a job.

Responsibilities may include:

- logging and MDC context
- tracing spans
- metrics and timing
- rate limiting
- timeout enforcement
- exception normalization
- retry/dead-letter instrumentation

### Middleware Contracts

The core library will define:

- `ClientMiddleware`
- `ClientChain`
- `ServerMiddleware`
- `ServerChain`

Spring Boot integration will auto-discover and order middleware beans; standalone mode will allow manual registration.

## Worker and Lease Model

Workers poll named queues and reserve jobs atomically. Once reserved:

- the job is marked as running
- a lease is created with an expiry
- the worker heartbeats periodically
- server middleware wraps handler execution

### Delivery Semantics

Mestri provides at-least-once delivery semantics.

Applications should treat job handlers as idempotent where practical.

### Orphan Recovery

Orphan recovery is built into Mestri and replaces app-specific maintenance jobs.

The maintenance loop:

- finds expired running job leases
- verifies worker heartbeat status
- requeues or retries orphaned jobs based on attempt policy
- evicts stale workers/process entries

## Scheduling and Recurring Jobs

Delayed and retryable jobs live in sorted sets scored by execution timestamp.

A promotion loop:

- reads due jobs from `scheduled` and `retry`
- moves them atomically into target queues
- preserves metadata and ordering guarantees as much as possible within Redis semantics

Recurring jobs are stored separately with:

- definition id
- cron expression
- timezone
- queue
- invocation descriptor
- next run timestamp

## Redis Primitive Mapping

The current Redis data model is intentionally explicit about primitive usage:

- Redis hashes:
  - job records
  - worker heartbeats
  - job leases
- Redis lists:
  - immediate queue buckets
- Redis sorted sets:
  - `scheduled`
  - `retry`
  - `dead`
  - `succeeded`
  - `deleted`
- Redis sets:
  - process registry

This keeps the system close to Redis semantics instead of hiding storage behind a relational-style state layer.

## Redis Test Strategy

Mestri uses two layers of Redis validation:

- fast in-memory persistence tests for isolated mapping and transition behavior
- real Redis integration tests backed by Testcontainers for queue, sorted-set, lease, heartbeat, retention-marker, worker execution, and maintenance behavior

The real Redis suite lives in `mestri-redis` and is intended to run in CI as part of normal validation.

## Running State

`running` is a logical runtime bucket, not a primary persisted Redis collection.

It is derived from:

- job leases
- worker heartbeat hashes
- process registry membership
- per-process or per-worker in-flight work metadata where needed

This is intentionally closer to Sidekiq’s runtime model than a standalone `running` list or sorted set.

## Deduplication

Mestri will provide optional deduplication support.

Deduplication can be driven by:

- explicit deduplication key
- middleware-derived deduplication strategy
- configurable default policies for scheduled or immediate jobs

This replaces brittle application-level cleanup logic based on job-name scanning.

## Retention Model

Mestri retains `succeeded` and `deleted` buckets with configurable retention.

### Retention Rules

- bucket-specific retention can be set for `succeeded`
- bucket-specific retention can be set for `deleted`
- bucket-specific retention can be set for `retry`
- bucket-specific retention can be set for `dead`
- a global fallback retention applies when bucket-specific settings are absent

Cleanup runs in the runtime maintenance loop.

## Health Model

Mestri exposes health as a first-class runtime feature.

### Spring Boot

- `MestriHealthIndicator` integrates with Actuator

### Standalone

- `MestriHealthService` returns a structured health snapshot

### Health Signals

- Redis connectivity
- active worker/process count
- stale running jobs
- queue depths
- retry/dead counts
- oldest queued job age
- scheduler/promoter liveness

### Status Semantics

- `UP`: Redis reachable and healthy workers available when enabled
- `DOWN`: Redis unavailable or no healthy workers while processing is enabled
- `DEGRADED`: system reachable but thresholds exceeded

## Dashboard

The dashboard is backed by Mestri-native APIs and data structures.

### Initial Pages

- overview
- queues
- running jobs and workers
- scheduled jobs
- retry jobs
- dead jobs
- succeeded jobs
- deleted jobs
- recurring jobs
- job details

### Initial Actions

- retry
- requeue
- delete
- inspect metadata and failure details

## Configuration

Mestri uses its own property namespace.

### Core Properties

- `mestri.enabled`
- `mestri.redis.host`
- `mestri.redis.port`
- `mestri.redis.database`
- `mestri.worker.enabled`
- `mestri.worker.concurrency`
- `mestri.worker.shutdown-timeout`
- `mestri.scheduler.enabled`
- `mestri.dashboard.enabled`
- `mestri.dashboard.username`
- `mestri.dashboard.password`
- `mestri.health.enabled`
- `mestri.dedup.enabled`

### Retention Properties

- `mestri.retention.global`
- `mestri.retention.succeeded`
- `mestri.retention.deleted`
- `mestri.retention.dead`
- `mestri.retention.retry`

### Retry Properties

- `mestri.retries.default-attempts`
- `mestri.retries.backoff`

### Migration Aid

For transition periods, the Spring Boot starter may optionally read legacy `org.jobrunr.*` and `JOBS_*` values as deprecated aliases, while documenting `mestri.*` as canonical.

## Compatibility Surface

### Supported in V1

- `JobScheduler.enqueue`
- `JobScheduler.schedule`
- `BackgroundJob.enqueue`
- `@Job(name = ..., retries = ...)`
- `JobId`
- `JobContext`
- Spring bean method invocation
- typed enqueue patterns for job handler beans

### Not Supported in V1

- `StorageProvider`
- mutable Job admin model
- JobRunr paging/state browsing APIs

## Module Layout

- `mestri-core`
  - job model
  - middleware contracts
  - worker engine
  - scheduling abstractions
  - health contracts
- `mestri-redis`
  - Redis key schema
  - payload serialization
  - hash/list/set/sorted-set access abstractions
  - job record, queue, schedule, lease, heartbeat, and retention persistence primitives
  - real Redis integration tests
  - maintenance, recovery, and retention cleanup
- `mestri-spring-boot-starter`
  - auto-configuration
  - Spring integration
  - actuator support
  - property binding
  - embedded dashboard wiring
- `mestri-dashboard`
  - dashboard HTTP API
  - UI assets
- `mestri-jobrunr-compat`
  - facade for the supported JobRunr-style APIs

## Delivery Phases

### Phase 0: Spec Lock

- finalize supported compatibility surface
- finalize Redis key schema
- finalize middleware contracts

### Phase 1: Core Runtime

- queueing model
- job record
- client/server middleware
- worker execution
- retries
- leases and heartbeats

### Phase 2: Redis Backend

- enqueue and reserve primitives
- scheduled/retry promotion
- maintenance loop
- orphan recovery
- retention cleanup

### Phase 3: Spring Boot Integration

- configuration properties
- bean invocation
- lifecycle management
- health indicator

### Phase 4: Dashboard

- operational views
- worker/process visibility
- admin actions

### Phase 5: JobRunr Compatibility

- scheduler facade
- annotation support
- context support
- migration verification against existing apps

### Phase 6: Hardening

- crash recovery testing
- performance testing
- observability polish
- migration documentation
