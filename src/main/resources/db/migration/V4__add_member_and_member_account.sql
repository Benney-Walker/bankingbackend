CREATE TABLE member (
                        id UUID PRIMARY KEY,
                        first_name VARCHAR(50) NOT NULL,
                        middle_name VARCHAR(100),
                        last_name VARCHAR(50) NOT NULL,
                        date_of_birth DATE NOT NULL,
                        phone_number VARCHAR(30) NOT NULL,
                        email VARCHAR(255),
                        residential_address TEXT,
                        occupation VARCHAR(150),
                        membership_status VARCHAR(20) DEFAULT 'PENDING',
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL
);

CREATE TABLE member_kyc_records (
                                    id UUID PRIMARY KEY,
                                    member_id UUID NOT NULL REFERENCES member(id),
                                    document_type VARCHAR(40) NOT NULL,
                                    verification_method VARCHAR(50),
                                    verified_by UUID REFERENCES staffs(id),
                                    verified_at TIMESTAMP NOT NULL,
                                    status VARCHAR(20) NOT NULL,
                                    rejection_reason TEXT,
                                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    CHECK ((status <> 'VERIFIED') OR verified_at IS NOT NULL)
);

CREATE TABLE member_account (
                                id UUID PRIMARY KEY,
                                member_id UUID REFERENCES member(id),
                                gl_account_id INT REFERENCES gl_account(id), -- Links this specific account to a GL category
                                account_number VARCHAR(20) UNIQUE NOT NULL,
                                account_type VARCHAR(20) NOT NULL, -- 'SAVINGS', 'CHECKING', 'LOAN'
                                status VARCHAR(20) NOT NULL,
                                created_at TIMESTAMP
);