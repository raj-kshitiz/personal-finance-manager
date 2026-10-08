# Personal Finance Manager

A REST API for tracking income, expenses, categories, savings goals and spending reports, built
for the "Personal Finance Manager" system design and implementation assignment.

## Table of contents

- [Overview](#overview)
- [Tech stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Run locally](#run-locally)
- [Run tests and coverage](#run-tests-and-coverage)
- [PostgreSQL profile](#postgresql-profile)
- [Docker](#docker)
- [Deploying to Render](#deploying-to-render)
- [API documentation](#api-documentation)
- [Error format](#error-format)
- [Architecture](#architecture)
- [Design decisions](#design-decisions)
- [Known limitations](#known-limitations)

## Overview

The API lets a registered user authenticate with a session cookie and then:

- record income/expense **transactions** against a category, with full CRUD and filtering;
- manage **categories** — seven system defaults (`Salary` income; `Food`, `Rent`,
  `Transportation`, `Entertainment`, `Healthcare`, `Utilities` expense) plus user-owned custom
  categories;
- track **savings goals**, each with an independently computed progress percentage;
- generate **monthly and yearly reports** broken down by category.

Every resource is strictly scoped to the authenticated user — there is no cross-account access to
categories, transactions or goals.

## Tech stack

| Component | Choice |
|---|---|
| Language | Java 21 (assignment requires 17+) |
| Framework | Spring Boot 3.5.16 |
| Build tool | Maven (via the included Maven wrapper, `./mvnw`) |
| Database | H2 (in-memory, default profile) / PostgreSQL (`postgres` profile) |
| Security | Spring Security 6, session-based authentication |
| Testing | JUnit 5, Mockito, Spring MockMvc, spring-security-test |
| Coverage | JaCoCo (80% line-coverage gate, enforced on `verify`) |

## Prerequisites

- JDK 21 (or newer) on `PATH` — only needed if you don't want to rely on the Maven wrapper to
  download it; the wrapper itself requires a JDK to run.
- No local Maven installation is required — use `./mvnw`.
- Docker, only if you intend to build/run the container image.

## Run locally

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` with the default (`h2`) profile: an in-memory H2
database that is created fresh on every startup and seeded with the seven default categories.
The API is served under `/api`, e.g. `http://localhost:8080/api/auth/register`.

Useful local environment overrides (all optional, see [application.yml](src/main/resources/application.yml)):

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `APP_TIMEZONE` | `Asia/Kolkata` | IANA zone used by the application `Clock` bean for every "today" calculation |
| `SESSION_COOKIE_SECURE` | `false` | `Secure` flag on the `JSESSIONID` cookie |

## Run tests and coverage

```bash
./mvnw clean verify
```

This runs the full test suite (service-layer unit tests with Mockito, `@WebMvcTest` controller
tests, and the Spring Boot integration test) and then enforces an 80% line-coverage gate via the
JaCoCo `check` goal bound to the `verify` phase — the build fails if coverage drops below 80%.
The human-readable coverage report is written to `target/site/jacoco/index.html` after the run.

**Current measured coverage: ~93.7% line, ~94.6% branch, across 235 tests** (all passing). The
exact figure moves by a few hundredths between runs, because the end-to-end test exercises a real
servlet container and a few timing-dependent paths; the enforced gate is 80%.

The only JaCoCo exclusions are the `PersonalFinanceManagerApplication` bootstrap class and three
pure framework-wiring classes (`SecurityConfig`, `ClockConfig`, `JacksonConfig`). The default
category seeder is deliberately **not** excluded, because it contains real business logic (which
categories exist, and the idempotency rule), and it is covered by its own unit tests.

The suite is also checked end to end: the official `financial_manager_tests.sh` acceptance script
passes **86/86** against a locally running instance, and `scripts/smoke.sh` passes 65/65.

To run only the tests without the coverage gate:

```bash
./mvnw test
```

## PostgreSQL profile

By default the app uses H2. To point it at PostgreSQL instead, activate the `postgres` Spring
profile and supply the connection details purely through environment variables — no code or
config file changes are needed:

```bash
export SPRING_PROFILES_ACTIVE=postgres
export DB_URL=jdbc:postgresql://localhost:5432/pfm
export DB_USERNAME=pfm_user
export DB_PASSWORD=pfm_password
./mvnw spring-boot:run
```

See [application-postgres.yml](src/main/resources/application-postgres.yml). Hibernate's
`ddl-auto` is set to `update` for both profiles, so the schema (tables, indexes, constraints) is
created/updated automatically on startup — there is no separate migration step for this
assignment. (A production system would switch this to `validate` and own the schema with
Flyway/Liquibase; `update` was chosen here to keep the assignment deployable without an extra
migration tool, as noted directly in `application-postgres.yml`.)

## Docker

Build the image:

```bash
docker build -t personal-finance-manager .
```

Run it (H2, default profile):

```bash
docker run --rm -p 8080:8080 personal-finance-manager
```

Run it against PostgreSQL:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=postgres \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/pfm \
  -e DB_USERNAME=pfm_user \
  -e DB_PASSWORD=pfm_password \
  personal-finance-manager
```

The image is a multi-stage build (`maven:3.9-eclipse-temurin-21` to compile, `eclipse-temurin:21-jre`
to run), runs as a non-root user, honours the `PORT` environment variable (defaults to 8080), and
sets `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` so it stays within a 512 MB
container's memory budget. See [Dockerfile](Dockerfile) for details, including why it
deliberately ships **without** a `HEALTHCHECK` instruction (the `eclipse-temurin:21-jre` base
image has neither `curl` nor `wget`, and Render's own `healthCheckPath` already covers this —
see below).

## Deploying to Render

This repo includes a [`render.yaml`](render.yaml) Blueprint for a free-tier Docker web service.

1. Push this repository to a public GitHub repo.
2. In the Render dashboard, choose **New > Blueprint** and point it at the repo (Render will read
   `render.yaml` automatically), or create a **New > Web Service**, select **Docker** as the
   environment, and point `dockerfilePath` at `./Dockerfile` manually.
3. Confirm/add the following environment variables on the service (already declared in
   `render.yaml`, no secrets involved):

   | Variable | Value | Why |
   |---|---|---|
   | `SESSION_COOKIE_SECURE` | `true` | Render terminates TLS at its proxy; the session cookie must be marked `Secure` in production |
   | `APP_TIMEZONE` | `Asia/Kolkata` | Deterministic "today" regardless of the container's own timezone |
   | `SPRING_PROFILES_ACTIVE` | `prod` | Activates `application-prod.yml` (quieter logging, `Secure` cookie default) |

   To run against PostgreSQL instead of H2 on Render, also set `SPRING_PROFILES_ACTIVE=postgres,prod`
   and add `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (e.g. from a Render PostgreSQL instance) as
   **secret** environment variables in the Render dashboard — never commit them.
4. Render builds the Docker image from `Dockerfile` and polls `healthCheckPath: /actuator/health`
   (public, unauthenticated, see [Security config](#architecture)) before routing traffic to the
   new instance.
5. Once deployed, verify with the official test script against the live URL:

   ```bash
   bash financial_manager_tests.sh https://<your-service>.onrender.com/api
   ```

   or with this repo's own smoke test:

   ```bash
   scripts/smoke.sh https://<your-service>.onrender.com/api
   ```

   Note: Render's free tier spins down an idle instance, so the first request after a period of
   inactivity can take tens of seconds (see [Known limitations](#known-limitations)).

## API documentation

Base path: `/api`. All request/response bodies are `application/json`. All endpoints except
`POST /auth/register`, `POST /auth/login` and `GET /actuator/health` require a valid session
cookie (`JSESSIONID`), obtained from a successful login.

Examples below use a curl cookie jar (`-c cookies.txt` to save, `-b cookies.txt` to send) against
a local server at `http://localhost:8080/api`.

### Authentication

#### `POST /auth/register`

Registers a new user.

Request:
```json
{ "username": "user@example.com", "password": "password123", "fullName": "John Doe", "phoneNumber": "+1234567890" }
```

Validation: `username` required, valid e-mail; `password` required, 8-72 chars with at least one
letter and one digit; `fullName` required, max 100 chars; `phoneNumber` required, matches
`^\+?[0-9]{10,15}$`.

Response (201):
```json
{ "message": "User registered successfully", "userId": 1 }
```

Status codes: `201 Created`, `400 Bad Request` (validation failure), `409 Conflict` (username
already registered, case-insensitive).

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"user@example.com","password":"password123","fullName":"John Doe","phoneNumber":"+1234567890"}'
```

#### `POST /auth/login`

Request:
```json
{ "username": "user@example.com", "password": "password123" }
```

Response (200):
```json
{ "message": "Login successful" }
```

Also sets the `JSESSIONID` session cookie. Status codes: `200 OK`, `401 Unauthorized` (wrong
credentials).

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user@example.com","password":"password123"}'
```

#### `POST /auth/logout`

No request body; uses the session cookie.

Response (200):
```json
{ "message": "Logout successful" }
```

Status codes: `200 OK`, `401 Unauthorized` (no/invalid session). The session is invalidated and
the cookie expired — any later request that reuses the old cookie gets `401`.

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/api/auth/logout
```

### Transactions

#### `POST /transactions`

Request:
```json
{ "amount": 5000.00, "date": "2024-01-15", "category": "Salary", "description": "January Salary" }
```

Validation: `amount` required, strictly positive, max 13 integer / 2 fraction digits; `date`
required, `YYYY-MM-DD`, must not be in the future (per the server's `Clock`, zone
`APP_TIMEZONE`); `category` required, must name a default category or one of the caller's own
custom categories (case-insensitive); `description` optional, max 500 chars.

Response (201):
```json
{ "id": 1, "amount": 5000.00, "date": "2024-01-15", "category": "Salary", "description": "January Salary", "type": "INCOME" }
```

`type` is never sent by the client — it is derived from the category.

Status codes: `201 Created`, `400 Bad Request` (validation, future date, unknown/inaccessible
category), `401 Unauthorized`.

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{"amount":5000.00,"date":"2024-01-15","category":"Salary","description":"January Salary"}'
```

#### `GET /transactions`

Query parameters (all optional): `startDate`, `endDate` (inclusive, `YYYY-MM-DD`), `categoryId`
(numeric), `category` (name, case-insensitive — in addition to `categoryId`), `type` (`INCOME` or
`EXPENSE`, case-insensitive).

```
GET /transactions?startDate=2024-01-01&endDate=2024-01-31&categoryId=1
```

Response (200), newest first (date descending, then id descending):
```json
{ "transactions": [ { "id": 1, "amount": 5000.00, "date": "2024-01-15", "category": "Salary", "description": "January Salary", "type": "INCOME" } ] }
```

Status codes: `200 OK`, `400 Bad Request` (malformed date/type/id, or `startDate` after
`endDate`), `401 Unauthorized`, `404 Not Found` (`categoryId` does not exist or is not
accessible to the caller).

```bash
curl -i -b cookies.txt "http://localhost:8080/api/transactions?startDate=2024-01-01&endDate=2024-01-31"
```

#### `PUT /transactions/{id}`

Request (every field optional, at least one required; `amount`/`description`/`category` only):
```json
{ "amount": 60000.00, "description": "Updated January Salary" }
```

Response (200):
```json
{ "id": 1, "amount": 60000.00, "date": "2024-01-15", "category": "Salary", "description": "Updated January Salary", "type": "INCOME" }
```

**`date` cannot be modified.** If the request body includes a `date` property it is silently
ignored — the call still succeeds (`200`) and the stored date is unchanged in the response. See
[Design decisions](#design-decisions) for why this differs from a literal reading of the
assignment PDF.

Status codes: `200 OK`, `400 Bad Request` (validation failure on a supplied field, non-numeric
`id`), `401 Unauthorized`, `404 Not Found` (transaction does not exist, or belongs to another
user — the two cases are indistinguishable by design).

```bash
curl -i -b cookies.txt -X PUT http://localhost:8080/api/transactions/1 \
  -H "Content-Type: application/json" \
  -d '{"amount":60000.00,"description":"Updated January Salary"}'
```

#### `DELETE /transactions/{id}`

Response (200):
```json
{ "message": "Transaction deleted successfully" }
```

Hard delete — the row is removed, so it immediately disappears from goal-progress calculations
and reports. Status codes: `200 OK`, `401 Unauthorized`, `404 Not Found` (unknown, or another
user's).

```bash
curl -i -b cookies.txt -X DELETE http://localhost:8080/api/transactions/1
```

### Categories

#### `GET /categories`

Response (200) — system defaults first, then the caller's custom categories:
```json
{ "categories": [ { "id": 1, "name": "Salary", "type": "INCOME", "isCustom": false, "custom": false }, { "id": 8, "name": "CustomCategory", "type": "EXPENSE", "isCustom": true, "custom": true } ] }
```

Status codes: `200 OK`, `401 Unauthorized`.

```bash
curl -i -b cookies.txt http://localhost:8080/api/categories
```

#### `POST /categories`

Request:
```json
{ "name": "SideBusinessIncome", "type": "INCOME" }
```

Validation: `name` required, 1-50 chars after trimming; `type` required, `INCOME` or `EXPENSE`
(an unrecognised value is `400`, not `500`).

Response (201):
```json
{ "id": 9, "name": "SideBusinessIncome", "type": "INCOME", "isCustom": true, "custom": true }
```

Status codes: `201 Created`, `400 Bad Request`, `401 Unauthorized`, `409 Conflict` (name already
used by one of the caller's own categories, case-insensitive, OR clashes with a default category
name — another user owning the same custom name is **not** a conflict).

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/api/categories \
  -H "Content-Type: application/json" \
  -d '{"name":"SideBusinessIncome","type":"INCOME"}'
```

#### `DELETE /categories/{name}`

`{name}` may need URL-encoding if it contains spaces or special characters.

Response (200):
```json
{ "message": "Category deleted successfully" }
```

Status codes: `200 OK`; `400 Bad Request` (category is referenced by at least one transaction);
`401 Unauthorized`; `403 Forbidden` (`{name}` is a system default category — cannot be deleted);
`404 Not Found` (no custom category with that name owned by the caller).

```bash
curl -i -b cookies.txt -X DELETE "http://localhost:8080/api/categories/SideBusinessIncome"
```

### Savings goals

#### `POST /goals`

Request:
```json
{ "goalName": "Emergency Fund", "targetAmount": 5000.00, "targetDate": "2026-01-01", "startDate": "2025-01-01" }
```

Validation: `goalName` required, max 100 chars; `targetAmount` required, strictly positive;
`targetDate` required, strictly after today; `startDate` optional (defaults to today), may be in
the past, but must be strictly before `targetDate`.

Response (201):
```json
{ "id": 1, "goalName": "Emergency Fund", "targetAmount": 5000.00, "targetDate": "2026-01-01", "startDate": "2025-01-01", "currentProgress": 1000.00, "progressPercentage": 20.0, "remainingAmount": 4000.00 }
```

Status codes: `201 Created`, `400 Bad Request`, `401 Unauthorized`.

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/api/goals \
  -H "Content-Type: application/json" \
  -d '{"goalName":"Emergency Fund","targetAmount":5000.00,"targetDate":"2026-01-01","startDate":"2025-01-01"}'
```

#### `GET /goals`

Response (200):
```json
{ "goals": [ { "id": 1, "goalName": "Emergency Fund", "targetAmount": 5000.00, "targetDate": "2026-01-01", "startDate": "2025-01-01", "currentProgress": 1000.00, "progressPercentage": 20.0, "remainingAmount": 4000.00 } ] }
```

Status codes: `200 OK`, `401 Unauthorized`.

```bash
curl -i -b cookies.txt http://localhost:8080/api/goals
```

#### `GET /goals/{id}`

Same shape as a single entry above. Status codes: `200 OK`, `400 Bad Request` (non-numeric
`id`), `401 Unauthorized`, `403 Forbidden` (goal belongs to another user), `404 Not Found`
(no such goal).

```bash
curl -i -b cookies.txt http://localhost:8080/api/goals/1
```

#### `PUT /goals/{id}`

Request (`goalName`/`startDate` are not updatable; at least one of the fields below is required):
```json
{ "targetAmount": 6000.00, "targetDate": "2026-02-01" }
```

Response (200), progress recalculated:
```json
{ "id": 1, "goalName": "Emergency Fund", "targetAmount": 6000.00, "targetDate": "2026-02-01", "startDate": "2025-01-01", "currentProgress": 1000.00, "progressPercentage": 16.67, "remainingAmount": 5000.00 }
```

Status codes: `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden` (another user's
goal), `404 Not Found`.

```bash
curl -i -b cookies.txt -X PUT http://localhost:8080/api/goals/1 \
  -H "Content-Type: application/json" \
  -d '{"targetAmount":6000.00,"targetDate":"2026-02-01"}'
```

#### `DELETE /goals/{id}`

Response (200):
```json
{ "message": "Goal deleted successfully" }
```

Status codes: `200 OK`, `401 Unauthorized`, `403 Forbidden` (another user's goal), `404 Not
Found`.

```bash
curl -i -b cookies.txt -X DELETE http://localhost:8080/api/goals/1
```

### Reports

#### `GET /reports/monthly/{year}/{month}`

`year` 1970-2100, `month` 1-12; non-numeric or out-of-range values are `400`. Only categories
with at least one transaction in the period appear.

Response (200):
```json
{ "month": 1, "year": 2024, "totalIncome": { "Freelance": 500.00, "Salary": 3000.00 }, "totalExpenses": { "Food": 400.00, "Rent": 1200.00 }, "netSavings": 1900.00 }
```

An empty period returns `"netSavings": 0` (exact zero, no decimals — see
[Design decisions](#design-decisions)).

Status codes: `200 OK`, `400 Bad Request`, `401 Unauthorized`.

```bash
curl -i -b cookies.txt http://localhost:8080/api/reports/monthly/2024/1
```

#### `GET /reports/yearly/{year}`

`year` 1970-2100; non-numeric or out-of-range is `400`.

Response (200):
```json
{ "year": 2024, "totalIncome": { "Freelance": 6000.00, "Salary": 36000.00 }, "totalExpenses": { "Food": 4800.00, "Rent": 14400.00 }, "netSavings": 22800.00 }
```

Status codes: `200 OK`, `400 Bad Request`, `401 Unauthorized`.

```bash
curl -i -b cookies.txt http://localhost:8080/api/reports/yearly/2024
```

### Health

#### `GET /actuator/health`

Public, unauthenticated. Returns `{"status":"UP"}` when the app and its database connection are
healthy. Used as Render's `healthCheckPath`.

## Error format

Every error — whether raised by validation, a business-rule exception, or Spring Security's own
authentication/authorization handlers — uses the same JSON body:

```json
{
  "timestamp": "2024-01-15T10:30:00.123+05:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/transactions",
  "details": ["amount: must be greater than 0"]
}
```

`details` is a list of `field: message` entries for per-field validation errors and is omitted
entirely (not `null`, not `[]`) when there is nothing to report.

| Exception | Status |
|---|---|
| `BadRequestException` (business-rule violation) | 400 |
| `MethodArgumentNotValidException`, `ConstraintViolationException` (bean validation) | 400, with `details[]` |
| `HttpMessageNotReadableException` (malformed JSON, bad date/enum/number) | 400 |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` | 400 |
| `UnauthorizedException`, Spring Security `AuthenticationException` (incl. bad credentials, no/expired session) | 401 |
| `ForbiddenOperationException` | 403 |
| `ResourceNotFoundException`, unmapped route | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `ConflictException`, `DataIntegrityViolationException` (race-condition duplicate) | 409 |
| `HttpMediaTypeNotSupportedException` | 415 |
| anything else | 500, generic message, stack trace logged server-side only |

Unauthenticated access to a protected endpoint is handled by a custom
`AuthenticationEntryPoint` (401) and access-denied cases by a custom `AccessDeniedHandler` (403);
both emit the exact same `ErrorResponse` body as the `@RestControllerAdvice`, so clients never
see a different error shape depending on where the rejection happened.

## Architecture

Standard layered architecture:

```
Controller  →  Service (interface)  →  Service impl  →  Repository (Spring Data JPA)  →  Database
    ↑                                       ↑
 DTOs (request/response,            CurrentUserProvider (resolves the
 separate from entities)            authenticated user from the
                                     SecurityContext) + Clock (injected
                                     "now"/"today", zone = APP_TIMEZONE)
```

- **Controllers** (`com.pfm.controller`) only translate HTTP ↔ DTOs; no business logic.
- **Services** (`com.pfm.service` interfaces, `com.pfm.service.impl` implementations) hold all
  business rules and validation that cannot be expressed as bean-validation annotations
  (ownership checks, duplicate detection, progress calculation, report aggregation). They never
  touch `HttpServletRequest`/`HttpSession` directly or the Spring Security API — they ask the
  injected `CurrentUserProvider` for the caller and the injected `java.time.Clock` for "today".
- **Repositories** (`com.pfm.repository`) are Spring Data JPA interfaces; filtering and
  aggregation (transaction search, goal progress sums, report GROUP BY) happen as database
  queries, not in-memory loops over fetched entities.
- **DTOs** (`com.pfm.dto.request` / `com.pfm.dto.response`) are separate from the JPA entities
  (`com.pfm.entity`) so the wire format can evolve independently of the persistence model, and so
  that internal fields (e.g. password hashes) can never leak into a response by accident.
- **`com.pfm.exception.GlobalExceptionHandler`** (`@RestControllerAdvice`) is the single place
  that turns any exception into the unified error body described above.
- **`com.pfm.config.ClockConfig`** exposes the `Clock` bean; **`com.pfm.security.CurrentUserProvider`**
  exposes the authenticated user — both are interfaces/beans so they can be mocked or fixed in
  tests, keeping "today" and "who is calling" deterministic.
- **`com.pfm.config.DefaultCategorySeeder`** idempotently seeds the seven default categories on
  startup.

## Design decisions

- **Session auth with CSRF disabled.** The assignment calls for session-based authentication with
  secure cookies; this is a pure JSON API tested with curl cookie jars and the official bash test
  script, not a browser form submitting cross-site. CSRF protection exists to stop a browser from
  silently attaching a user's cookie to a forged cross-origin request — it's not meaningful
  protection for a client that has to deliberately read and resend the session cookie itself, and
  enabling it would require every test script and example `curl` call in this README to first
  fetch and resend a CSRF token. The trade-off is accepted and documented rather than hidden:
  deploy this behind a real browser-facing frontend and CSRF protection (or a token-based auth
  scheme instead of cookies) should be reconsidered.
- **H2 by default, PostgreSQL via profile.** H2 in-memory keeps `./mvnw spring-boot:run` and the
  test suite zero-config and fast; the `postgres` profile is purely environment-variable driven
  (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`) so switching databases never touches code, matching the
  assignment's "Database: H2 (optional)" requirement while still supporting a real deployment
  target.
- **Money is always `BigDecimal`, with one documented exception.** All monetary values and the
  goal `remainingAmount`/report totals use `BigDecimal`, scale 2, `RoundingMode.HALF_UP`, to avoid
  floating-point rounding errors in financial figures. `progressPercentage` on a savings goal is
  the single exception and is a JSON `double` (e.g. `20.0`, `16.67`, `65.5`) — both the
  assignment's own examples and the official test script compare it as a plain floating-point
  number, not a fixed-scale decimal string.
- **DB-side aggregation.** Transaction filtering (`GET /transactions`), goal progress
  (`sum(INCOME) - sum(EXPENSE)` since a goal's start date) and report totals (`GROUP BY` category)
  are all computed with database queries/aggregates rather than fetching rows and summing in Java.
  This keeps correctness automatic when a transaction is deleted (it simply drops out of the next
  aggregate query) and scales better than loading a user's entire transaction history into memory.
- **Timezone via an injected `Clock`.** Every "is this date in the future?", "what does `startDate`
  default to?" and "what's the cut-off for progress?" decision goes through one injected
  `java.time.Clock` bean, fixed to the `APP_TIMEZONE` zone (default `Asia/Kolkata`) rather than
  `LocalDate.now()`/the container's default zone. This makes "today" deterministic in tests (a
  fixed `Clock` can be substituted) and avoids a UTC-vs-local mismatch making a same-day
  transaction look like it's dated "tomorrow" when the grader's script runs close to midnight.
- **403 vs. 404 policy, resource by resource** (see `docs/DESIGN.md` for the authoritative
  table): a **transaction** that exists but belongs to another user returns the same `404` as one
  that doesn't exist at all — existence of another user's transaction must not be leakable through
  the status code. A **savings goal** owned by another user returns `403` instead, because the
  assignment's own API specification explicitly lists `403 Forbidden` among the goal endpoints'
  status codes. A **category** addressed by name follows the same idea: another user's custom
  category "doesn't exist" from the caller's point of view (`404`), while trying to delete a
  **system default** category is `403` — it exists and is visible to everyone, the operation is
  simply not permitted.
- **Transaction `type` is derived, never stored or accepted as input.** It comes from the
  category's own `type` (`INCOME`/`EXPENSE`), so a transaction and its category can never
  disagree, and changing a transaction's category on update automatically re-derives `type`.
- **Category responses include `id`.** The assignment's example response for `GET /categories`
  omits `id`, but `GET /transactions` supports filtering by `categoryId`, which is meaningless
  without a way to discover category ids first — so `id` is added as an extra field on
  `CategoryResponse`, additive and non-breaking relative to the documented shape.
- **Category responses emit both `isCustom` and `custom`.** The assignment's example body uses
  `isCustom`; the official test script's string-matching instead greps for `"custom":true`. Rather
  than pick one and fail the other, both property names are emitted with the same boolean value.
- **`date` is ignored, not rejected, on transaction update.** The assignment prose says "users can
  modify any transaction field except the date field," which taken literally would suggest
  rejecting a request body that contains `date`. The official test script instead sends a `date`
  field on update and expects `200 OK` with the stored date unchanged in the response (not a
  `400`). The behavior here follows the test script: `date` is a documented no-op on
  `PUT /transactions/{id}`, not a validation error, and `TransactionUpdateRequest` doesn't even
  declare the field — Jackson (configured to ignore unknown properties) drops it silently.
- **Exact-zero aggregates render as `0`, not `0.00`.** `currentProgress` on a goal and `netSavings`
  on a report use `MoneyUtil.normalize`, which emits `BigDecimal.ZERO` (serialized as `0`) when the
  value is exactly zero, and a scale-2 value (e.g. `6550.00`) otherwise. This matches the official
  test script's literal string comparisons (`"netSavings": 0` for an empty period vs.
  `"netSavings": 6550.00` for a populated one) and is applied consistently rather than only where a
  test happens to check it.
- **Hard delete.** Deleting a transaction, category or goal removes the row rather than
  soft-deleting/flagging it. The assignment requires that a deleted transaction stop affecting
  goal progress and reports immediately, which a hard delete guarantees for free given the DB-side
  aggregation above; there is no undo/audit requirement in the assignment that would justify the
  extra complexity of soft deletes.
- **Dates are parsed strictly, and a number is never accepted as a date.** Request DTOs carry no
  `@JsonFormat(pattern = "yyyy-MM-dd")`. A pattern-based Jackson formatter resolves *leniently*,
  which silently turned `"2024-02-30"` into 29 February — the client would get a 201 for a date it
  never sent. Without a pattern, Jackson uses `DateTimeFormatter.ISO_LOCAL_DATE`, which rejects
  impossible calendar dates outright. For the same reason `JacksonConfig` refuses to coerce a
  number into a date: `{"date": 12345}` was otherwise read as an epoch day and became a real
  transaction date. Transaction dates before 1900-01-01 are rejected too, because a proleptic
  year-zero date parses fine as JSON but does not survive the JDBC round trip (`0000-01-01` came
  back as `0001-01-01`), silently storing a value the client never sent. All are now 400s.
- **Supplying both `categoryId` and `category` that disagree is a 400.** The assignment documents
  `categoryId` and the official script uses `category` (a name), so both are supported. When both
  are present they must resolve to the same category; otherwise the request is rejected rather
  than one filter silently overriding the other and returning rows that contradict what was asked
  for.
- **The `postgres` profile fails fast with an actionable message.** If `DB_URL`, `DB_USERNAME` or
  `DB_PASSWORD` is unset, `PostgresEnvironmentValidator` names the missing variables at startup. It
  is registered as a `BeanFactoryPostProcessor`, because Spring builds the `DataSource` and
  `EntityManagerFactory` early enough that an ordinary validating bean is not guaranteed to run
  first — registering it as an ordinary bean is not enough, because it can be constructed after the
  datasource has already failed.
  Without it the unresolved `${DB_URL}` placeholder reached the JDBC driver and surfaced as
  `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}` buried in a Hikari stack
  trace, which does not tell an operator reading a failed deploy log what actually went wrong.

## Known limitations

- **In-memory HTTP sessions.** Sessions are held in the application's memory (Spring's default
  servlet session store), not in a shared store like Redis. Restarting the app (a redeploy, a
  crash, Render's free-tier spin-down) invalidates every active session — all logged-in users are
  silently logged out and must log in again.
- **H2 data is lost on restart.** The default profile uses an in-memory H2 database. Every
  restart starts from a clean, freshly-seeded database — nothing is persisted. Use the
  `postgres` profile with a real PostgreSQL instance for data that needs to survive a restart.
- **Render free-tier cold starts.** A free-tier Render web service spins down after a period of
  inactivity; the first request afterwards triggers a cold start that can take on the order of a
  minute before the health check (and then real requests) start succeeding.
- **No CSRF protection.** CSRF is disabled for this cookie-authenticated API (see
  [Design decisions](#design-decisions)); it is not safe to embed this API's session cookie in a
  general-purpose browser frontend without reconsidering this trade-off.
- **No token refresh / expiry renewal.** The session simply expires after the configured timeout
  (30 minutes of inactivity); there is no refresh-token or "remember me" mechanism.
- **No pagination.** `GET /transactions` and `GET /goals` return the caller's entire result set in
  one response. Fine for an assignment-scale dataset; would need `page`/`size` parameters (or
  keyset pagination) before being used with a large transaction history.
