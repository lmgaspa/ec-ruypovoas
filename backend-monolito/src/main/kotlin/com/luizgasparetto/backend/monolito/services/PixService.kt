package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class PixService(
    private val objectMapper: ObjectMapper
) {
    @Value("\${MERCADO_PAGO_TOKEN}")
    private lateinit var mercadoPagoToken: String

    private val restTemplate = RestTemplate()

    fun createPixPayment(firstName: String, email: String, amount: Double): JsonNode {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(mercadoPagoToken)

        headers.add("X-Idempotency-Key", java.util.UUID.randomUUID().toString())

        val body = mapOf(
            "transaction_amount" to amount,
            "description" to "Compra de livros",
            "payment_method_id" to "pix",
            "payer" to mapOf(
                "email" to email,
                "first_name" to firstName
            )
        )

        val request = HttpEntity(body, headers)

        val response = restTemplate.postForEntity(
            "https://api.mercadopago.com/v1/payments",
            request,
            String::class.java
        )

        if (response.statusCode != HttpStatus.CREATED && response.statusCode != HttpStatus.OK) {
            throw IllegalStateException("Erro ao criar pagamento PIX: ${response.body}")
        }

        return objectMapper.readTree(response.body)
    }

}
