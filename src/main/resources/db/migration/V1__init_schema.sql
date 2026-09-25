CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE rooms (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    capacity   INTEGER      NOT NULL,
    floor      INTEGER      NOT NULL,
    CONSTRAINT chk_rooms_capacity CHECK (capacity > 0)
);

CREATE TABLE bookings (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT       NOT NULL REFERENCES rooms (id),
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    start_time TIMESTAMPTZ  NOT NULL,
    end_time   TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    CONSTRAINT chk_bookings_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT chk_bookings_time CHECK (start_time < end_time),
    -- Safety net against concurrent inserts: no two ACTIVE bookings of the same room may overlap.
    -- '[)' range means back-to-back bookings (10:00-11:00, 11:00-12:00) are allowed.
    CONSTRAINT excl_bookings_no_overlap EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status = 'ACTIVE')
);

CREATE INDEX idx_bookings_room_time ON bookings (room_id, start_time, end_time);
CREATE INDEX idx_bookings_user ON bookings (user_id);
