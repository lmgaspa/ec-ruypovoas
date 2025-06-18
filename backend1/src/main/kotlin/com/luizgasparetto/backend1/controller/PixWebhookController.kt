package com.luizgasparetto.backend1.controller

import com.luizgasparetto.backend1.dto.PixPagamentoConfirmadoEvent
import com.luizgasparetto.backend1.dto.PixRequestDto
import com.luizgasparetto.backend1.service.KafkaProducerService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@RestController
@RequestMapping("/webhook/pix")
class PixWebhookController(
    @Value("\${mercadopago.token}") private val token: String,
    private val kafkaProducerService: KafkaProducerService
) {

    private val webClient = WebClient.builder()
        .baseUrl("https://api.mercadopago.com/v1/payments")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .build()

    val pedidosPendentes = mutableMapOf<String, PixRequestDto>()

    @PostMapping("/registrar")
    fun registrarPedido(@RequestBody dto: PixRequestDto): Map<String, String> {
        pedidosPendentes[dto.email] = dto
        return mapOf("status" to "registrado")
    }

    @PostMapping
    fun receberWebhook(@RequestBody payload: Map<String, Any?>) {
        val tipo = payload["type"]
        if (tipo == "payment") {
            val data = payload["data"] as? Map<*, *>
            val idPagamento = data?.get("id")?.toString() ?: return

            val response = webClient.get()
                .uri("/$idPagamento")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() ?: return

            if (response["status"] == "approved") {
                val payer = response["payer"] as? Map<*, *> ?: return
                val email = payer["email"]?.toString() ?: return
                val nome = listOfNotNull(payer["first_name"], payer["last_name"]).joinToString(" ")

                val pedido = pedidosPendentes[email] ?: return

                val event = PixPagamentoConfirmadoEvent(
                    idPagamento = idPagamento,
                    status = "approved",
                    valor = BigDecimal.valueOf((response["transaction_amount"] as Number).toDouble()),
                    email = email,
                    nome = nome,
                    sobrenome = pedido.sobrenome,
                    cpf = pedido.cpf,
                    country = pedido.country,
                    cep = pedido.cep,
                    address = pedido.address,
                    number = pedido.number,
                    complement = pedido.complement,
                    district = pedido.district,
                    city = pedido.city,
                    state = pedido.state,
                    phone = pedido.phone,
                    note = pedido.note,
                    delivery = pedido.delivery,
                    payment = pedido.payment,
                    cartItems = pedido.cartItems
                )

                kafkaProducerService.enviarPagamentoConfirmado(event)
                pedidosPendentes.remove(email)
            }
        }
    }
}
