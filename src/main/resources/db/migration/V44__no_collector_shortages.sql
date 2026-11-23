-- No collector shortages (owner decision 2026-11-23, replacing V43 the same day): approval of a
-- daily submission is refused when the cash counted is below the expected amount, so there is
-- nothing to repay. The repayment table and its posting rules go. A new migration rather than
-- an edit to V43, so databases that already ran V43 still start.
DROP TABLE collector_shortage_repayment;

DELETE FROM posting_rule WHERE event = 'COLLECTOR_SHORTAGE_REPAYMENT';

DROP INDEX idx_collector_submission_shortage;
