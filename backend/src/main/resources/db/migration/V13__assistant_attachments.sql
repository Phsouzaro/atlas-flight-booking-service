-- Voos e reservas que as tools devolveram em cada resposta do assistente (fase 5). A UI desenha cards com
-- eles; guardar junto da resposta faz o histórico e o replay da Idempotency-Key mostrarem os mesmos cards.
-- Mesmos dados que o modelo viu: passageiro só com primeiro nome e inicial, sem documento.
ALTER TABLE assistant_messages ADD COLUMN attachments JSONB;
