ALTER TABLE staffs
    ADD COLUMN branch UUID NOT NULL,
    ADD CONSTRAINT fk_target_table_branch_table
    FOREIGN KEY (branch) REFERENCES branch_table(id);