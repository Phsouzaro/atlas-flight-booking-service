-- Catálogo de voos (fase 1).
-- Aeroportos, voos e inventário são simulados e entram pelo seeder do perfil local.
-- As classes tarifárias são dado de referência do domínio (as regras da fase 3 dependem dos códigos),
-- por isso entram aqui e existem em todo ambiente.

CREATE TABLE airports (
    code         VARCHAR(3)   PRIMARY KEY,   -- IATA
    name         VARCHAR(120) NOT NULL,
    city         VARCHAR(80)  NOT NULL,
    country_code VARCHAR(2)   NOT NULL,      -- ISO 3166-1 alpha-2
    time_zone    VARCHAR(40)  NOT NULL,      -- id IANA, ex.: America/Sao_Paulo
    CONSTRAINT ck_airports_code CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_airports_country CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE TABLE fare_classes (
    code          VARCHAR(20) PRIMARY KEY,
    name          VARCHAR(60) NOT NULL,
    cabin         VARCHAR(20) NOT NULL,
    display_order INT         NOT NULL,
    CONSTRAINT ck_fare_classes_cabin CHECK (cabin IN ('ECONOMY', 'BUSINESS'))
);

INSERT INTO fare_classes (code, name, cabin, display_order) VALUES
    ('ECONOMY_LIGHT', 'Econômica Light', 'ECONOMY',  1),
    ('ECONOMY_FLEX',  'Econômica Flex',  'ECONOMY',  2),
    ('BUSINESS',      'Executiva',       'BUSINESS', 3);

CREATE TABLE flights (
    id               UUID        PRIMARY KEY,
    flight_number    VARCHAR(8)  NOT NULL,
    origin_code      VARCHAR(3)  NOT NULL REFERENCES airports (code),
    destination_code VARCHAR(3)  NOT NULL REFERENCES airports (code),
    departure_at     TIMESTAMPTZ NOT NULL,
    arrival_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_flights_number_departure UNIQUE (flight_number, departure_at),
    CONSTRAINT ck_flights_route CHECK (origin_code <> destination_code),
    CONSTRAINT ck_flights_times CHECK (arrival_at > departure_at)
);

-- a busca é sempre por rota + janela de partida
CREATE INDEX ix_flights_route_departure ON flights (origin_code, destination_code, departure_at);

-- Assentos e preço por voo e classe. O preço é fixo por classe tarifária (não varia com a demanda).
CREATE TABLE seat_inventory (
    id              UUID          PRIMARY KEY,
    flight_id       UUID          NOT NULL REFERENCES flights (id) ON DELETE CASCADE,
    fare_class_code VARCHAR(20)   NOT NULL REFERENCES fare_classes (code),
    total_seats     INT           NOT NULL,
    available_seats INT           NOT NULL,
    price_amount    NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,  -- ISO 4217
    CONSTRAINT uq_seat_inventory_flight_fare UNIQUE (flight_id, fare_class_code),
    -- rede de segurança para a fase 2: nenhum update deixa o disponível negativo ou acima do total
    CONSTRAINT ck_seat_inventory_seats CHECK (total_seats > 0 AND available_seats BETWEEN 0 AND total_seats),
    CONSTRAINT ck_seat_inventory_price CHECK (price_amount >= 0),
    CONSTRAINT ck_seat_inventory_currency CHECK (currency ~ '^[A-Z]{3}$')
);
