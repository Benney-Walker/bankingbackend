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

All endpoints except `/api/auth/**` require `Authorization: Bearer <access token>`. Only ACTIVE staff can log in, refresh a token or use one. On every request the token's owner is loaded from the database and their current status and roles decide access, so deactivation and role changes take effect immediately; the `roles` claim inside the token is informational only.

Access is set by URL in `SecurityConfig`:

| Path | Roles |
|---|---|
| `/api/auth/**` | Public |
| `/api/admin/**`, `/actuator/**` | ADMINISTRATOR |
| `/api/staff/v1/add-staff` | ADMINISTRATOR |
| `/api/member/**`, `/api/staff/**` (other endpoints), `/api/transaction/**` | ADMINISTRATOR, AGENT |
| anything else | any logged-in staff member |

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/v1/staff-login` | Desktop login with `staffId` and `password`; returns `staffName`, `roles`, `authToken`, `refreshToken`. Collector accounts → 403 |
| POST | `/api/auth/v1/collector-login` | Collector app login, same body and response. Only COLLECTOR accounts (others → 403). See "Collectors" |
| POST | `/api/auth/v1/refresh-staff-token` | Body `{ "refreshToken" }`; returns the new access token as a plain string. Collector, inactive or unknown accounts → 401 |
| POST | `/api/auth/v1/refresh-collector-token` | The same for collectors; non-collector, inactive or unknown accounts → 401 |
| POST | `/api/auth/v1/password-reset/request-code` | Body `{ "staffId" }`. Texts a 6-digit code (Vynfy) to an ACTIVE staff member. Unknown or inactive staff ID → 400; again within a minute → 409; SMS failure → 500 |
| POST | `/api/auth/v1/password-reset/confirm-code` | Body `{ "staffId", "code" }`. Code valid 10 minutes, 3 wrong attempts. Returns a short-lived reset token (`JWT_RESET_EXPIRATION`). Wrong or expired code → 400 |
| PUT | `/api/auth/v1/password-reset/new-password` | Body `{ "newPassword", "resetToken" }`. Password 8–72 characters, can't be the current one (409). Invalid reset token → 401 |
| POST | `/api/staff/v1/add-staff` | ADMINISTRATOR only. Create a staff member or collector in a branch (`branchId`); roles `ADMINISTRATOR`, `AGENT`, `COLLECTOR`. `COLLECTOR` can't be combined with another role (400); a branch can have only one active agent (409). Password 8–72 characters |
| GET | `/api/staff/v1/all-staff` | Staff list: an agent sees their own branch's staff, an administrator everyone. Not paginated yet |
| GET | `/api/staff/v1/staff-details/{staffId}` | One staff member (404 if unknown) |
| PUT | `/api/staff/v1/update-staff/{staffId}` | Replace profile fields: `firstName`, `middleName` (optional), `lastName`, `dateOfBirth` (yyyy-MM-dd), `phoneNumber`, `email`, `residentialAddress` |
| PUT | `/api/staff/v1/update-roles/{staffId}` | Body `{ "roles": ["AGENT", …] }`, the complete new role set; takes effect on the staff member's next request |
| POST | `/api/staff/v1/deactivate/{staffId}` | Set status `INACTIVE`; blocks API access immediately |
| POST | `/api/staff/v1/reactivate/{staffId}` | Set status `ACTIVE` |
| GET | `/api/member/v1/member-details/{memberId}` | Member details, `registeredByStaffId` and `accounts` (`accountNumber`, `accountType`, `status`; no balances) |
| POST | `/api/member/v1/add-member` | Register a member (status `PENDING`) and queue creation of a shares account plus the selected `SAVINGS` or `CURRENT` account, both inactive with zero balance |
| POST | `/api/member/v1/kyc-document-upload` | Multipart: `memberId`, `documentType` (`ECOWAS_CARD`, `VOTER_CARD`), `idNumber` (unique; Ghana Card format `GHA-123456789-0`), `file` (**JPEG or PNG, at most 5 MB**). Creates a `PENDING` KYC record and queues automated verification. One KYC record per member; after a manual rejection only the staff member who registered the member may upload a replacement |
| GET | `/api/member/v1/kyc-document/{memberId}` | The document image, streamed from the private bucket (`Cache-Control: no-store`); each view is logged |
| PUT | `/api/admin/v1/update-member/{memberId}` | Body `{ "phoneNumber", "residentialAddress", "occupation" }`, the only member fields that can change after registration |
| GET | `/api/admin/v1/kyc-reviews?status=MANUAL_REVIEW&page=0&size=20` | KYC records with the given status, oldest first |
| GET | `/api/admin/v1/kyc-details/{memberId}` | One member's KYC record |
| POST | `/api/admin/v1/manual-kyc-approval/{memberId}` | Mark KYC verified and activate the member and their accounts |
| POST | `/api/admin/v1/manual-kyc-rejection/{memberId}` | Body `{ "reason" }` (required). Marks the document `REJECTED`; the member stays `PENDING` |
| POST | `/api/transaction/v1/deposit` | Cash deposit into a savings or current account. See "Transactions and ledger" |
| POST | `/api/transaction/v1/share-purchase` | Cash share purchase into the member's shares account |
| POST | `/api/transaction/v1/withdrawal` | Cash withdrawal from a savings or current account; fee on top |
| POST | `/api/transaction/v1/transfer` | Transfer between two savings or current accounts, same member or two members |
| GET | `/api/transaction/v1/account/{accountNumber}` | Account balances, type, status and owner |
| GET | `/api/transaction/v1/history/{accountNumber}?page=0&size=20` | The account's transactions, newest first, `size` 1–100. Includes transfers into it and first deposits that bought its shares |
| GET | `/api/admin/ledger/v1/trial-balance` | Debit and credit totals and balance for every postable GL account; `balanced` should always be true |

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

Services raise `BadRequestException`, `ConflictException`, `ResourceNotFoundException`, `UnauthorizedException`, `InternalServerError` and others (all extend `ApiException` in the `exception` package), and `GlobalExceptionHandler` builds the body. Success response bodies are unchanged.

## Collectors

Collectors are staff accounts (owner decision 2026-11-14): a row in `staffs` with a branch and **only** the `COLLECTOR` role. They're created with `add-staff` (ADMINISTRATOR only) and get a staff ID from the same counter as everyone else. The separate `collector` table and entity were removed; V36 drops the table.

- **Sign-in:** the collector app uses `collector-login` and `refresh-collector-token`, which accept only collector accounts; the desktop app's `staff-login` and `refresh-staff-token` refuse them. Wrong passwords and inactive accounts get 401 on both.
- **Requests:** a collector's access token works like any staff token. Roles are read from the database on every request, so deactivating a collector cuts them off immediately.
- **Branch rules:** a branch has at most **one active agent** and any number of collectors. Checked when adding staff, changing roles and reactivating (409); the branch row is locked during the check so two changes can't both pass. Deactivating an agent frees the branch for a new one.
- **Roles:** `COLLECTOR` can't be combined with `ADMINISTRATOR` or `AGENT` (400) on add-staff and update-roles.

## RabbitMQ messaging

- Exchange `cash_client_ecosystem` with the queues `default_share_account_queue`, `other_account_creation_queue`, `kyc_verification_queue` and `transaction_sms_queue` (member SMS after each transaction; new queues are simply created on start-up).
- **Retries and dead letters:** a message whose listener throws gets 3 retries (1 s, 2 s, 4 s). After that it's rejected and moved to its dead-letter queue (`default_share_account_dead_letter_queue`, `other_account_creation_dead_letter_queue`, `kyc_verification_dead_letter_queue` or `transaction_sms_dead_letter_queue`) through the exchange `cash_client_ecosystem_dead_letter`, keeping its original routing key. Failed messages never loop forever. Inspect dead-letter queues in the RabbitMQ management UI.
- **Duplicates:** messages come from the outbox and carry its ID as the AMQP message ID. The account-creation consumers record that ID in `processed_messages` in the same transaction as the account, and skip a message they've already handled. The KYC consumer only acts on `PENDING` records, so a repeat is a no-op. The SMS consumer can't put an SMS in a database transaction, so it checks `processed_messages`, sends, then records the ID; a crash in between can send one SMS twice.

- **Automated KYC flow:** upload → `PENDING` record and an outbox message → the consumer reads the record (short transaction) → downloads the document from R2 and runs OCR (no transaction open) → writes the result with the record locked (short transaction). Storage or OCR failures send the record to `MANUAL_REVIEW`; a result for a record that was approved manually in the meantime is ignored.

> **One-time step when upgrading from a version before 2.2:** the three work queues now carry a dead-letter argument, and RabbitMQ refuses to redeclare an existing queue with different arguments. Once they're empty, delete `default_share_account_queue`, `other_account_creation_queue` and `kyc_verification_queue` in the RabbitMQ management UI (or `rabbitmqctl delete_queue <name>`) before starting the new version, which recreates them. Messages waiting in the outbox aren't affected.

## Actuator and API docs

- **Actuator:** only `/actuator/health` and `/actuator/info` are exposed, and both need an ADMINISTRATOR access token (401 without a token, 403 for other roles). Health details (database, RabbitMQ, disk) are shown to that administrator. Every other actuator endpoint (env, beans, configprops, heapdump, …) is off.
- **API docs:** set `API_DOCS_ENABLED=true` to serve the OpenAPI description at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`. Off by default. When on, these pages are public, so keep it off in production. In Swagger UI, use **Authorize** with an access token from login to try endpoints.

## Financial settings

Settings the credit union changes without a code change live in the `financial_settings` table (V31), one row. Administrators will edit them from the desktop app; the backend reads them through `FinancialSettingsService`.

| Setting | Meaning | Mock value |
|---|---|---|
| `initialShareAmount` | Fixed amount (GHS) taken from a member's first deposit for the mandatory share purchase | 50.00 |
| `minFirstDeposit` | A member's first deposit must be at least this (GHS); can't be below `initialShareAmount` | 100.00 |
| `withdrawalMinBalance` | Balance an account must keep after a withdrawal (GHS) | 20.00 |
| `withdrawalDailyLimit` | Most that can be withdrawn from one account per day (GHS) | 10,000.00 |
| `withdrawalFeeEnabled` | Whether the withdrawal fee is charged | true |
| `withdrawalFeeType` | `FLAT` (GHS per withdrawal) or `PERCENTAGE` (of the amount withdrawn) | FLAT |
| `withdrawalFeeValue` | GHS for `FLAT`; percent for `PERCENTAGE` (1.5 = 1.5%, at most 100). A percentage fee is rounded to 2 decimal places, half up | 2.00 |

The mock values are placeholders until the credit union gives its real ones.

- **Updating:** `FinancialSettingsService.updateSettings` replaces all values at once. The request carries the `version` the client last read; if someone else saved in between, it's rejected with 409 so they can reload. Saving unchanged values does nothing.
- **Validation:** amounts can't be negative, the daily limit must be above zero, at most 4 decimal places, and a percentage fee can't exceed 100. The database enforces the same rules.
- **History:** each change is recorded in the audit log (`FINANCIAL_SETTINGS_UPDATED`) with old and new values and who made it.
- No endpoint yet; the desktop app's settings screen will need one. Deposits, first deposits and withdrawals read the settings inside their own transaction.

## Transactions and ledger

Administrators and agents record money movements at the office through `/api/transaction`. Collector recording comes later with the collector flow (10-minute correction window, member SMS).

**Request body** for deposit, share purchase and withdrawal: `{ "idempotencyKey", "accountNumber", "amount", "description" }`. Transfer: `{ "idempotencyKey", "fromAccountNumber", "toAccountNumber", "amount", "description" }`. The app generates `idempotencyKey` (e.g. a UUID) once per user action and re-sends it unchanged on retries: the same key with the same request returns the recorded transaction without moving money again; the same key with a different request → 409. Amounts must be positive with at most 4 decimal places.

**Rules:**
- Accounts and their members must be `ACTIVE` (after KYC) → otherwise 409.
- **Deposit:** savings or current accounts only. A member's **first deposit** must be at least `minFirstDeposit` (400 otherwise); `initialShareAmount` of it goes to their shares account and the rest to the chosen account. It's one transaction with `shareAmount` and `counterpartyAccountNumber` (the shares account).
- **Share purchase:** into the shares account, only after the first deposit (409).
- **Withdrawal:** savings or current only. The fee from the settings is taken **on top** (withdraw 100 with a fee of 2 → account goes down 102). The account must keep `withdrawalMinBalance` after amount and fee, and the amounts withdrawn from one account in a day (Africa/Accra) can't exceed `withdrawalDailyLimit` (fees don't count). Both → 409 with how much can still be withdrawn.
- **Transfer:** between savings/current accounts of the same member or two members; no fee; the source keeps `withdrawalMinBalance`; the receiving member must have made their first deposit (409). Shares can't be transferred.
- Recorded transactions are final (no reversals). Current and available balance move together for now.

**How a transaction is saved:** in one database transaction, the account rows are locked (`SELECT … FOR UPDATE`; deposits and share purchases also lock the member's shares account, transfers lock both accounts in ID order), a retry is detected, the rules are checked, balances change, the transaction row is saved and its journal lines are posted. All of it commits together or not at all.

**Member SMS (owner decision 2026-11-14):** every transaction texts the member their account update. The message is written inside the transaction, so it shows the balance right after it, and goes through the outbox and `transaction_sms_queue`, so it's sent only after the transaction commits and never for a failed one. Account numbers show only the last 4 digits; each message ends with a reference (first 8 characters of the transaction ID). A transfer between two members texts both; between a member's own accounts, one message. A retried request sends no second SMS. If Vynfy refuses or can't be reached, the message is retried 3 times and then moves to `transaction_sms_dead_letter_queue`.

| Transaction | Example |
|---|---|
| Deposit | GHS 40.00 deposited to your SAVINGS account ****0002 on 14/11/2026 10:00. New balance: GHS 140.00. Ref: 3F2A9C1B |
| First deposit | GHS 120.00 deposited on 14/11/2026 10:00: GHS 50.00 to shares ****0001, GHS 70.00 to CURRENT ****0002. Balances: shares GHS 50.00, CURRENT GHS 70.00. Ref: 3F2A9C1B |
| Share purchase | GHS 25.00 paid into your shares account ****0001 on 14/11/2026 10:00. New balance: GHS 75.00. Ref: 3F2A9C1B |
| Withdrawal | GHS 100.00 withdrawn from your SAVINGS account ****0002 on 14/11/2026 10:05. Fee: GHS 2.00. New balance: GHS 38.00. Ref: 7B1D04E2 |
| Transfer, sender | GHS 30.00 transferred from your SAVINGS account ****0002 to account ****0005 on 14/11/2026 10:00. New balance: GHS 470.00. Ref: … |
| Transfer, receiver | GHS 30.00 received in your CURRENT account ****0005 from account ****0002 on 14/11/2026 10:00. New balance: GHS 30.00. Ref: … |
| Own accounts | GHS 30.00 moved from your SAVINGS account ****0002 to your CURRENT account ****0003 on 14/11/2026 10:00. Balances: SAVINGS GHS 470.00, CURRENT GHS 30.00. Ref: … |

**Ledger:** journal lines come from the `posting_rule` table (V34), not code, so the accountant can change which GL account each event uses. Each rule puts one part of the money (`PRINCIPAL`, `FEE`, `SHARE`, `REMAINDER`) on the debit or credit side of a GL account, optionally only for a given account type on the transaction's main account or its counterparty. If the rules can't produce a balanced journal that accounts for every amount, the whole transaction is refused (500) and nothing is saved. Journal lines can't be changed or deleted (database triggers).

| Event | Debit | Credit |
|---|---|---|
| Deposit | 1010 Cash on Hand | 2010 savings / 2015 current |
| First deposit | 1010 (whole amount) | 3010 Share Capital (share part), 2010 / 2015 (rest) |
| Share purchase | 1010 | 3010 |
| Withdrawal | 2010 / 2015 (amount + fee) | 1010 (amount), 4020 Fee and Commission Income (fee) |
| Transfer | source 2010 / 2015 | destination 2010 / 2015 |

The chart (V33) and rules are a working version until the credit union's accountant reviews them.

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
| `FINANCIAL_SETTINGS_UPDATED` | An administrator changed the financial settings (target `SETTINGS`, old and new values) |

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
| V23 | `branch_table`: credit union branches (name, location) |
| V24, V25 | Required `branch` on `member` and `staffs` |
| V26 | `collector` table |
| V27 | Required `branch` on `collector` |
| V28 | `password_reset_codes.is_sent`: whether the reset SMS went out |
| V29, V30 | `password` and `role` on `collector` |
| V31 | `financial_settings`: the editable first-deposit share amount and withdrawal settings, one row, seeded with **mock values** |
| V32 | `financial_settings.min_first_deposit` (mock 100.00); can't be below the share amount |
| V33 | Working chart of accounts (16 GL accounts, 5 of them headers) and `gl_account.is_header` |
| V34 | `posting_rule`: which GL accounts each event debits and credits, seeded from standard practice |
| V35 | `transaction.counterparty_account_id`, `fee_amount`, `share_amount`; journal lines can't be changed or deleted and transactions can't be deleted (triggers) |
| V36 | Drops the old `collector` table: collectors are staff accounts now |

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
| Financial settings | Stored and editable through the service (V31, V32), mock values; no endpoint yet |
| Transactions | Deposits (with first-deposit share split), share purchases, withdrawals (fee on top, minimum balance, daily limit) and transfers at the office, with balance and history reads |
| General ledger | Working chart of accounts, posting rules as data, balanced and immutable journal lines, trial balance |
| Endpoint role permissions | URL rules in `SecurityConfig` (see API) |
| Transaction SMS | Every office transaction texts the member (outbox + `transaction_sms_queue`, retries, dead-letter queue) |
| Collector accounts | Staff with only the COLLECTOR role; own login and refresh endpoints; one active agent per branch (see "Collectors") |
| Collector recording, loans | Not implemented |
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
- **Financial settings and the ledger/transaction engine (PR #14):** editable `financial_settings` with mock values (V31, V32); working chart of accounts (V33) and posting rules as data (V34); `PostingService` builds balanced journal lines from the rules; deposits with the first-deposit share split, share purchases, withdrawals (fee on top, rounded to 2 decimals for percentages, minimum balance, daily limit) and transfers between savings/current accounts of the same or different members, at `/api/transaction` for ADMINISTRATOR and AGENT; account balance and history reads; trial balance at `/api/admin/ledger`. Journal lines and transactions are protected by triggers (V35).
- **Transaction SMS (PR #14):** every transaction queues a member SMS with the new balance through the outbox; `TransactionSmsSender` sends it after commit with retries and a dead-letter queue (`transaction_sms_queue`, `transaction_sms_dead_letter_queue`).
- **Collector auth (owner, master `5837a4f`; fixed on master `806660c`, 2026-11-14):** collectors are staff accounts with only the COLLECTOR role; `collector-login` / `refresh-collector-token` accept only collectors and the staff endpoints refuse them; one active agent per branch; add-staff is ADMINISTRATOR only. The empty `collector` table is dropped by V36.
