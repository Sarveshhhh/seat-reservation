CREATE TABLE IF NOT EXISTS shows (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_paise BIGINT NOT NULL CHECK (price_paise > 0),
    total_seats INT NOT NULL CHECK (total_seats > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS seats (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_show_seat UNIQUE (show_id, seat_number),
    CONSTRAINT chk_seat_status CHECK (status IN ('AVAILABLE', 'CONFIRMED'))
);

CREATE INDEX IF NOT EXISTS idx_seats_show_lookup ON seats(show_id, seat_number);

CREATE TABLE IF NOT EXISTS reservations (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(100) NOT NULL,
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_reservation_status CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_reservations_show_user ON reservations(show_id, user_id, status);

CREATE TABLE IF NOT EXISTS reservation_seats (
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats(id),
    PRIMARY KEY (reservation_id, seat_id)
);
