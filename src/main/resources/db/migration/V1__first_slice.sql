CREATE TABLE users (id UUID PRIMARY KEY);
CREATE TABLE roasters (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 200 AND btrim(name) <> ''),
    UNIQUE (id, owner_id)
);
CREATE TABLE coffee_lots (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    roaster_id UUID NOT NULL,
    name TEXT CHECK (char_length(name) BETWEEN 1 AND 200 AND btrim(name) <> ''),
    description TEXT CHECK (char_length(description) BETWEEN 1 AND 4000 AND btrim(description) <> ''),
    roast_profile TEXT CHECK (roast_profile IN ('FILTER', 'ESPRESSO', 'OMNI')),
    origin_components JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(origin_components) = 'array' AND jsonb_array_length(origin_components) <= 50),
    varieties JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(varieties) = 'array' AND jsonb_array_length(varieties) <= 50),
    processing_method TEXT CHECK (char_length(processing_method) BETWEEN 1 AND 200 AND btrim(processing_method) <> ''),
    declared_descriptors JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(declared_descriptors) = 'array' AND jsonb_array_length(declared_descriptors) <= 50),
    UNIQUE (id, owner_id),
    FOREIGN KEY (roaster_id, owner_id) REFERENCES roasters(id, owner_id)
);
CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    coffee_lot_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    purchased_on DATE NOT NULL,
    roasted_on DATE,
    weight_grams INTEGER CHECK (weight_grams > 0),
    price_amount NUMERIC(12,2) CHECK (price_amount BETWEEN 0 AND 9999999999.99),
    price_currency TEXT CHECK (price_currency ~ '^[A-Z]{3}$'),
    state TEXT NOT NULL DEFAULT 'PURCHASED' CHECK (state = 'PURCHASED'),
    UNIQUE (owner_id, idempotency_key),
    FOREIGN KEY (coffee_lot_id, owner_id) REFERENCES coffee_lots(id, owner_id),
    CHECK ((price_amount IS NULL) = (price_currency IS NULL))
);
CREATE INDEX roasters_owner ON roasters(owner_id, id);
CREATE INDEX coffee_lots_owner ON coffee_lots(owner_id, id);
CREATE INDEX purchases_history ON purchases(owner_id, coffee_lot_id, purchased_on DESC, id ASC);
