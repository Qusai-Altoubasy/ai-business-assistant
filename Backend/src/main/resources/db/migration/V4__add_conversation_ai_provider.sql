ALTER TABLE conversations
    ADD COLUMN ai_provider VARCHAR(16);

-- Existing conversations were created using Gemini.
UPDATE conversations
SET ai_provider = 'GEMINI';

ALTER TABLE conversations
    ALTER COLUMN ai_provider SET NOT NULL;

ALTER TABLE conversations
    ADD CONSTRAINT conversations_ai_provider_check
    CHECK (ai_provider IN ('GEMINI', 'OLLAMA'));
