📚 Ecommerce AG Books - Backend

This is the backend service for the AG Books online store. It handles:

PIX payment creation

Payment confirmation via MercadoPago Webhook

Dispatching messages to other microservices using Apache Kafka

🚀 Technologies Used

Kotlin & Spring Boot

Gradle

Kafka (CloudAMQP)

PostgreSQL

Java Mail Sender

Heroku (deployment)

🧱 Microservices Architecture

🟦 Backend 1 (PIX Service)

Receives frontend requests

Registers PIX payments

Listens to MercadoPago webhook

Sends payment confirmation messages via Kafka

Endpoint:

POST /api/pix/criar

Request Body:

{
  "nome": "John",
  "sobrenome": "Doe",
  "cpf": "12345678900",
  "country": "Brazil",
  "cep": "45600-000",
  "address": "Main St",
  "number": "123",
  "complement": "Apt 1",
  "district": "Downtown",
  "city": "Itabuna",
  "state": "BA",
  "phone": "(73)3212-1229",
  "email": "john.doe@email.com",
  "note": "Deliver fast",
  "delivery": "Normal",
  "valor": 49.90,
  "payment": "pix",
  "cartItems": [
    { "id": "extase", "quantity": 1 },
    { "id": "regressantes", "quantity": 1 },
    { "id": "sempre", "quantity": 1 }
  ]
}

Response:

{
  "idPagamento": "1234567890",
  "qrCode": "...",
  "qrCodeBase64": "...",
  "status": "pending"
}

🟦 Backend 2 (Email Service)

Listens to Kafka topic

Processes the purchase

Sends email notifications

Persists data in PostgreSQL

⚙️ How It Works

1. PIX Payment Creation

Frontend sends data to POST /api/pix/criar

MercadoPago responds with QR code and pending status

2. Webhook Listener

Endpoint: POST /webhook/pix

Called automatically by MercadoPago when payment status changes

If status is "approved", full data is sent to Kafka

3. Kafka Message Format

Event: PixPagamentoConfirmadoEvent

Topic: pix-confirmed-topic

Includes user data, payment value, and cart items

4. Email Notification

Microservice 2 listens to the topic

Sends formatted email to the admin

Saves the purchase in PostgreSQL

✅ Running Locally

Prerequisites

Java 17+

Docker (for Kafka & PostgreSQL)

Gradle

Startup Steps

# Start Kafka & PostgreSQL
docker-compose up -d

# Run Backend 1 (PIX)
cd ./backend1
./gradlew bootRun

# Run Backend 2 (Email)
cd ../backend2
./gradlew bootRun

🔧 Environment Variables

Configure via .env or export:

MERCADOPAGO_TOKEN=your_token_here
SPRING_KAFKA_BOOTSTRAP_SERVERS=...
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
SPRING_DATASOURCE_URL=...

📬 Contact

Made with ❤️ by Luiz Gasparetto#ecommerceag #ecommerce-ag-books