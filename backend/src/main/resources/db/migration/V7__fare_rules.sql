-- Regras tarifárias por classe (fase 3). Multas são por passageiro, na moeda de fee_currency.
-- Valores simulados, escolhidos para que as três classes se comportem de forma diferente:
--   Light: não reembolsável; remarca com multa.
--   Flex: reembolsável com multa; remarca sem multa.
--   Executiva: reembolso integral; remarca sem multa.
-- A janela de 24h (desistência sem custo) vale para todas e é regra do domínio, não da classe.

ALTER TABLE fare_classes
    ADD COLUMN refundable        BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN cancellation_fee  NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN change_fee        NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN fee_currency      VARCHAR(3)    NOT NULL DEFAULT 'BRL',
    ADD CONSTRAINT ck_fare_classes_fees CHECK (cancellation_fee >= 0 AND change_fee >= 0),
    ADD CONSTRAINT ck_fare_classes_fee_currency CHECK (fee_currency ~ '^[A-Z]{3}$');

UPDATE fare_classes SET refundable = FALSE, cancellation_fee = 0,      change_fee = 250.00 WHERE code = 'ECONOMY_LIGHT';
UPDATE fare_classes SET refundable = TRUE,  cancellation_fee = 150.00, change_fee = 0      WHERE code = 'ECONOMY_FLEX';
UPDATE fare_classes SET refundable = TRUE,  cancellation_fee = 0,      change_fee = 0      WHERE code = 'BUSINESS';
