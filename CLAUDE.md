# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Start required dependency (Redis) before running the app
docker compose up -d

# Run the application
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Build without tests
./mvnw package -DskipTests

# Full build + test
./mvnw verify
```

H2 console available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:javahexagonal`, user: `sa`, no password).

## Architecture

This is a **Hexagonal Architecture (Ports & Adapters)** Spring Boot 3.5.6 / Java 21 project. The four packages map directly to architectural layers with a strict inward-only dependency rule:

```
interfaces/          ← Inbound adapters (REST controllers)
infrastructure/      ← Outbound adapters (Spring config, security, AOP, filters)
application/         ← Use cases (services, DTOs, converters, utilities)
domain/              ← Core (entities, repository port interfaces only)
```

**Dependency rule:** `interfaces` and `infrastructure` depend on `application`; `application` depends on `domain`; `domain` has no outward dependencies.

### Ports & Adapters

- **Outbound port:** `domain/repo/UserRepo.java` — interface only; Spring Data JPA implements it in `infrastructure/`
- **Inbound port:** `application/service/UserService.java` — interface; `application/service/impl/UserServiceImpl.java` is the implementation
- **Inbound adapter:** Controllers in `interfaces/` call the application service interfaces
- **Domain entities** extend `AbstractModel` (JPA auditing fields: createdAt, updatedAt, createdBy, modifiedBy)

### Dual-Cache (L1 + L2)

`DualCacheService` / `DualCacheServiceImpl` combines **Caffeine** (local JVM cache, L1) and **Redis** (distributed cache, L2). Cache versioning and invalidation are managed via two custom AOP annotations:

- `@VersionCache` — reads from cache with version-key check (handled by `VersionCacheAspect`)
- `@BumpVersion` — invalidates cache by incrementing the version key (handled by `VersionLocalCache`)

### Cross-Cutting Concerns (AOP)

- `@LogsActivity` + `LogsActivityHandler` — declarative activity logging
- `@EnumValid` + `EnumValidator` — Bean Validation constraint for enum fields

### API Response Shape

All endpoints use a standard envelope:
- `BaseResponse<T>` — single-item responses
- `BasePageResponse<T>` — paginated responses with a `Meta` object

### Security

JWT-based auth via `jjwt` 0.11.5. `JwtUtils` handles token creation/validation. `SecurityConfig` wires the Spring Security filter chain. `AuditorAwareImpl` feeds Spring Data auditing from the current security context. `RequestIdFilter` injects `X-Request-ID` correlation IDs into every request.

### i18n Error Messages

Error messages are externalized to `errors.properties` (English) and `errors_vi.properties` (Vietnamese). `GlobalExceptionHandler` + `ExceptionResolve` translate all exceptions to HTTP responses using these bundles. `BusinessError` is the domain exception type.

### Runtime Configuration (`application.yml`)

| Key | Default |
|---|---|
| Server port | `8080` |
| Database | H2 in-memory (`jdbc:h2:mem:javahexagonal`), `ddl-auto: create-drop` |
| Redis | `localhost:6379` |
| JWT access token TTL | 15 minutes |
| JWT refresh token TTL | 30 days |
| Async thread pool | core=5, max=10, queue=10000 |

MySQL config is present but commented out in `application.yml` — swap datasource and dialect to switch databases.
