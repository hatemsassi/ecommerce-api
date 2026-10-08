# Production readiness checklist

Categories, in the order to work through them. Each item says what to look for, how to verify, and common false positives. Suggested severities are defaults — adjust to actual impact.

## Contents
1. Configuration & profiles
2. Secrets
3. Security & exposure
4. Transactions
5. Query performance (N+1, indexes)
6. API boundary (DTOs, validation)
7. Error handling & logging
8. Operations
9. Tests & build

---

## 1. Configuration & profiles

| Check | Default severity |
|---|---|
| A production profile exists (`application-prod.*`) or the deployment clearly supplies config via env vars | Blocker if dev-only settings below are unconditional |
| `spring.jpa.show-sql=true`, `hibernate.format_sql`, `logging.level.org.hibernate.SQL=DEBUG` active in prod | Warning (performance, log volume) |
| `logging.level.org.hibernate.orm.jdbc.bind=TRACE` (or `org.hibernate.type.descriptor.sql=TRACE`) active in prod | Blocker — logs every bound parameter value, i.e. customer emails, addresses, payment data |
| `spring-boot-devtools` not excluded from the packaged jar | Info — Boot excludes devtools from repackaged jars by default; only flag if `excludeDevtools=false` or it's on the compile classpath without `optional`/`runtime` scope |
| `spring.jpa.hibernate.ddl-auto` is `validate` or `none` | Blocker if `update`/`create`/`create-drop` in prod |
| `spring.jpa.open-in-view=false` | Warning if true/unset (holds DB connections during view rendering, hides lazy-loading problems) |
| H2 or other test DB on the runtime classpath | Warning if scope isn't `test` |

**Verify:** check whether each setting is overridden in another profile file or by an env-var placeholder before flagging it.

## 2. Secrets

| Check | Default severity |
|---|---|
| Credentials, API keys, JWT secrets hard-coded in properties or Java | Blocker |
| Placeholder with a real-looking default, e.g. `${DB_PASSWORD:admin}` | Blocker — if the env var is ever missing, prod silently connects with the default instead of failing fast. Fix: remove the default (`${DB_PASSWORD}`) so startup fails |
| Secrets committed in git history (`git log -p -S password -- '*.properties'`) | Warning; mention rotation |

**False positive:** test-only credentials in `src/test/resources` are fine.

## 3. Security & exposure

| Check | Default severity |
|---|---|
| `spring-boot-starter-security` (or another auth layer) present, with authentication on mutating endpoints | Blocker if absent on an API that creates orders, changes order/payment status, or deletes data |
| Authorisation: customers can only read/modify their own resources (e.g. orders filtered by `customerId` request param is not authorisation) | Blocker when authentication exists but ownership isn't enforced |
| An `AccessDeniedException` handler exists but nothing throws it | Info — signals planned-but-missing authorisation |
| Swagger UI / `/v3/api-docs` enabled in prod (`springdoc.swagger-ui.enabled`, `springdoc.api-docs.enabled`) and `try-it-out-enabled=true` | Warning — disable or protect in the prod profile |
| Actuator exposure: `management.endpoints.web.exposure.include=*` or sensitive endpoints (`env`, `heapdump`, `configprops`, `loggers`) exposed without auth | Blocker if exposed unauthenticated; Info if only `health`/`info` (the Boot default) |
| CORS: `allowedOrigins("*")` together with credentials, or wide-open CORS on a non-public API | Warning |
| Client-controlled state changes that should be server-driven, e.g. a public endpoint letting the caller set payment status to `COMPLETED` | Blocker — flag the business risk even if the code is otherwise correct |

## 4. Transactions

Services here use class-level `@Transactional(readOnly = true)` with method-level `@Transactional` on writes. That pattern is good, but it has a sharp edge: a write method **without** its own `@Transactional` runs read-only, and with Hibernate a read-only session skips dirty checking — changes to managed entities are silently not flushed. No exception, just lost writes.

| Check | Default severity |
|---|---|
| Public service method that calls `save*`/`delete*`/a `@Modifying` query, or mutates a managed entity via setters, without method-level `@Transactional` under a read-only class default | Blocker |
| Same, in a class with no class-level `@Transactional` (each repository call commits separately; multi-step writes aren't atomic) | Warning/Blocker depending on whether partial writes corrupt data |
| `@Modifying` queries invoked outside a transaction | Blocker (throws `TransactionRequiredException`) |
| `@Lock(PESSIMISTIC_WRITE)` queries invoked outside a transaction | Blocker (lock released immediately — no protection) |
| Self-invocation: a `@Transactional` method called from another method in the same class (proxy bypassed) | Warning |

**False positives:** package-private/private helpers called only from transactional methods don't need the annotation; read methods correctly inherit the read-only default.

## 5. Query performance

### N+1
Look at every list/page endpoint: trace repository finder → service → `XxxResponse.from(...)` and note every association the mapper touches.

| Check | Default severity |
|---|---|
| Mapper touches a lazy `@ManyToOne`/`@OneToMany` property (other than `getId()`, which doesn't initialise a proxy) on a paged/list query with no `@EntityGraph`/`JOIN FETCH` | Warning; downgrade to Info if `hibernate.default_batch_fetch_size` is set (turns N+1 into ~N/batch) |
| Inverse-side `@OneToOne(mappedBy = ...)` on a list query | Warning — can't be lazily proxied, so Hibernate issues one select per row regardless of fetch type; batch fetching may not cover it. Fix: add it to the entity graph |
| Repository call inside a loop | Warning, unless it's deliberate (e.g. per-row `SELECT ... FOR UPDATE` in id order to avoid deadlocks — mention as Info) |
| `JOIN FETCH`/entity graph of a **collection** combined with `Pageable` | Warning — Hibernate paginates in memory (`HHH90003004`) |

**Do not recommend** adding `JOIN FETCH` to `@Lock(PESSIMISTIC_WRITE)` queries — it would lock the joined rows (customers, products) too.

### Indexes (`db/schema.sql`)
| Check | Default severity |
|---|---|
| Columns used in finder filters/sorts on large tables (e.g. `orders.status`, `orders.created_at` when sorted by it) without an index | Warning |

**False positive:** MySQL InnoDB automatically creates an index for every foreign key column that doesn't already have one, so FK columns are indexed even without an explicit `CREATE INDEX`. Unique keys are indexes too.

## 6. API boundary

| Check | Default severity |
|---|---|
| Controller returns an entity (directly, in `ResponseEntity`, `List`, `Page`, `Optional`) | Blocker — leaks internal fields, risks lazy-loading exceptions with open-in-view off, and serialises bidirectional relations recursively |
| Controller accepts an entity as `@RequestBody` (mass assignment) | Blocker |
| `@RequestBody` without `@Valid`, or request DTOs without constraints on required fields | Warning |
| Returning `Page<T>` without `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` | Warning — unstable JSON shape |
| Unbounded page size (no `spring.data.web.pageable.max-page-size` and no cap) | Warning — a client can request `size=100000` |
| Endpoints not under `/api/v1/`, or verbs/status codes not matching `.claude/rules/api-design.md` | Warning |

**False positives:** enums used as request params, `ResponseEntity<Void>` for 204s, and `Page<XxxResponse>` with VIA_DTO are all fine.

## 7. Error handling & logging

| Check | Default severity |
|---|---|
| A catch-all `@ExceptionHandler(Exception.class)` exists and returns a generic message | Blocker if missing and `server.error.include-stacktrace`/`include-message` could leak internals |
| Exception messages that embed user input or internals are returned to clients | Warning |
| `server.error.include-stacktrace=always` or `include-message=always` | Blocker |
| Personal data (email, address, phone, payment info) written to logs via `log.info/warn` | Warning; Blocker if payment data |
| Unexpected errors logged with the stack trace (`log.error(..., ex)`) | Info if present — good practice |

## 8. Operations

Severity depends on the deployment target. If it's unknown (no Dockerfile/manifests), report these as Warnings and list "deployment target" under *Not checked*.

| Check | Default severity |
|---|---|
| Actuator present with `health` exposed; liveness/readiness groups (`management.endpoint.health.probes.enabled=true`) for container orchestration | Warning |
| Graceful shutdown (`server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase`) | Warning |
| Connection pool sized explicitly (`spring.datasource.hikari.maximum-pool-size`, timeouts) | Info |
| Metrics export (Micrometer registry) | Info |
| Structured/JSON logging or a log config suitable for aggregation | Info |

## 9. Tests & build

| Check | Default severity |
|---|---|
| `./mvnw clean compile` fails | Blocker |
| `./mvnw test` has failures | Blocker |
| Controllers without a `@WebMvcTest` (`XxxControllerTest`) | Warning — status codes, validation (400) and error mapping (404/409/422) are untested |
| Services without a unit test (`XxxServiceTest`) | Warning |
| Critical business rules (stock reservation, order status transitions, refunds) without tests that exercise them | Warning |
| Tests violating `.claude/rules/testing.md` (naming `should_x_when_y`, AAA comments) | Info |
| Project version is `-SNAPSHOT` | Info |
| Dependency versions unpinned or using ranges | Warning |

To measure test coverage, compare classes in `src/main/java/**/controller` and `service` with matching `*Test.java` files in `src/test/java`.
