CREATE TABLE loan_product (
                              id SERIAL PRIMARY KEY,
                              name VARCHAR(100) NOT NULL,
                              base_interest_rate DECIMAL(5, 4) NOT NULL, -- e.g., 0.1250 for 12.5%
                              interest_type VARCHAR(50) NOT NULL, -- 'FLAT', 'REDUCING_BALANCE'
                              min_amount DECIMAL(19, 4) NOT NULL,
                              max_amount DECIMAL(19, 4) NOT NULL,
                              max_term_months INT NOT NULL,
                              penalty_rate DECIMAL(5, 4),
                              is_active BOOLEAN NOT NULL
);

CREATE TABLE loan_application (
                                  id UUID PRIMARY KEY,
                                  member_id UUID REFERENCES member(id) NOT NULL,
                                  loan_product_id INT REFERENCES loan_product(id) NOT NULL,
                                  requested_amount DECIMAL(19, 4) NOT NULL,
                                  requested_term_months INT NOT NULL,
                                  approved_amount DECIMAL(19, 4), -- Can differ from requested
                                  status VARCHAR(50) NOT NULL , -- 'PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'DISBURSED'
                                  rejection_reason TEXT,
                                  created_at TIMESTAMP NOT NULL,
                                  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE loan_account (
                              id UUID PRIMARY KEY,
                              loan_application_id UUID REFERENCES loan_application(id),
                              member_account_id UUID REFERENCES member_account(id) UNIQUE, -- Links to the GL/Sub-ledger
                              principal_amount DECIMAL(19, 4) NOT NULL,
                              current_interest_rate DECIMAL(5, 4) NOT NULL,
                              term_months INT NOT NULL,
                              disbursement_date DATE NOT NULL,
                              maturity_date DATE NOT NULL,
                              status VARCHAR(20) NOT NULL -- 'ACTIVE', 'IN_DEFAULT', 'CLOSED_PAID'
);

CREATE TABLE amortization_schedule (
                                       id UUID PRIMARY KEY,
                                       loan_account_id UUID REFERENCES loan_account(id) NOT NULL,
                                       installment_number INT NOT NULL,
                                       due_date DATE NOT NULL,
                                       expected_principal DECIMAL(19, 4) NOT NULL,
                                       expected_interest DECIMAL(19, 4) NOT NULL,
                                       expected_total DECIMAL(19, 4) NOT NULL,
                                       paid_principal DECIMAL(19, 4) DEFAULT 0.0000,
                                       paid_interest DECIMAL(19, 4) DEFAULT 0.0000,
                                       status VARCHAR(20) NOT NULL -- 'PENDING', 'PARTIAL', 'PAID', 'OVERDUE'
);