-- V1__create_books.sql
-- Criação da tabela books

CREATE TABLE IF NOT EXISTS books (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    image_url TEXT NOT NULL,
    description TEXT,
    author VARCHAR(255),
    category VARCHAR(100),
    stock INT NOT NULL DEFAULT 0
);
