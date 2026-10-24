CREATE TABLE gl_account (
                            id SERIAL PRIMARY KEY,
                            account_code VARCHAR(20) UNIQUE NOT NULL, -- e.g., '1000' for Cash, '2000' for Member Deposits
                            name VARCHAR(100) NOT NULL,
                            category VARCHAR(20) NOT NULL, -- 'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'
                            is_active BOOLEAN DEFAULT TRUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);