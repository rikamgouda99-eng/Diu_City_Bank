-- DIU City Bank: one-time migration for the banking modules (transactions, FD, cards, loans, KYC...)
-- Run once against the DCB schema BEFORE starting the new version of the application:
--   mysql -u root -p DCB < db/migration-v2-banking-modules.sql
-- Safe to re-run. Hibernate (ddl-auto=update) creates all new tables on startup.

-- 1. Money must be DECIMAL, never DOUBLE. Existing balances are preserved.
ALTER TABLE bank_account MODIFY account_balance DECIMAL(19,2);

-- 2. The old card_entity, loan_details and transaction_status_table were never used by the application
--    (no rows) and their shape can't hold the new modules (card_entity even had a plaintext CVV column).
--    They are dropped ONLY if they still have the old shape AND are empty; Hibernate then recreates
--    them with the new structure under the same names. Tables with data are left untouched.

-- card_entity (old shape has a 'cvv' column)
SET @legacy := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'card_entity' AND column_name = 'cvv');
SET @rows := -1;
SET @sql := IF(@legacy > 0, 'SELECT COUNT(*) INTO @rows FROM card_entity', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@rows = 0, 'DROP TABLE card_entity', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(@rows = 0, 'card_entity: legacy table dropped', IF(@rows > 0, 'card_entity: HAS DATA, not dropped - migrate manually', 'card_entity: already migrated')) AS card_entity;

-- loan_details (old shape has a 'duration' column)
SET @legacy := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'loan_details' AND column_name = 'duration');
SET @rows := -1;
SET @sql := IF(@legacy > 0, 'SELECT COUNT(*) INTO @rows FROM loan_details', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@rows = 0, 'DROP TABLE loan_details', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(@rows = 0, 'loan_details: legacy table dropped', IF(@rows > 0, 'loan_details: HAS DATA, not dropped - migrate manually', 'loan_details: already migrated')) AS loan_details;

-- transaction_status_table (old shape has no 'reference' column)
SET @exists := (SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'transaction_status_table');
SET @migrated := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'transaction_status_table' AND column_name = 'reference');
SET @rows := -1;
SET @sql := IF(@exists > 0 AND @migrated = 0, 'SELECT COUNT(*) INTO @rows FROM transaction_status_table', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(@rows = 0, 'DROP TABLE transaction_status_table', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SELECT IF(@rows = 0, 'transaction_status_table: legacy table dropped', IF(@rows > 0, 'transaction_status_table: HAS DATA, not dropped - migrate manually', 'transaction_status_table: already migrated')) AS transaction_status_table;
