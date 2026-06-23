-- Per-model version history for Lyria prompt config.
ALTER TABLE lyria_prompt_config ADD COLUMN model VARCHAR(60);
ALTER TABLE lyria_prompt_config ADD COLUMN version INTEGER;
ALTER TABLE lyria_prompt_config ADD COLUMN created_by VARCHAR(100);
ALTER TABLE lyria_prompt_config ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE lyria_prompt_config ADD COLUMN deactivated_at TIMESTAMP WITH TIME ZONE;

-- Backfill existing rows as clip-preview v1.
UPDATE lyria_prompt_config SET model = 'lyria-3-clip-preview' WHERE model IS NULL;
UPDATE lyria_prompt_config SET version = 1 WHERE version IS NULL;
UPDATE lyria_prompt_config SET created_by = COALESCE(updated_by, 'SYSTEM') WHERE created_by IS NULL;
UPDATE lyria_prompt_config SET activated_at = created_at WHERE status = 'ACTIVE' AND activated_at IS NULL;

ALTER TABLE lyria_prompt_config ALTER COLUMN model SET NOT NULL;
ALTER TABLE lyria_prompt_config ALTER COLUMN version SET NOT NULL;

CREATE INDEX idx_lyria_prompt_config_model_status ON lyria_prompt_config(model, status);

-- Seed an active v1 for the pro-preview model so both models exist out of the box.
INSERT INTO lyria_prompt_config (
    model,
    version,
    prompt_template,
    keys_json,
    secondary_instrumentations_json,
    tempo_grooves_json,
    acoustic_environments_json,
    status,
    created_by,
    activated_at
) VALUES (
    'lyria-3-pro-preview',
    1,
    'You are Lyria 3 Pro, generating high-fidelity professional full-length musical arrangements for Mytel. Constraints: - Match genre=%s, mood=%s, primary instrument=%s. - Vary the arrangement: tempo around %d BPM, key of %s. - Instrumentation details: %s. - Tempo and groove feel: %s. - Acoustic environment/vibe: %s. - Incorporate premium cinematic mixing, complex counterpoint melodies, and radio-ready production.',
    '["C major", "G major", "D major", "F major", "A minor", "D minor"]',
    '["orchestral violin section with grand piano", "analog synthesizers and electric synth bass", "acoustic guitar with cello harmony"]',
    '["dynamic cinematic crescendo tempo", "steady mid-tempo rock groove", "relaxed chillout acoustic beat"]',
    '["large symphonic concert hall", "high-end analog recording studio", "open-air amphitheater acoustics"]',
    'ACTIVE',
    'SYSTEM',
    CURRENT_TIMESTAMP
);
