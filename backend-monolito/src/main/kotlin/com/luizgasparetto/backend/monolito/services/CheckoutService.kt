package com.luizgasparetto.backend.monolito.services

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
import java.math.BigDecimal
import java.util.*

@Service
class CheckoutService(
    private val efiAuthService: EfiAuthService,
    private val objectMapper: ObjectMapper,
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val restTemplate: RestTemplate,
    @Value("\${efi.pix.sandbox}") private val sandbox: Boolean,
    @Value("\${efi.pix.chave}") private val chavePix: String
) {

    fun processCheckout(request: CheckoutRequest): CheckoutResponse {
        val totalAmount = calculateTotalAmount(request)
        val txid = UUID.randomUUID().toString().replace("-", "").take(35)

        val order = Order(
            firstName = request.firstName,
            lastName  = request.lastName,
            email     = request.email,
            cpf       = request.cpf,
            number    = request.number,
            complement= request.complement,
            district  = request.district,
            address   = request.address,
            city      = request.city,
            state     = request.state,
            cep       = request.cep,
            phone     = request.phone,
            note      = request.note,
            total     = totalAmount,
            shipping  = request.shipping.toBigDecimal(),
            paid      = false,
            txid      = txid,
            items     = mutableListOf()
        )

        val orderItems = request.cartItems.map {
            OrderItem(
                bookId = it.id,
                title = it.title,
                quantity = it.quantity,
                price = it.price.toBigDecimal(),
                imageUrl = bookService.getImageUrl(it.id),
                order = order
            )
        }.toMutableList()

        order.items = orderItems
        orderRepository.save(order)

        val token = efiAuthService.getAccessToken()

        val cobrancaBody = mapOf(
            "calendario" to mapOf("expiracao" to 3600),
            "devedor" to mapOf(
                "nome" to "${request.firstName} ${request.lastName}",
                "cpf" to request.cpf.replace(Regex("[^\\d]"), "")
            ),
            "valor" to mapOf("original" to "%.2f".format(Locale.US, totalAmount)),
            "chave" to chavePix,
            "solicitacaoPagador" to "Pedido $txid"
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        val baseUrl = if (sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"

        val cobrancaResponse = restTemplate.exchange(
            "$baseUrl/v2/cob/$txid",
            HttpMethod.PUT,
            HttpEntity(cobrancaBody, headers),
            String::class.java
        )

        val cobrancaJson = objectMapper.readTree(cobrancaResponse.body)
        val locId = cobrancaJson["loc"]?.get("id")?.asText()
            ?: throw RuntimeException("Campo loc.id não encontrado na resposta da cobrança")

        val qrResponse = restTemplate.exchange(
            "$baseUrl/v2/loc/$locId/qrcode",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )

        val qrJson = objectMapper.readTree(qrResponse.body)
        val qrCode = qrJson["qrcode"]?.asText()?.takeIf { it.isNotBlank() }
            ?: throw RuntimeException("QR Code não encontrado ou vazio")
        val qrCodeBase64 = qrJson["imagemQrcode"]?.asText() ?: ""

        order.qrCode = qrCode
        order.qrCodeBase64 = qrCodeBase64
        orderRepository.save(order)

        request.cartItems.forEach {
            bookService.updateStock(it.id, it.quantity)
        }

        return CheckoutResponse(
            qrCode = qrCode,
            qrCodeBase64 = qrCodeBase64,
            message = "Pedido gerado com sucesso",
            orderId = order.id.toString(),
            txid = order.txid ?: ""
        )
    }

    private fun calculateTotalAmount(request: CheckoutRequest): BigDecimal {
        val totalBooks = request.cartItems.sumOf {
            it.price.toBigDecimal().multiply(BigDecimal(it.quantity))
        }
        return totalBooks.add(request.shipping.toBigDecimal())
    }
}
