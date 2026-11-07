# bankingbackend — Credit Union Management System (backend)

Spring Boot API for a single credit union. It serves the staff desktop application (administrators and agents) and the collector mobile application. Members do not log in; all actions are performed by authenticated staff.

## Stack

- Java 17, Spring Boot 4.1.2 (Web MVC, Security, Data JPA, AMQP, Validation)
- PostgreSQL with Flyway migrations (`spring.jpa.hibernate.ddl-auto=validate`)
- RabbitMQ for internal asynchronous work (account creation, KYC verification). Account-creation messages go through a transactional outbox (`outbox_events`), so they're never lost if RabbitMQ is down
- Cloudflare R2 (S3-compatible, private bucket `kyc-documents`) for KYC documents
- AWS Textract for KYC OCR (provider not final)
- Vynfy for SMS (used for staff password-reset codes). Vynfy only accepts international numbers, so stored local numbers (`0241234567`) are sent as `+233241234567`
- JWT (JJWT) for staff authentication

## Prerequisites

- JDK 17
- A PostgreSQL database
- A RabbitMQ broker
- Cloudflare R2 credentials with access to the `kyc-documents` bucket
- AWS credentials with Textract access (only needed for automated KYC)

## Configuration

Settings live in `src/main/resources/application.properties` as environment-variable placeholders. At startup the application also loads a `.env` file from the working directory, if present (it is git-ignored). Never commit real values.

| Area | Variables |
|---|---|
| Database | `POSTGRES_URL`, `DB_USERNAME`, `POSTGRES_ROOT_PASSWORD` |
| JWT | `JWT_SECRET`, `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION` (milliseconds) |
| RabbitMQ | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` |
| Cloudflare R2 | `CLOUD_FLARE_ID`, `CLOUD_FLARE_ACCESS_KEY`, `CLOUD_FLARE_SECRET_KEY` |
| AWS Textract | `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` (region is `us-east-1`) |
| Vynfy SMS | `VYNFY_SMS_URL`, `VYNFY_API_KEY`, `VYNFY_SENDER_ID` |
| Outbox | `OUTBOX_RETRY_INTERVAL_MS` (optional, default `5000`): how often unsent RabbitMQ messages are retried |
| CORS | `CORS_ALLOWED_ORIGINS` (optional; comma-separated browser origins, no trailing slash. Defaults to the local dev server and the current Vercel app) |

`PUBLIC_URL` is still referenced in the properties file but nothing reads it; KYC documents are stored privately and referenced by object key.

## Running

```bash
./mvnw spring-boot:run     # Flyway applies pending migrations on startup
./mvnw test                # unit tests; no database or broker needed
```

### Tests

- Unit tests use JUnit 5, Mockito and AssertJ (from the Spring Boot test starters) and run with no external services. They currently cover `MemberService` registration, `StaffService` staff creation, `IdGenerator` and the `Member` timestamps.
- `BankingbackendApplicationTests.contextLoads` starts the full application, so it is skipped unless `POSTGRES_URL` is set in the environment. Tests don't load `.env` (only `main()` does), so export the variables in your shell or IDE run configuration to include it.
- Integration tests against a real PostgreSQL and RabbitMQ are not set up yet (decision D1).

### First staff account

There is no bootstrap for the first administrator yet (roadmap increment 1.4), and `POST /api/staff/v1/add-staff` requires a logged-in staff member. Until 1.4 lands, create the first account directly in the database: a row in `staffs` with a BCrypt-hashed `password`, and a matching row in `staff_roles` with role `ADMINISTRATOR`.

## API (current)

All endpoints except `/api/auth/**` require `Authorization: Bearer <access token>`. Only ACTIVE staff can log in, refresh a token or use one. On every request the token's owner is loaded from the database and their current status and roles decide access, so deactivation and role changes take effect immediately; the `roles` claim inside the token is informational only. Each endpoint declares the roles allowed to call it with `@PreAuthorize` (owner-approved, 2026-11-06); any other role gets 403. Every new endpoint must declare its roles too, and `EndpointRoleRulesTest` fails if one doesn't.

| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/api/auth/v1/login` | Public | Staff login with `staffId` and `password`; returns access and refresh tokens and roles |
| POST | `/api/auth/v1/refresh-token` | Public | New access token from `{ "refreshToken": ... }` in the request body; returns `{ "authToken": ... }` |
| POST | `/api/auth/v1/password-reset/request` | Public | Body `{ "staffId" }`. If it's an ACTIVE staff member, a 6-digit code is texted to their phone (Vynfy). At most one code per staff ID per minute; repeats within the minute send nothing. Always returns the same 200 message, whether or not the staff ID exists |
| POST | `/api/auth/v1/password-reset/confirm` | Public | Body `{ "staffId", "code", "newPassword" }`. Code valid 10 minutes, 3 wrong attempts allowed, then a new code is needed. Password 8–72 characters. Wrong/expired code → 400 `Invalid or expired reset code` |
| POST | `/api/staff/v1/add-staff` | ADMINISTRATOR | Create a staff member; roles are `ADMINISTRATOR`, `AGENT`, `COLLECTOR`; password 8–72 characters |
| GET | `/api/staff/v1/all-staff?page=0&size=20` | ADMINISTRATOR | Staff list sorted by staff ID; `size` 1–100. Returns `{ "content": [staff…], "page": { "size", "number", "totalElements", "totalPages" } }` |
| GET | `/api/staff/v1/staff-details/{staffId}` | ADMINISTRATOR | One staff member (404 if unknown) |
| PUT | `/api/staff/v1/update-staff/{staffId}` | ADMINISTRATOR | Replace profile fields: `firstName`, `middleName` (optional), `lastName`, `dateOfBirth` (yyyy-MM-dd), `phoneNumber`, `email`, `residentialAddress`. Same validation and duplicate checks as add-staff, ignoring the staff member being edited. Returns the updated staff |
| PUT | `/api/staff/v1/update-roles/{staffId}` | ADMINISTRATOR | Body `{ "roles": ["AGENT", …] }` is the complete new role set. Takes effect on the staff member's next request. Returns the updated staff |
| POST | `/api/staff/v1/deactivate/{staffId}` | ADMINISTRATOR | Set status `INACTIVE`; blocks the staff member's API access immediately. Already inactive → no change |
| POST | `/api/staff/v1/reactivate/{staffId}` | ADMINISTRATOR | Set status `ACTIVE`. Already active → no change |
| POST | `/api/member/v1/add-member` | ADMINISTRATOR, AGENT | Register a member (status `PENDING`) and queue creation of a shares account plus the selected `SAVINGS` or `CURRENT` account, both inactive with zero balance. `accountType` other than `SAVINGS`/`CURRENT` (including `SHARES`) or a `dob` not in yyyy-MM-dd → 400. Accounts are created asynchronously, normally within a moment |
| POST | `/api/member/v1/kyc-document-upload` | ADMINISTRATOR, AGENT | Multipart: `memberId`, `documentType` (`ECOWAS_CARD`, `VOTER_CARD`), `file`; stores the document in R2 and queues verification |
| POST | `/api/member/v1/manual-kyc-approval/{memberId}` | ADMINISTRATOR, AGENT | Mark KYC verified and activate the member and their accounts |

Staff responses contain `staffId`, `firstName`, `middleName`, `lastName`, `dateOfBirth`, `phoneNumber`, `email`, `residentialAddress`, `status`, `roles`, `createdAt` and `updatedAt`. They never include the password.

### Error responses

Every error uses the same JSON body:

```json
{
  "timestamp": "2026-11-06T23:40:12.123",
  "status": 409,
  "error": "Conflict",
  "message": "A member with this phone number already exists.",
  "path": "/api/member/v1/add-member"
}
```

| Status | When |
|---|---|
| 400 | Validation failure (`message` and `errors` are a field → message map), malformed or missing JSON body, missing parameter or multipart part, wrongly typed value (e.g. a bad UUID in the path), invalid staff phone number (must be 10 digits starting with 0), staff date of birth not in yyyy-MM-dd format, unknown staff role, staff list `page`/`size` out of range |
| 401 | Wrong staff ID or password (an inactive account gets the same response), invalid refresh token, a missing/expired access token, or a token belonging to staff who are no longer active |
| 403 | Authenticated, but the staff member's role isn't allowed to call the endpoint |
| 404 | Unknown path, or a record that doesn't exist (e.g. an unknown staff ID, or manual KYC approval for an unknown member) |
| 405 | HTTP method not supported by the endpoint |
| 409 | Duplicate staff (name + date of birth, phone, email), duplicate member phone; an administrator deactivating themselves or removing their own `ADMINISTRATOR` role; any change that would leave no active administrator |
| 500 | Anything unexpected; the body never contains internal details, which are logged instead |

Services raise `BadRequestException`, `ConflictException`, `ResourceNotFoundException` or `UnauthorizedException` (all extend `ApiException` in `Utility`), and `GlobalExceptionHandler` builds the body. Success response bodies are unchanged.

## RabbitMQ messaging

- Exchange `cash_client_ecosystem` with the queues `default_share_account_queue`, `other_account_creation_queue` and `kyc_verification_queue`.
- **Retries and dead letters:** a message whose listener throws gets 3 retries (1 s, 2 s, 4 s). After that it's rejected and moved to its dead-letter queue (`default_share_account_dead_letter_queue`, `other_account_creation_dead_letter_queue` or `kyc_verification_dead_letter_queue`) through the exchange `cash_client_ecosystem_dead_letter`, keeping its original routing key. Failed messages never loop forever. Inspect dead-letter queues in the RabbitMQ management UI.
- **Duplicates:** account-creation messages come from the outbox and carry its ID as the AMQP message ID. The consumers record that ID in `processed_messages` in the same transaction as the account, and skip a message they've already handled.

> **One-time step when upgrading from a version before 2.2:** the three work queues now carry a dead-letter argument, and RabbitMQ refuses to redeclare an existing queue with different arguments. Once they're empty, delete `default_share_account_queue`, `other_account_creation_queue` and `kyc_verification_queue` in the RabbitMQ management UI (or `rabbitmqctl delete_queue <name>`) before starting the new version, which recreates them. Messages waiting in the outbox aren't affected.

## Database migrations

Migrations are in `src/main/resources/db/migration` and are never edited once applied; every schema change gets the next version number.

| Version | Change |
|---|---|
| V1–V13 | Initial schema: ID counters, GL accounts, staff and roles, members, KYC records, member accounts, transactions, journal entries, loan tables |
| V14 | `member_kyc_records.verified_at` is nullable, so pending KYC records can be saved. The V4 check constraint still requires it once status is `VERIFIED` |
| V15 | `id_entity.id` becomes an identity column, starting after any existing rows, so `IdGenerator` can create its first counters |
| V16 | `password_reset_codes`: at most one pending reset code per staff member (hash, expiry, failed attempts); deleted with the staff record |
| V17 | `outbox_events`: RabbitMQ messages saved with the change they announce, sent after commit and retried until sent |
| V18 | `processed_messages`: message IDs each consumer has already handled, for skipping duplicate deliveries |

## Status

Development follows the approved roadmap in the project docs, one increment at a time.

| Area | State |
|---|---|
| Staff login, JWT access and refresh | Implemented |
| Staff creation | Implemented; persistence fixed in 0.1 (staff IDs are now generated) |
| Member registration and async account creation | Implemented; member timestamps fixed in 0.1 |
| KYC upload to R2 | Implemented; pending records can now be saved (V14) |
| Automated KYC verification | Present but not working end to end (bucket name not injected, runs outside a transaction); fixed in roadmap 3.1. Compares names only |
| Manual KYC approval | Implemented (ADMINISTRATOR, AGENT) |
| Endpoint role permissions | Implemented for all current endpoints (1.1) |
| Transactions, ledger postings, shares, SMS on collection | Not implemented (Phases 4–5) |
| Loans | Entities only; lending rules not approved (Phase 7) |

### Completed increments

- **0.1 Persistence fixes for existing flows:** migrations V14 and V15; repository ID types now match their entities (`GLAccountRepo`, `LoanProductRepo` and `IdEntityRepo` use `Integer`); `Staff` IDs are generated as UUIDs; `Member` sets `createdAt` and `updatedAt` itself.
- **0.2 Test foundation (unit tests):** Mockito unit tests for member registration, staff creation and ID generation; `contextLoads` only runs when the environment is configured. Integration tests wait on D1.
- **0.3 Error handling and logging:** one error body for every endpoint (decided 2026-11-06), domain exceptions instead of hand-built error responses, 400/404/405 handling for malformed requests and unknown paths (previously 500), and SLF4J logging instead of `System.out`/`System.err`; unexpected errors are logged with their stack trace.
- **1.2 Active-staff enforcement and auth fixes:** inactive staff are rejected at login, on refresh and on every request; login authorities use role names; staff phone check fixed (`&&` → `||`) and, with unknown roles, now returns 400 (decision D5a); CORS origins moved to `app.cors.allowed-origins` with the trailing-slash origin fixed; duplicate AMQP starter removed from `pom.xml`.
- **1.1 Endpoint role checks:** method security enabled; add-staff is ADMINISTRATOR only; member registration, KYC upload and manual KYC approval are ADMINISTRATOR or AGENT; `@PreAuthorize` denials return 403 instead of 500.
- **1.3 Refresh-token method:** `/api/auth/v1/refresh-token` is now `POST` (decision D4); `GET` returns 405. Clients must update their call.
- **1.5a Staff list, details and profile update:** ADMINISTRATOR-only endpoints to page through staff, view one, and update profile details; an invalid staff date of birth now returns 400 instead of 500.
- **1.5b Staff roles and status:** ADMINISTRATOR-only role replacement, deactivation and reactivation, with the owner's lockout rule (no self-deactivation, no self-demotion, always at least one active administrator).
- **Immediate role changes (D32):** `JwtAuthenticationFilter` takes roles from the database (with status, in one query) instead of the token.
- **1.5c Staff password reset:** public request/confirm endpoints from a reset page; 6-digit SMS code, stored only as a BCrypt hash (migration V16, `password_reset_codes`), valid 10 minutes, 3 attempts, active staff only; administrators can't reset other people's passwords. Email delivery waits for an email provider.
- **1.5c follow-ups:** SMS recipients converted to `+233…` for Vynfy; one reset code per staff ID per minute; add-staff passwords must be 8–72 characters; reset SMS wording approved.
- **2.1 Reliable member registration:** member and both account-creation messages are saved in one transaction through a transactional outbox (V17); messages are sent right after commit, and a scheduled job retries unsent ones every `OUTBOX_RETRY_INTERVAL_MS`. Account type (SAVINGS/CURRENT only) and date of birth are validated up front (400). Delivery is at-least-once, which 2.2 makes safe.
- **2.2 Duplicate-safe consumers and dead letters:** account-creation consumers skip redelivered messages (V18 `processed_messages`, keyed by message ID and consumer); listeners retry 3 times, then failed messages go to per-queue dead-letter queues. Requires the one-time queue deletion described under RabbitMQ messaging.
