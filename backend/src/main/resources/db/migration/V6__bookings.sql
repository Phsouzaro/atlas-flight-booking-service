-- Reservas (módulo booking). Sem FK para users e flights de propósito: são tabelas de outros módulos,
-- e o booking só conhece o id. O que precisa ser mostrado do voo fica copiado no item.

CREATE TABLE bookings (
    id                UUID          PRIMARY KEY,
    user_id           UUID          NOT NULL,
    code              VARCHAR(6)    NOT NULL,   -- localizador mostrado ao usuário
    status            VARCHAR(20)   NOT NULL,
    total_amount      NUMERIC(12,2) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    hold_expires_at   TIMESTAMPTZ   NOT NULL,
    confirmed_at      TIMESTAMPTZ,
    expired_at        TIMESTAMPTZ,
    payment_reference VARCHAR(64),
    CONSTRAINT uq_bookings_code UNIQUE (code),
    CONSTRAINT ck_bookings_status CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'REBOOKED', 'EXPIRED')),
    CONSTRAINT ck_bookings_total CHECK (total_amount >= 0),
    CONSTRAINT ck_bookings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_bookings_confirmed CHECK (status <> 'CONFIRMED' OR (confirmed_at IS NOT NULL AND payment_reference IS NOT NULL))
);

CREATE INDEX ix_bookings_user ON bookings (user_id, created_at DESC);
-- o job de expiração só olha os holds
CREATE INDEX ix_bookings_held_expiry ON bookings (hold_expires_at) WHERE status = 'HELD';

CREATE TABLE booking_items (
    id                UUID          PRIMARY KEY,
    booking_id        UUID          NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    flight_id         UUID          NOT NULL,
    flight_number     VARCHAR(8)    NOT NULL,
    origin_code       VARCHAR(3)    NOT NULL,
    destination_code  VARCHAR(3)    NOT NULL,
    departure_at      TIMESTAMPTZ   NOT NULL,
    fare_class_code   VARCHAR(20)   NOT NULL,
    seats             INT           NOT NULL,
    unit_price_amount NUMERIC(12,2) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    CONSTRAINT ck_booking_items_seats CHECK (seats BETWEEN 1 AND 9),
    CONSTRAINT ck_booking_items_price CHECK (unit_price_amount >= 0)
);

CREATE INDEX ix_booking_items_booking ON booking_items (booking_id);

CREATE TABLE passengers (
    id              UUID         PRIMARY KEY,
    booking_id      UUID         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    position        INT          NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    document_number VARCHAR(30)  NOT NULL,
    birth_date      DATE         NOT NULL,
    CONSTRAINT uq_passengers_position UNIQUE (booking_id, position)
);
