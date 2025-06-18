package com.luizgasparetto.backend1.controller

import com.luizgasparetto.backend1.dto.PixPagamentoConfirmadoEvent
import com.luizgasparetto.backend1.dto.PixRequestDto
import com.luizgasparetto.backend1.dto.PurchaseMessage
import com.luizgasparetto.backend1.service.StockService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient

@RestController
@RequestMapping("/api/pix")
class PixController(
    @Value("\${mercadopago.token}") private val token: String,
    private val stockService: StockService
) {

    private val webClient = WebClient.builder()
        .baseUrl("https://api.mercadopago.com/v1/payments")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .build()

    @PostMapping("/criar")
    fun criarPix(@RequestBody dto: PixRequestDto): Map<String, Any?> {
        val body = mapOf(
            "transaction_amount" to dto.valor,
            "description" to "Compra de livros AG",
            "payment_method_id" to "pix",
            "payer" to mapOf(
                "email" to dto.email,
                "first_name" to dto.nome,
                "last_name" to dto.sobrenome
            )
        )

        val response = webClient.post()
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: return emptyMap()

        val pointOfInteraction = (response["point_of_interaction"] as? Map<*, *>)?.get("transaction_data") as? Map<*, *>

        return mapOf(
            "idPagamento" to response["id"],
            "qrCode" to pointOfInteraction?.get("qr_code"),
            "qrCodeBase64" to pointOfInteraction?.get("qr_code_base64"),
            "status" to response["status"]
        )
    }

    @PostMapping("/confirm")
    fun confirmPayment(@RequestBody event: PixPagamentoConfirmadoEvent): ResponseEntity<String> {
        if (event.status == "paid") {
            val purchaseMessage = PurchaseMessage(
                firstName = event.nome,
                lastName = event.sobrenome,
                cpf = event.cpf,
                country = event.country,
                cep = event.cep,
                address = event.address,
                number = event.number,
                complement = event.complement,
                district = event.district,
                city = event.city,
                state = event.state,
                phone = event.phone,
                email = event.email,
                note = event.note,
                delivery = event.delivery,
                payment = event.payment,
                valor = event.valor,
                cartItems = event.cartItems
            )
            stockService.sendPurchase(purchaseMessage)
            return ResponseEntity.ok("Mensagem enviada com sucesso")
        }
        return ResponseEntity.badRequest().body("Pagamento não confirmado")
    }
}
