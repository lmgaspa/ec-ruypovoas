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
import org.springframework.beans.factory.annotation.Qualifier

import org.springframework.transaction.annotation.Transactional

@Service
class CheckoutService(
    private val efiAuthService: EfiAuthService,
    private val objectMapper: ObjectMapper,
    private val orderRepository: OrderRepository,
    private val mapper: ObjectMapper,
    private val bookService: BookService,
    @Qualifier("efiRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${efi.pix.sandbox}") private val sandbox: Boolean,
    @Value("\${efi.pix.chave}") private val chavePix: String
) {
    private val log = org.slf4j.LoggerFactory.getLogger(CheckoutService::class.java)

    fun processCheckout(request: CheckoutRequest): CheckoutResponse {
        request.cartItems.forEach { item ->
            bookService.validateStock(item.id, item.quantity)
        }
        
        val totalAmount = calculateTotalAmount(request)
        val txid = java.util.UUID.randomUUID().toString().replace("-", "").take(35)

        // ----- TX 1: cria pedido + itens
        val order = createOrderTx(request, totalAmount, txid)

        // ----- Chama Efí (fora de TX)
        val baseUrl = if (sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val token = efiAuthService.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }
        val cpfNum = request.cpf.replace(Regex("[^\\d]"), "").takeIf { it.isNotBlank() }
        val cobrancaBody = buildMap<String, Any> {
            put("calendario", mapOf("expiracao" to 3600))
            put("valor", mapOf("original" to totalAmount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()))
            put("chave", chavePix)
            put("solicitacaoPagador", "Pedido $txid")
            if (cpfNum != null) put("devedor", mapOf("nome" to "${request.firstName} ${request.lastName}", "cpf" to cpfNum))
        }

        val cobrancaResp = restTemplate.exchange(
            "$baseUrl/v2/cob/$txid", HttpMethod.PUT, HttpEntity(cobrancaBody, headers), String::class.java
        )
        require(cobrancaResp.statusCode.is2xxSuccessful) { "Falha ao criar cobrança: ${cobrancaResp.statusCode}" }

        val locId = objectMapper.readTree(cobrancaResp.body).path("loc").path("id").asText(null)
            ?: error("Campo loc.id não encontrado na resposta da cobrança")

        val qrResp = restTemplate.exchange(
            "$baseUrl/v2/loc/$locId/qrcode", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java
        )
        require(qrResp.statusCode.is2xxSuccessful) { "Falha ao obter QR Code: ${qrResp.statusCode}" }

        val qrJson = objectMapper.readTree(qrResp.body)
        val qrCode = qrJson.path("qrcode").asText().takeIf { it.isNotBlank() } ?: error("QR Code não encontrado ou vazio")
        val qrCodeBase64 = qrJson.path("imagemQrcode").asText("")

        // ----- TX 2: grava QR no pedido (+ opcional: baixa estoque)
        updateOrderWithQrAndStockTx(order.id!!, qrCode, qrCodeBase64)

        log.info("CHECKOUT OK: orderId={}, txid={}, qrLen={}, imgLen={}", order.id, txid, qrCode.length, qrCodeBase64.length)
        return CheckoutResponse(
            qrCode = qrCode,
            qrCodeBase64 = qrCodeBase64,
            message = "Pedido gerado com sucesso",
            orderId = order.id.toString(),
            txid = txid
        )
    }

    @Transactional
    fun createOrderTx(request: CheckoutRequest, totalAmount: BigDecimal, txid: String): Order {
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

        order.items = request.cartItems.map {
            OrderItem(
                bookId = it.id,
                title = it.title,
                quantity = it.quantity,
                price = it.price.toBigDecimal(),
                imageUrl = bookService.getImageUrl(it.id),
                order = order
            )
        }.toMutableList()

        val saved = orderRepository.save(order)
        log.info("TX1: order salvo id={}, txid={}", saved.id, txid)
        return saved
    }

    @Transactional
    fun updateOrderWithQrAndStockTx(orderId: Long, qrCode: String, qrB64: String) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalStateException("Order $orderId não encontrado") }

        order.qrCode = qrCode
        order.qrCodeBase64 = qrB64
        orderRepository.save(order)

        log.info("TX2: QR gravado. orderId={}", orderId)
    }

    private fun calculateTotalAmount(request: CheckoutRequest): BigDecimal {
        val totalBooks = request.cartItems.sumOf { it.price.toBigDecimal() * BigDecimal(it.quantity) }
        return totalBooks + request.shipping.toBigDecimal()
    }
}
