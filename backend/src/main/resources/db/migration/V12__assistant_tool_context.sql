-- Resultados das tools de cada resposta do assistente, para os próximos turnos (fase 4).
-- Sem isso o modelo perde os ids devolvidos pelas tools (flightId, bookingId) assim que o turno acaba e
-- precisa buscar de novo, ou pior, inventar. Só os ids e dados já mostrados ao modelo: sem PII nova.
ALTER TABLE assistant_messages ADD COLUMN tool_context TEXT;
