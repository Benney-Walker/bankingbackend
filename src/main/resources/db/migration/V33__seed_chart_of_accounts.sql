-- Working chart of accounts (owner's proposal with Claude's five additions, 2026-11-13).
-- Not yet approved by the credit union; accounts and codes are data, so they can be changed
-- after review without a code change. Header accounts only group others and are never posted to.
ALTER TABLE gl_account ADD COLUMN is_header BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO gl_account (account_code, name, category, is_header) VALUES
    ('1000', 'Assets',                     'ASSET',     TRUE),
    ('1010', 'Cash on Hand',               'ASSET',     FALSE),
    ('1015', 'Cash with Collectors',       'ASSET',     FALSE),
    ('1020', 'Bank Account',               'ASSET',     FALSE),
    ('1100', 'Loans Receivable',           'ASSET',     FALSE),
    ('2000', 'Liabilities',                'LIABILITY', TRUE),
    ('2010', 'Members'' Savings Deposits', 'LIABILITY', FALSE),
    ('2015', 'Members'' Current Accounts', 'LIABILITY', FALSE),
    ('3000', 'Equity',                     'EQUITY',    TRUE),
    ('3010', 'Share Capital',              'EQUITY',    FALSE),
    ('4000', 'Revenue',                    'REVENUE',   TRUE),
    ('4010', 'Loan Interest Income',       'REVENUE',   FALSE),
    ('4020', 'Fee and Commission Income',  'REVENUE',   FALSE),
    ('5000', 'Expenses',                   'EXPENSE',   TRUE),
    ('5010', 'Salaries and Wages',         'EXPENSE',   FALSE),
    ('5020', 'Operating Expenses',         'EXPENSE',   FALSE)
ON CONFLICT (account_code) DO NOTHING;
