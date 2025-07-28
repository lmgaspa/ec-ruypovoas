package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class PixService(
    @Value("\${mercadopago.token}") private val token: String
) {
    fun createPixPayment(name: String, email: String, amount: Double): JsonNode {
        val url = "https://api.mercadopago.com/v1/payments"

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        val body = mapOf(
            "transaction_amount" to amount,
            "description" to "Compra de livros",
            "payment_method_id" to "pix",
            "payer" to mapOf("email" to email)
        )

        val request = HttpEntity(body, headers)
        val response = RestTemplate().postForEntity(url, request, JsonNode::class.java)
        val pixResponse = response.body ?: throw RuntimeException("Erro ao gerar pagamento Pix")

        if (!pixResponse.has("point_of_interaction")) {
            throw IllegalStateException("Resposta inválida do Mercado Pago.")
        }

        return pixResponse
    }
}
