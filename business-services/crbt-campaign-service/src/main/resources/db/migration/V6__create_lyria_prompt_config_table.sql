-- Migration for Lyria-3 Dynamic Prompt Configuration
CREATE TABLE lyria_prompt_config (
    id BIGSERIAL PRIMARY KEY,
    prompt_template TEXT NOT NULL,
    keys_json TEXT NOT NULL,
    secondary_instrumentations_json TEXT NOT NULL,
    tempo_grooves_json TEXT NOT NULL,
    acoustic_environments_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

CREATE INDEX idx_lyria_prompt_config_status ON lyria_prompt_config(status) WHERE status = 'ACTIVE';

-- Seed default prompt config as active
INSERT INTO lyria_prompt_config (
    prompt_template,
    keys_json,
    secondary_instrumentations_json,
    tempo_grooves_json,
    acoustic_environments_json,
    status,
    updated_by
) VALUES (
    'You are Lyria 3, generating short ringtones for Mytel CRBT. Constraints: - Output exactly one 30-second instrumental track. - No vocals, no speech, no copyrighted melodies. - Match genre=%s, mood=%s, primary instrument=%s. - Vary the arrangement: tempo around %d BPM, key of %s. - Instrumentation details: %s. - Tempo and groove feel: %s. - Acoustic environment/vibe: %s. - Structure the arrangement with a fresh melodic motif and a distinct chord progression. - Provide a clean fade-friendly ending so the track loops smoothly. - Quality enforcement: Strictly instrumental, no vocals, High fidelity, 48kHz, radio-ready mixing, clean mastering, catchy ringtone melody, balanced EQ, avoiding harsh treble.',
    '["C major", "G major", "D major", "A major", "E major", "F major", "B-flat major", "A minor", "E minor", "D minor"]',
    '["solo acoustic guitar and soft flute", "electric piano and a gentle string quartet", "ambient synthesizer pads and a soft marimba", "acoustic ukulele and bright upright piano", "subtle acoustic guitar and delicate glockenspiel", "warm electric guitar and vintage electric piano"]',
    '["relaxed groove with a slow tempo", "upbeat bouncy rhythm with a mid-tempo feel", "energetic driving beat with a fast tempo", "soft laid-back percussion with a gentle pulse", "smooth steady rhythm with a moderate tempo"]',
    '["intimate lo-fi living room session vibe", "cinematic grand spacious festival vibe", "warm cozy studio recording feel", "dreamy spacious reverb and airy atmosphere", "clean bright modern production style"]',
    'ACTIVE',
    'SYSTEM'
);
