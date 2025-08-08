CREATE TABLE order_items (
    id BIGINT PRIMARY KEY,
    book_id VARCHAR(255) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    quantity INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    order_id BIGINT NOT NULL,
    image_url TEXT
);