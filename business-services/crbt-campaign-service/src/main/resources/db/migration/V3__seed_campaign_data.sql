-- Seed an active campaign
INSERT INTO campaigns (name, description, status, start_at, end_at) VALUES
('Tet 2026 Promo', 'Promotion campaign for Lunar New Year 2026', 'ACTIVE', '2026-01-01 00:00:00+00', '2026-02-28 23:59:59+00');

-- Seed campaign packages for the campaign above (campaign_id = 1)
INSERT INTO campaign_packages (campaign_id, name, price, credit_amount, validity_days) VALUES
(1, 'Daily 1K', 1000.00, 2, 1),
(1, 'Weekly 5K', 5000.00, 12, 7),
(1, 'Monthly 20K', 20000.00, 50, 30),
(1, 'VIP 249K', 249000.00, 1000, 365);
