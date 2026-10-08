# Design notes

Working notes made while building the API: the conventions the layers rely on, the
exception-to-status mapping, the ownership policy, and the places where the provided acceptance
script pins down behaviour that the assignment PDF leaves open.

## Layering conventions

- `controller` is thin: bind and validate the request, delegate to a service, wrap the result in a
  response DTO, set the status. No business logic, and never builds an error body.
- `service` owns every rule. Services resolve the caller through `CurrentUserProvider` rather than
  touching the Spring Security API, and read "today" from an injected `java.time.Clock`, so they are
  deterministic under test.
- `repository` owns every query. Filtering and aggregation happen in the database, never in memory.
- `dto.request` / `dto.response` are separate from entities in both directions.

## Exception → HTTP status

Handled centrally in `exception/GlobalExceptionHandler`.

| Exception | Status |
|---|---|
| `BadRequestException` | 400 |
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 (`details[]` per field) |
| `HttpMessageNotReadableException` (malformed JSON, bad date/enum/number) | 400 |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` | 400 |
| `UnauthorizedException`, Spring Security `AuthenticationException` | 401 |
| `ForbiddenOperationException` | 403 |
| `ResourceNotFoundException`, `NoHandlerFoundException`, `NoResourceFoundException` | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `ConflictException`, `DataIntegrityViolationException` | 409 |
| `HttpMediaTypeNotSupportedException` | 415 |
| anything else | 500, generic message, stack trace logged but never returned |

Unauthenticated access is handled by `JsonAuthenticationEntryPoint` (401) and Spring Security
denials by `JsonAccessDeniedHandler` (403). Both emit the same `ErrorResponse` shape as the handler
above, so there is exactly one error format across the whole API:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request",
  "message": "Validation failed", "path": "/api/transactions",
  "details": ["amount: must be greater than 0"] }
```

`details` is omitted when empty.

## 403 vs 404

| Resource | Does not exist | Exists, owned by another user |
|---|---|---|
| Transaction (`/api/transactions/{id}`) | 404 | **404** — existence must not leak |
| Savings goal (`/api/goals/{id}`) | 404 | **403** — the assignment lists 403 for goal endpoints |
| Category by name (`/api/categories/{name}`) | 404 | 404 for another user's custom category; **403** for a system default |
| `categoryId` filter on `GET /api/transactions` | 404 | 404 |

The split is deliberate: the assignment explicitly documents 403 on the goal endpoints, but leaking
"this transaction id exists, it just isn't yours" is worse than being consistent, so transactions
answer 404 either way.

## Behaviour pinned by the acceptance script

The provided `financial_manager_tests.sh` settles several things the PDF leaves ambiguous. Where it
and my own first reading disagreed, the script wins — it is what the deliverable is graded against.

1. **`PUT /api/transactions/{id}` ignores `date`.** A body containing `date` returns **200** with
   the stored date unchanged, rather than 400. A body containing *only* `date` is also 200. The
   assignment says the date "cannot be modified", which I first implemented as a rejection; the
   script expects it to be ignored.
2. **`progressPercentage` is a JSON floating point number**, rounded half-up to two decimals:
   `0.0`, `50.0`, `60.33`, `65.5`. Not a fixed-scale decimal. The PDF's own examples (`20.0`,
   `16.67`) agree.
3. **Exact-zero aggregates serialise as `0`**, not `0.00` — `MoneyUtil.normalize` handles
   `currentProgress` and `netSavings`. Non-zero values keep scale 2 (`6550.00`).
4. **Category responses carry both `isCustom` and `custom`** with the same value: the PDF uses the
   first spelling, the script greps for the second.
5. **`GET /api/transactions` accepts `?category=<name>`** as well as `?categoryId=<id>`. Only the
   latter is documented; only the former is exercised by the script.
6. **Transaction JSON contains exactly one `"id":` per transaction** — the script counts
   occurrences to assert list lengths, so no additional field may use that exact spelling.
7. **Responses are not pretty-printed**, for the same reason.

## Shared helpers

- `util/MoneyUtil` — `normalize` (exact zero → `0`), `scale` (always 2dp), `nullToZero`.
- `dto/request/TransactionFilter.parse(...)` — parses and validates every query filter in one place,
  so controllers stay free of parsing logic.
- `config/DefaultCategorySeeder.DEFAULT_CATEGORIES` — the seven system categories.

## Conventions

- No Lombok (ruled out by the assignment's stack). Plain Java and records.
- Money is always `BigDecimal`; the single exception is `progressPercentage`, for the reason above.
- JavaDoc on public classes and methods.
