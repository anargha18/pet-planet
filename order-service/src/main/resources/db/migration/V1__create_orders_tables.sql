CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    pet_id UUID NOT NULL,
    pet_name VARCHAR(255) NOT NULL,
    pet_species VARCHAR(32) NOT NULL,
    pet_breed VARCHAR(255) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity = 1),
    subtotal NUMERIC(19,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_pet_id ON order_items(pet_id);
