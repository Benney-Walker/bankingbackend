-- Collectors are staff accounts with the COLLECTOR role (owner decision 2026-11-14), so the
-- separate collector table (V26, V27, V29, V30) is no longer used. No collector could log in from
-- it, and nothing references it.
DROP TABLE IF EXISTS collector;
