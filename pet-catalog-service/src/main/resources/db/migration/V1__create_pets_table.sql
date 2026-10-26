CREATE TABLE pets (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    species VARCHAR(20) NOT NULL,
    breed VARCHAR(120) NOT NULL,
    age INTEGER NOT NULL CHECK (age >= 0),
    gender VARCHAR(20) NOT NULL,
    price NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    description VARCHAR(2000) NOT NULL,
    availability_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pets_species ON pets (species);
CREATE INDEX idx_pets_breed_lower ON pets (LOWER(breed));
CREATE INDEX idx_pets_price ON pets (price);
CREATE INDEX idx_pets_availability_status ON pets (availability_status);
