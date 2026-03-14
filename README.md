# Mestri

Mestri is a Redis-backed background job system for Kotlin and Spring Boot with Sidekiq-style client/server middleware, worker architecture, and dashboard, plus JobRunr-compatible APIs.

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
