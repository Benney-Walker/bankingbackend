CREATE TABLE transaction (
                             id UUID PRIMARY KEY,
                             idempotency_key VARCHAR(100) UNIQUE NOT NULL, -- Prevents duplicate processing on retries
                             transaction_type VARCHAR(50) NOT NULL, -- 'DEPOSIT', 'WITHDRAWAL', 'LOAN_PAYMENT', 'FEE'
                             description TEXT,
                             status VARCHAR(20) NOT NULL,
                             created_at TIMESTAMP
);

CREATE TABLE journal_entry (
                               id UUID PRIMARY KEY,
                               transaction_id UUID REFERENCES transaction(id) NOT NULL,
                               gl_account_id INT REFERENCES gl_account(id) NOT NULL,
                               member_account_id UUID REFERENCES member_account(id), -- NULL for internal credit union movements
                               amount DECIMAL(19, 4) NOT NULL, -- NEVER use FLOAT for financial data
                               entry_type VARCHAR(10) NOT NULL, -- 'DEBIT' or 'CREDIT'
                               created_at TIMESTAMP
);