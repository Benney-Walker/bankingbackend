# bankingbackend — Credit Union Management System (backend)

Spring Boot API for a single credit union. It serves the staff desktop application (administrators and agents) and the collector mobile application. Members do not log in; all actions are performed by authenticated staff.

## Stack

- Java 17, Spring Boot 4.1.2 (Web MVC, Security, Data JPA, AMQP, Validation)
- PostgreSQL with Flyway migrations (`spring.jpa.hibernate.ddl-auto=validate`)
- RabbitMQ for internal asynchronous work (account creation, KYC verification). All messages (account creation and KYC verification) go through a transactional outbox (`outbox_events`), so they're never lost if RabbitMQ is down
- Cloudflare R2 (S3-compatible, private bucket `kyc-documents`) for KYC documents
- AWS Textract for KYC OCR (provider not final). The KYC flow only talks to `OcrInterface` (`interfaces` package); `TextractOcrService` is the current implementation, so switching providers means adding one class
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
| API docs | `API_DOCS_ENABLED` (optional, default `false`): serve `/v3/api-docs` and Swagger UI. Leave off in production |

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
| GET | `/api/member/v1/search-members?query=&page=0&size=20` | ADMINISTRATOR, AGENT, COLLECTOR | Members sorted by last then first name; `query` (optional) matches part of a first, middle, last or full name, part of a phone number, or an exact account number. `size` 1–100. Same paged shape as the staff list; each item has `memberId`, names, `phoneNumber`, `membershipStatus` |
| GET | `/api/member/v1/member-details/{memberId}` | ADMINISTRATOR, AGENT, COLLECTOR | Member details plus `registeredByStaffId` (null for members registered before V20) and `accounts` (`accountNumber`, `accountType`, `status`; no balances). 404 if unknown |
| PUT | `/api/member/v1/update-member/{memberId}` | ADMINISTRATOR, AGENT | Body `{ "phoneNumber", "residentialAddress", "occupation" }`, the only member fields that can change after registration; everything else is locked. Phone must be unique among members (409) |
| POST | `/api/member/v1/add-member` | ADMINISTRATOR, AGENT | Register a member (status `PENDING`) and queue creation of a shares account plus the selected `SAVINGS` or `CURRENT` account, both inactive with zero balance. `accountType` other than `SAVINGS`/`CURRENT` (including `SHARES`), a `dob` not in yyyy-MM-dd, or a `phoneNumber` that isn't 10 digits starting with 0 → 400. Accounts are created asynchronously, normally within a moment |
| POST | `/api/member/v1/kyc-document-upload` | ADMINISTRATOR, AGENT | Multipart: `memberId`, `documentType` (`ECOWAS_CARD`, `VOTER_CARD`, case-insensitive), `idNumber` (the number on the document; required, stored trimmed and upper-cased, unique across members → 409 if another member has it; for `ECOWAS_CARD`, the Ghana Card, it must look like `GHA-123456789-0`, else 400; no format rule for `VOTER_CARD` yet), `file` (**JPEG or PNG, at most 5 MB**, judged by the file's content, not its name). Stores the document in the private R2 bucket, creates a `PENDING` KYC record and queues automated verification. One KYC record per member: a second upload → 409, except after a manual rejection, when **only the staff member who registered the member** may upload a replacement (anyone else → 403). The new document replaces the old one (the old file is deleted from R2 after commit), the record goes back to `PENDING` and automated verification runs again. Unknown document type, non-JPEG/PNG or empty file → 400; over 5 MB → 413; unknown member → 404; all before anything is uploaded |
| GET | `/api/member/v1/kyc-reviews?status=MANUAL_REVIEW&page=0&size=20` | ADMINISTRATOR, AGENT | KYC records with the given status (default `MANUAL_REVIEW`; also `PENDING`, `REJECTED`, `VERIFIED`), oldest first, `size` 1–100. **Administrators see every member; agents only members they registered.** Each item: `memberId`, names, `documentType`, `idNumber`, `status`, `reason`, `verificationMethod`, `reviewedByStaffId`, `registeredByStaffId`, `submittedAt`, `verifiedAt` |
| GET | `/api/member/v1/kyc-details/{memberId}` | ADMINISTRATOR, AGENT | One member's KYC record (same fields). Agent viewing a member they didn't register → 403; no KYC → 404 |
| GET | `/api/member/v1/kyc-document/{memberId}` | ADMINISTRATOR, AGENT | The document image itself (`image/jpeg` or `image/png`), streamed from the private bucket by the backend; never a public or pre-signed URL. Sent with `Cache-Control: no-store`. Same visibility rules; each view is logged |
| POST | `/api/member/v1/manual-kyc-approval/{memberId}` | ADMINISTRATOR | Mark KYC verified (method `MANUAL`, approving administrator recorded) and activate the member and their accounts. Only for a document in `PENDING` or `MANUAL_REVIEW`. 404 unknown member; 409 if there's no submission or it's already `VERIFIED`/`REJECTED` |
| POST | `/api/member/v1/manual-kyc-rejection/{memberId}` | ADMINISTRATOR | Body `{ "reason" }` (required). Marks the document `REJECTED` with the reason and the reviewing administrator; the member stays `PENDING` and accounts inactive until a replacement is uploaded and verified. Same status rules and errors as approval |

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
| 400 | Validation failure (`message` and `errors` are a field → message map), malformed or missing JSON body, missing parameter or multipart part, wrongly typed value (e.g. a bad UUID in the path), invalid staff or member phone number (must be exactly 10 digits starting with 0), staff date of birth not in yyyy-MM-dd format, unknown staff role, staff list `page`/`size` out of range |
| 401 | Wrong staff ID or password (an inactive account gets the same response), invalid refresh token, a missing/expired access token, or a token belonging to staff who are no longer active |
| 403 | Authenticated, but the staff member's role isn't allowed to call the endpoint, or a rule limits the action to a particular staff member (KYC re-upload after rejection) |
| 404 | Unknown path, or a record that doesn't exist (e.g. an unknown staff ID or member, or manual KYC approval for an unknown member) |
| 405 | HTTP method not supported by the endpoint |
| 413 | Uploaded file larger than 5 MB |
| 409 | Duplicate staff (name + date of birth, phone, email), duplicate member phone, a second KYC submission for the same member; an administrator deactivating themselves or removing their own `ADMINISTRATOR` role; any change that would leave no active administrator |
| 500 | Anything unexpected; the body never contains internal details, which are logged instead |

Services raise `BadRequestException`, `ConflictException`, `ResourceNotFoundException` or `UnauthorizedException` (all extend `ApiException` in `Utility`), and `GlobalExceptionHandler` builds the body. Success response bodies are unchanged.

## RabbitMQ messaging

- Exchange `cash_client_ecosystem` with the queues `default_share_account_queue`, `other_account_creation_queue` and `kyc_verification_queue`.
- **Retries and dead letters:** a message whose listener throws gets 3 retries (1 s, 2 s, 4 s). After that it's rejected and moved to its dead-letter queue (`default_share_account_dead_letter_queue`, `other_account_creation_dead_letter_queue` or `kyc_verification_dead_letter_queue`) through the exchange `cash_client_ecosystem_dead_letter`, keeping its original routing key. Failed messages never loop forever. Inspect dead-letter queues in the RabbitMQ management UI.
- **Duplicates:** messages come from the outbox and carry its ID as the AMQP message ID. The account-creation consumers record that ID in `processed_messages` in the same transaction as the account, and skip a message they've already handled. The KYC consumer only acts on `PENDING` records, so a repeat is a no-op.

- **Automated KYC flow:** upload → `PENDING` record and an outbox message → the consumer reads the record (short transaction) → downloads the document from R2 and runs OCR (no transaction open) → writes the result with the record locked (short transaction). Storage or OCR failures send the record to `MANUAL_REVIEW`; a result for a record that was approved manually in the meantime is ignored.

> **One-time step when upgrading from a version before 2.2:** the three work queues now carry a dead-letter argument, and RabbitMQ refuses to redeclare an existing queue with different arguments. Once they're empty, delete `default_share_account_queue`, `other_account_creation_queue` and `kyc_verification_queue` in the RabbitMQ management UI (or `rabbitmqctl delete_queue <name>`) before starting the new version, which recreates them. Messages waiting in the outbox aren't affected.

## Actuator and API docs

- **Actuator:** only `/actuator/health` and `/actuator/info` are exposed, and both need an ADMINISTRATOR access token (401 without a token, 403 for other roles). Health details (database, RabbitMQ, disk) are shown to that administrator. Every other actuator endpoint (env, beans, configprops, heapdump, …) is off.
- **API docs:** set `API_DOCS_ENABLED=true` to serve the OpenAPI description at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`. Off by default. When on, these pages are public, so keep it off in production. In Swagger UI, use **Authorize** with an access token from login to try endpoints.

## Audit log

Business actions are recorded in the `audit_log` table (V22), in the same database transaction as the action, so an action and its entry are saved together or not at all. Each entry has the time, the acting staff code (empty for automated actions), the action, the target (`STAFF` with a staff code, or `MEMBER` with a member ID) and a short description. The table is append-only.

| Action | When |
|---|---|
| `STAFF_CREATED`, `STAFF_PROFILE_UPDATED`, `STAFF_ROLES_CHANGED`, `STAFF_DEACTIVATED`, `STAFF_REACTIVATED` | Staff management. Profile updates list the changed field names, not the values; role changes show old and new roles. Requests that change nothing aren't logged |
| `STAFF_PASSWORD_RESET` | A staff member completed a password reset (actor is that staff member) |
| `MEMBER_REGISTERED`, `MEMBER_CONTACT_UPDATED` | Member registration; contact update (changed field names only) |
| `KYC_SUBMITTED`, `KYC_RESUBMITTED` | Document upload; replacement after a rejection. The ID number isn't repeated |
| `KYC_AUTO_VERIFIED`, `KYC_SENT_TO_MANUAL_REVIEW` | Automated check outcome (no actor). The manual-review reason stays on the KYC record because it quotes personal details |
| `KYC_APPROVED`, `KYC_REJECTED` | Manual review by an administrator; rejections include the reason |
| `KYC_DOCUMENT_VIEWED` | A staff member opened a member's ID document |

Passwords, reset codes and document contents are never written to the log. There's no endpoint for reading the log yet.

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
| V19 | Unique `member_kyc_records.member_id`: one KYC record per member. Stops with a clear message if existing data already has a member with two records |
| V20 | `member.registered_by`: the staff member who registered each member (null for members registered earlier) |
| V21 | `member_kyc_records.id_number`: the ID document number typed at upload, unique across members |
| V22 | `audit_log`: business audit trail (who, what, which record, when). Append-only: triggers reject UPDATE, DELETE and TRUNCATE |

## Status

Development follows the approved roadmap in the project docs, one increment at a time.

| Area | State |
|---|---|
| Staff login, JWT access and refresh | Implemented |
| Staff creation | Implemented; persistence fixed in 0.1 (staff IDs are now generated) |
| Member registration and async account creation | Implemented; member timestamps fixed in 0.1 |
| KYC upload to R2 | Implemented; pending records can now be saved (V14) |
| Automated KYC verification | Works end to end (3.1): R2 download and Textract OCR run outside database transactions; first name, last name, date of birth and ID number must all be read with ≥ 85% confidence and all match (3.6) for automatic activation; anything missing, unclear or different goes to `MANUAL_REVIEW` with a reason listing each problem. Dates are compared only through the OCR provider's ISO-normalised value, never guessed from printed text. OCR confirms the text matches; it doesn't prove the document is genuine |
| Manual KYC approval and rejection | Implemented (ADMINISTRATOR only) |
| Endpoint role permissions | Implemented for all current endpoints (1.1) |
| Transaction groundwork | Implemented (5.1): account row locking (`PESSIMISTIC_WRITE`), idempotency keys, amount rules. No money-moving endpoint yet |
| Deposits, withdrawals, transfers, ledger postings, shares, SMS on collection | Not implemented; waits on the approved chart of accounts and posting rules (D15, D30) |
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
- **2.3 Member search, details and contact update:** all roles can search and view members (no balances); ADMINISTRATOR and AGENT can change only phone, address and occupation. Member and staff phone numbers must be exactly 10 digits starting with 0 (`PhoneNumberRules`).
- **3.1 Automated KYC fixed:** the bucket name is now read correctly (download moved into `StorageService`); verification runs as read → external calls → write, without holding a transaction during R2/OCR; the KYC message goes through the outbox; upload validates document type and member before uploading; manual approval returns 409 when there's no KYC submission instead of a 500; `RabbitMQProducer` removed (everything uses the outbox). Account numbers keep their current format (decision D7).
- **3.2 (part) and 3.3 KYC upload rules:** one KYC record per member, enforced in the database (V19) and checked before upload (409); JPEG/PNG only, detected from the file's bytes; 5 MB maximum (app check plus `spring.servlet.multipart.max-file-size=5MB`; over the limit → 413). Stored objects use the detected extension and content type. Re-upload after a rejected review is pending owner clarification.
- **3.2 completed + manual rejection (part of 3.7):** members record who registered them (V20); ADMINISTRATOR-only manual approval **and** rejection (reason required), only for documents under review; after a rejection, only the registering staff member can upload a replacement, which resets the record to `PENDING`, re-runs verification and deletes the old file after commit.
- **3.4 OCR provider abstraction:** `OcrInterface` returns provider-neutral `ExtractedIdDocument` values (first, middle and last name, date of birth, document number, each with a 0–100 confidence); all Textract code lives in `TextractOcrService`. Matching behaviour is unchanged.
- **3.5 ID number capture:** the KYC upload form sends `idNumber`; it's stored on the KYC record (V21), replaced on re-upload, and unique across members (checked before upload and enforced in the database). Ghana Card (`ECOWAS_CARD`) numbers must match `GHA-#########-#` (`IdNumberRules`); the Voter card format is pending the owner's confirmation.
- **3.6 Full field matching:** automatic KYC approval now needs first name, last name, date of birth and ID number all at ≥ 85% OCR confidence and all matching (decision D11); otherwise manual review with an itemised reason.
- **3.7 Review list and document viewing:** ADMINISTRATOR and AGENT can list KYC records by status, open one, and view the document image streamed through the backend (decision D13); administrators see all members, agents only members they registered; approving/rejecting stays ADMINISTRATOR-only.
- **5.1 Transaction groundwork (no endpoints yet):** `AccountLockService` locks member accounts with PostgreSQL row locks (`PESSIMISTIC_WRITE`, i.e. `SELECT … FOR UPDATE`; owner decision 2026-11-07), so money movements on the same account run one at a time. Only ACTIVE accounts of ACTIVE members can be locked (409 otherwise; unknown → 404). Transfers lock both accounts in ascending ID order to prevent deadlocks; same account twice → 400. `TransactionService` handles idempotency keys: the same key with the same type, account and amount returns the recorded transaction instead of applying it again; the same key with a different request → 409, as does a simultaneous duplicate caught by the unique key. `MoneyRules`: amounts must be positive, at most 4 decimal places and fit `DECIMAL(19,4)`. Whether amounts are limited to 2 decimal places (pesewas) is undecided. Recorded transactions are final, with no reversals (D16).
- **8.1 Business audit log:** `audit_log` table (V22, append-only, checked on PostgreSQL 16) and `AuditLogService`, which joins the caller's transaction. Staff management, password resets, member registration and contact updates, and every KYC step (submit, re-upload, automated result, manual approval/rejection, document viewing) are recorded with who acted, on what, and when. Viewing a KYC document now runs in a read-write transaction so the view is recorded.
- **9.x Actuator and API docs:** `spring-boot-starter-actuator` with only health and info exposed, both ADMINISTRATOR-only (owner decision 2026-11-07: no public actuator access); springdoc-openapi 3.2.0 (the line for Spring Boot 4.1) with the JWT bearer scheme documented, served only when `API_DOCS_ENABLED=true`.
