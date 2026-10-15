-- Cancelamento e remarcação (fase 3).
-- Remarcar não altera a reserva antiga além do status: ela vira REBOOKED e aponta para a nova,
-- que nasce CONFIRMED e aponta de volta. O histórico fica inteiro.
ALTER TABLE bookings
    ADD COLUMN cancelled_at       TIMESTAMPTZ,
    ADD COLUMN refund_amount      NUMERIC(12,2),
    ADD COLUMN refund_reference   VARCHAR(64),
    ADD COLUMN rebooked_at        TIMESTAMPTZ,
    ADD COLUMN rebooked_to_id     UUID REFERENCES bookings (id),
    ADD COLUMN rebooked_from_id   UUID REFERENCES bookings (id),
    ADD CONSTRAINT ck_bookings_refund CHECK (refund_amount IS NULL OR refund_amount >= 0),
    ADD CONSTRAINT ck_bookings_cancelled CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND refund_amount IS NOT NULL)),
    ADD CONSTRAINT ck_bookings_rebooked CHECK (status <> 'REBOOKED' OR (rebooked_at IS NOT NULL AND rebooked_to_id IS NOT NULL));

CREATE UNIQUE INDEX uq_bookings_rebooked_from ON bookings (rebooked_from_id) WHERE rebooked_from_id IS NOT NULL;
