-- Loans (owner decisions 2026-11-24, D25/D26). Builds on the V6 tables, which nothing used yet.
--   - administrators create loan products; interest is flat, at a yearly rate
--   - agents and administrators apply for any branch's member; administrators approve (possibly
--     a lower amount) or reject; approval pays the loan into the member's savings or current
--     account at once, minus the product's processing fee
--   - eligibility: active member, one loan at a time, member for at least
--     loan_min_membership_months (financial settings); guarantors (other members) as many as the
--     product needs, recorded only
--   - monthly installments on the payout day of each month are taken from the same account by a
--     daily job; what's there is taken (the account may be emptied), the rest is overdue and
--     retried every day; no penalty; no early payoff

-- Products ---------------------------------------------------------------------------------------
ALTER TABLE loan_product
    ADD COLUMN processing_fee_type  VARCHAR(20)   NOT NULL DEFAULT 'FLAT' CHECK (processing_fee_type IN ('FLAT', 'PERCENTAGE')),
    ADD COLUMN processing_fee_value DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (processing_fee_value >= 0),
    ADD COLUMN guarantors_required  INT           NOT NULL DEFAULT 0 CHECK (guarantors_required >= 0),
    ADD COLUMN created_at           TIMESTAMP     NOT NULL DEFAULT now(),
    ADD COLUMN updated_at           TIMESTAMP     NOT NULL DEFAULT now(),
    ADD COLUMN updated_by           VARCHAR(50);
-- Only flat interest is offered (owner decision 2026-11-24).
UPDATE loan_product SET interest_type = 'FLAT' WHERE interest_type <> 'FLAT';
ALTER TABLE loan_product
    ADD CONSTRAINT chk_loan_product_flat CHECK (interest_type = 'FLAT'),
    ADD CONSTRAINT chk_loan_product_amounts CHECK (min_amount > 0 AND max_amount >= min_amount),
    ADD CONSTRAINT chk_loan_product_term CHECK (max_term_months > 0),
    ADD CONSTRAINT chk_loan_product_rate CHECK (base_interest_rate >= 0),
    ADD CONSTRAINT chk_loan_product_fee_percent CHECK (processing_fee_type <> 'PERCENTAGE' OR processing_fee_value <= 100);
CREATE UNIQUE INDEX uq_loan_product_name ON loan_product (lower(name));

-- Minimum membership time, one value for every product (owner: set in financial settings). The
-- starting value is a mock, like the other settings.
ALTER TABLE financial_settings
    ADD COLUMN loan_min_membership_months INT NOT NULL DEFAULT 3 CHECK (loan_min_membership_months >= 0);

-- Applications ---------------------------------------------------------------------------------------
-- Statuses: PENDING, then APPROVED (paid out at once) or REJECTED. Final once reviewed.
UPDATE loan_application SET status = 'APPROVED' WHERE status = 'DISBURSED';
UPDATE loan_application SET status = 'PENDING' WHERE status = 'UNDER_REVIEW';
ALTER TABLE loan_application
    ADD COLUMN applied_by  UUID REFERENCES staffs (id),
    ADD COLUMN reviewed_by UUID REFERENCES staffs (id),
    ADD COLUMN reviewed_at TIMESTAMP,
    ADD COLUMN purpose     VARCHAR(500),
    ADD CONSTRAINT chk_loan_application_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT chk_loan_application_amounts CHECK (requested_amount > 0 AND requested_term_months > 0
        AND (approved_amount IS NULL OR (approved_amount > 0 AND approved_amount <= requested_amount))),
    ADD CONSTRAINT chk_loan_application_reviewed CHECK (status = 'PENDING' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    ADD CONSTRAINT chk_loan_application_approved CHECK (status <> 'APPROVED' OR approved_amount IS NOT NULL),
    ADD CONSTRAINT chk_loan_application_rejected CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL);
CREATE INDEX idx_loan_application_member ON loan_application (member_id, status);
CREATE INDEX idx_loan_application_status ON loan_application (status, created_at);
-- One waiting application per member.
CREATE UNIQUE INDEX uq_loan_application_pending ON loan_application (member_id) WHERE status = 'PENDING';

-- Guarantors: other members, recorded only (owner decision 2026-11-24).
CREATE TABLE loan_guarantor (
    id                  UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL REFERENCES loan_application (id),
    member_id           UUID NOT NULL REFERENCES member (id),
    CONSTRAINT uq_loan_guarantor UNIQUE (loan_application_id, member_id)
);
CREATE INDEX idx_loan_guarantor_member ON loan_guarantor (member_id);

-- Loan accounts ---------------------------------------------------------------------------------------
-- member_account_id is the savings or current account the loan was paid into and is repaid from.
-- A member can borrow again from the same account after repaying, so it's no longer unique.
ALTER TABLE loan_account DROP CONSTRAINT loan_account_member_account_id_key;
UPDATE loan_account SET status = 'ACTIVE' WHERE status = 'IN_DEFAULT';
ALTER TABLE loan_account
    ALTER COLUMN loan_application_id SET NOT NULL,
    ALTER COLUMN member_account_id SET NOT NULL,
    ADD COLUMN member_id          UUID REFERENCES member (id),
    ADD COLUMN total_interest     DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (total_interest >= 0),
    ADD COLUMN processing_fee     DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (processing_fee >= 0),
    ADD COLUMN disbursement_transaction_id UUID REFERENCES transaction (id),
    ADD COLUMN closed_at          TIMESTAMP,
    ADD COLUMN created_at         TIMESTAMP NOT NULL DEFAULT now(),
    ADD CONSTRAINT chk_loan_account_status CHECK (status IN ('ACTIVE', 'CLOSED_PAID')),
    ADD CONSTRAINT uq_loan_account_application UNIQUE (loan_application_id);
UPDATE loan_account la SET member_id = ma.member_id FROM member_account ma WHERE ma.id = la.member_account_id;
ALTER TABLE loan_account ALTER COLUMN member_id SET NOT NULL;
-- One loan at a time (owner decision 2026-11-24).
CREATE UNIQUE INDEX uq_loan_account_active_member ON loan_account (member_id) WHERE status = 'ACTIVE';

-- Installments: PENDING (not due), PAID, or OVERDUE (due and not fully collected). The daily job
-- collects what it can on and after the due date.
UPDATE amortization_schedule SET status = 'OVERDUE' WHERE status = 'PARTIAL';
UPDATE amortization_schedule SET paid_principal = 0 WHERE paid_principal IS NULL;
UPDATE amortization_schedule SET paid_interest = 0 WHERE paid_interest IS NULL;
ALTER TABLE amortization_schedule
    ALTER COLUMN paid_principal SET NOT NULL,
    ALTER COLUMN paid_interest SET NOT NULL,
    ADD COLUMN paid_at TIMESTAMP,
    ADD CONSTRAINT chk_schedule_status CHECK (status IN ('PENDING', 'PAID', 'OVERDUE')),
    ADD CONSTRAINT chk_schedule_amounts CHECK (expected_principal >= 0 AND expected_interest >= 0
        AND expected_total = expected_principal + expected_interest
        AND paid_principal >= 0 AND paid_interest >= 0
        AND paid_principal <= expected_principal AND paid_interest <= expected_interest),
    ADD CONSTRAINT uq_schedule_installment UNIQUE (loan_account_id, installment_number);
CREATE INDEX idx_schedule_due ON amortization_schedule (due_date) WHERE status <> 'PAID';

-- Each amount taken for an installment, with its split for the ledger (proportional to the
-- installment, owner decision 2026-11-24; the member only sees the total).
CREATE TABLE loan_repayment (
    id                       UUID PRIMARY KEY,
    loan_account_id          UUID           NOT NULL REFERENCES loan_account (id),
    amortization_schedule_id UUID           NOT NULL REFERENCES amortization_schedule (id),
    transaction_id           UUID           NOT NULL UNIQUE REFERENCES transaction (id),
    principal_part           DECIMAL(19, 4) NOT NULL CHECK (principal_part >= 0),
    interest_part            DECIMAL(19, 4) NOT NULL CHECK (interest_part >= 0),
    created_at               TIMESTAMP      NOT NULL,
    CONSTRAINT chk_loan_repayment_positive CHECK (principal_part + interest_part > 0)
);
CREATE INDEX idx_loan_repayment_loan ON loan_repayment (loan_account_id, created_at);

-- Transactions -----------------------------------------------------------------------------------------
-- Installments are taken by the system, not by a member of staff.
ALTER TABLE transaction DROP CONSTRAINT transaction_channel_check;
ALTER TABLE transaction ADD CONSTRAINT transaction_channel_check CHECK (channel IN ('OFFICE', 'COLLECTOR', 'SYSTEM'));

-- Ledger ------------------------------------------------------------------------------------------------
-- New amount parts:
--   LOAN_PRINCIPAL: the borrowed-money part of an installment collected
--   INTEREST:       the interest part of an installment collected
-- A payout uses PRINCIPAL (the loan), FEE (processing fee) and REMAINDER (loan minus fee, credited
-- to the member).
ALTER TABLE posting_rule DROP CONSTRAINT posting_rule_amount_part_check;
ALTER TABLE posting_rule
    ADD CONSTRAINT posting_rule_amount_part_check
        CHECK (amount_part IN ('PRINCIPAL', 'FEE', 'SHARE', 'REMAINDER', 'SETTLED', 'OVERAGE', 'LOAN_PRINCIPAL', 'INTEREST'));

INSERT INTO posting_rule (event, amount_part, entry_type, gl_account_id, match_side, match_account_type, description)
SELECT r.event, r.amount_part, r.entry_type, g.id, r.match_side, r.match_account_type, r.description
FROM (VALUES
    -- Payout into the member's account, the processing fee kept back
    ('LOAN_DISBURSEMENT', 'PRINCIPAL',      'DEBIT',  '1100', NULL,      NULL,      'Loan given to the member'),
    ('LOAN_DISBURSEMENT', 'REMAINDER',      'CREDIT', '2010', 'PRIMARY', 'SAVINGS', 'Loan paid into the member''s savings, less the fee'),
    ('LOAN_DISBURSEMENT', 'REMAINDER',      'CREDIT', '2015', 'PRIMARY', 'CURRENT', 'Loan paid into the member''s current account, less the fee'),
    ('LOAN_DISBURSEMENT', 'FEE',            'CREDIT', '4020', NULL,      NULL,      'Loan processing fee earned'),
    -- Installment taken from the member's account
    ('LOAN_REPAYMENT',    'PRINCIPAL',      'DEBIT',  '2010', 'PRIMARY', 'SAVINGS', 'Installment taken from the member''s savings'),
    ('LOAN_REPAYMENT',    'PRINCIPAL',      'DEBIT',  '2015', 'PRIMARY', 'CURRENT', 'Installment taken from the member''s current account'),
    ('LOAN_REPAYMENT',    'LOAN_PRINCIPAL', 'CREDIT', '1100', NULL,      NULL,      'Borrowed money repaid'),
    ('LOAN_REPAYMENT',    'INTEREST',       'CREDIT', '4010', NULL,      NULL,      'Loan interest earned')
) AS r (event, amount_part, entry_type, account_code, match_side, match_account_type, description)
JOIN gl_account g ON g.account_code = r.account_code;
