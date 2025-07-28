package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.dto.CheckoutRequest
import com.luizgasparetto.backend.monolito.dto.CheckoutResponse
import com.luizgasparetto.backend.monolito.models.Order
import com.luizgasparetto.backend.monolito.models.OrderItem
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class CheckoutService(
    private val objectMapper: ObjectMapper,
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    @Value("\${mercadopago.token}") private val token: String
) {

    fun processCheckout(request: CheckoutRequest): CheckoutResponse {
        val totalAmount = calculateTotalAmount(request)
        val url = "https://api.mercadopago.com/v1/payments"

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
            set("X-Idempotency-Key", UUID.randomUUID().toString())
        }

        val body = mapOf(
            "transaction_amount" to totalAmount,
            "description" to "Compra de livros",
            "payment_method_id" to "pix",
            "payer" to mapOf("email" to request.email)
        )

        val httpEntity = HttpEntity(body, headers)
        val restTemplate = RestTemplate()
        val response: ResponseEntity<JsonNode> = try {
            restTemplate.postForEntity(url, httpEntity, JsonNode::class.java)
        } catch (e: Exception) {
            println("Erro ao se comunicar com Mercado Pago: ${e.message}")
            throw RuntimeException("Erro ao processar pagamento com Mercado Pago: ${e.message}", e)
        }

        val pixResponse = response.body ?: throw RuntimeException("Erro ao gerar pagamento: resposta vazia")

        val qrCode = pixResponse["point_of_interaction"]?.get("transaction_data")?.get("qr_code")?.asText()
            ?: throw IllegalStateException("QR Code não encontrado na resposta do Mercado Pago")
        val qrCodeBase64 = pixResponse["point_of_interaction"]?.get("transaction_data")?.get("qr_code_base64")?.asText()
            ?: throw IllegalStateException("QR Code Base64 não encontrado na resposta do Mercado Pago")

        val order = Order(
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            address = request.address,
            city = request.city,
            state = request.state,
            cep = request.cep,
            total = totalAmount,
            shipping = request.shipping,
            qrCode = qrCode,
            qrCodeBase64 = qrCodeBase64,
            items = mutableListOf()
        )

        val orderItems = request.cartItems.map { cartItem ->
            OrderItem(
                bookId = cartItem.id,
                title = cartItem.title,
                quantity = cartItem.quantity,
                price = cartItem.price,
                order = order
            )
        }.toMutableList()

        order.items = orderItems

        orderRepository.save(order)

        request.cartItems.forEach { cartItem ->
            bookService.updateStock(cartItem.id, cartItem.quantity)
        }

        return CheckoutResponse(
            qrCode = qrCode,
            qrCodeBase64 = qrCodeBase64,
            message = "Pedido gerado com sucesso",
            orderId = order.id.toString()
        )
    }

    private fun calculateTotalAmount(request: CheckoutRequest): Double {
        val totalBooks = request.cartItems.sumOf { it.price * it.quantity }
        return totalBooks + request.shipping
    }
}
