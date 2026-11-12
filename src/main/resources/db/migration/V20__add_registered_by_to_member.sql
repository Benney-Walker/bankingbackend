-- Who registered each member (owner decision 2026-11-07). Only the staff member who registered a
-- member may re-upload that member's KYC document after a rejection. Members registered before
-- this migration have no value recorded.
ALTER TABLE member
    ADD COLUMN registered_by UUID REFERENCES staffs (id);
