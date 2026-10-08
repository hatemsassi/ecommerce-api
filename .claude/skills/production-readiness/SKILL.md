---
name: production-readiness
description: Audit this Spring Boot / JPA / MySQL API for production readiness and produce a severity-ranked report (Blocker / Warning / Info) with a Ready / Not ready verdict. Covers configuration and profiles, secrets, security and endpoint exposure, transactions, N+1 queries, DTO boundaries, error handling, logging of sensitive data, database schema and indexes, operational endpoints, tests and build. Use this skill whenever the user asks whether the app is ready to deploy, ship, release or go live, asks for a pre-release / go-live / launch checklist, a hardening or "what would break in prod" review, or wants a broad health check of the codebase — even if they don't say "production readiness". Prefer it over the narrower check-transactions, find-n-plus-one and verify-dto-coverage commands when the question is about the app as a whole.
---

# Production readiness check

The goal is an honest answer to one question: *what would hurt us if this code went to production today?* The reader is a developer deciding whether to ship, so each finding has to be real, located precisely, and come with a fix they can act on. A short report of verified problems is worth far more than a long list of generic advice — false positives teach people to ignore the report.

## Workflow

### 1. Build a picture of the project

Read these before judging anything, because most "problems" turn out to be handled somewhere else:

- `pom.xml` — Spring Boot version, starters (is `spring-boot-starter-security` there? actuator? devtools?), project version.
- Every `application*.properties` / `application*.yml` under `src/main/resources` and `src/test/resources` — note which profiles exist and what each overrides.
- The `config` package — security, web, OpenAPI, CORS configuration.
- `.claude/CLAUDE.md` and `.claude/rules/*.md` — the project's own rules are part of the bar.
- `db/schema.sql` (tables come from here; Hibernate only validates).
- Deployment artefacts if any: `Dockerfile`, `docker-compose*`, k8s manifests, CI workflows. Their absence tells you the deployment target is unknown — say so in the report rather than guessing.

### 2. Run the checks

Work through `references/checklist.md`. It lists what to look for in each category, how to verify it, and the traps that commonly produce false positives. Read it fully the first time; it encodes the project-specific nuances (e.g. why a class-level `@Transactional(readOnly = true)` makes a missing method annotation dangerous, and why `default_batch_fetch_size` changes how severe a lazy association is).

For every candidate finding, verify before reporting:
- Is it overridden by a profile, an env var, or other config? A dev default in `application.properties` is fine *if* a prod profile overrides it — and a Blocker if nothing does.
- Is it mitigated elsewhere (global config, a base class, an entity graph)?
- Can you point to `file:line`? If you can't locate it, it isn't a finding yet.

### 3. Run the build

Run `./mvnw clean compile` and then `./mvnw test` (on Windows PowerShell, `.\mvnw.cmd`). Failing compilation or tests is a Blocker. Record the failing test names and the key error lines, not the whole log.

If the build can't run for environmental reasons (no JDK, no network for dependencies, no database for an integration test), report that as a limitation of the check rather than a defect in the code — the user needs to know what wasn't verified. Long-running commands are fine; don't skip the build to save time.

Do not modify code during the audit. The user asked for an assessment; changes come after they've seen it.

### 4. Assign severity

- **Blocker** — would cause a security exposure, data loss/corruption, leaked secrets or personal data, or an outage in production. Ship nothing until fixed.
- **Warning** — will cause pain (performance, operability, maintainability, missing safety net) but isn't an immediate incident.
- **Info** — worth knowing; good practice already in place counts here too when it's non-obvious (it tells the reader what *not* to change).

Calibrate to impact, not to how easy it is to spot. A missing authentication layer on an API that changes payment status is a Blocker even though it's one missing dependency; a `-SNAPSHOT` version is Info.

### 5. Write the report

Use this structure:

```markdown
# Production readiness — <project name>

**Verdict:** Not ready | Ready with warnings | Ready
<one or two sentences: the deciding reasons>

| Severity | Count |
|---|---|
| Blocker | n |
| Warning | n |
| Info | n |

## Blockers
| # | Category | Location | Problem | Why it matters | Fix |
|---|---|---|---|---|---|
| B1 | Security | `pom.xml` | ... | ... | ... |

## Warnings
(same table, W1, W2, ...)

## Info
(same table, or a short list)

## Build & tests
- `./mvnw clean compile`: pass/fail (+ key error)
- `./mvnw test`: X passed, Y failed (+ failing test names)

## Not checked
<anything you couldn't verify and why, e.g. "Deployment target unknown — no Dockerfile or manifests found">
```

Verdict rule: any Blocker → **Not ready**; no Blockers but Warnings → **Ready with warnings**; otherwise **Ready**.

Locations are `path:line` relative to the repo root so they're clickable. Fixes should be concrete (the property to set, the annotation to add, the dependency to include) rather than "consider improving X".

Close by offering to fix the Blockers, and name which ones you'd start with.
