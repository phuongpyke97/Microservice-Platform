ALTER TABLE credit_transactions ADD COLUMN before_balance INTEGER;
ALTER TABLE credit_transactions ADD COLUMN after_balance INTEGER;
ALTER TABLE credit_transactions ADD COLUMN model VARCHAR(60);
