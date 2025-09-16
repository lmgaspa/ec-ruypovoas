// src/main/kotlin/com/luizgasparetto/backend/monolito/services/CheckoutService.kt
package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.dto.CheckoutRequest
import com.luizgasparetto.backend.monolito.dto.CheckoutResponse
import com.luizgasparetto.backend.monolito.models.Order
import com.luizgasparetto.backend.monolito.models.OrderItem
import com.luizgasparetto.backend.monolito.models.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CheckoutService(
    private val efiAuthService: EfiAuthService,
    private val objectMapper: ObjectMapper,
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val pixWatcher: PixWatcher,
    private val cardWatcher: CardWatcher,
    private val emailService: EmailService,
    private val paymentProcessor: PaymentProcessor,
    @Qualifier("efiRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${efi.pix.sandbox}") private val sandbox: Boolean,
    @Value("\${efi.pix.chave}") private val chavePix: String,
    @Value("\${checkout.reserve.ttl-seconds:900}") private val reserveTtlSeconds: Long
) {
    private val log = LoggerFactory.getLogger(CheckoutService::class.java)

    fun processCheckout(request: CheckoutRequest): CheckoutResponse {
        // 1) valida estoque
        request.cartItems.forEach { item ->
            bookService.validateStock(item.id, item.quantity)
        }

        // 2) total e txid (Efí exige 26–35 alfanuméricos)
        val totalAmount = calculateTotalAmount(request)
        val txid = "PED" + UUID.randomUUID().toString().replace("-", "").uppercase().take(32) // 35 chars

        // 3) cria pedido e reserva
        val order = createOrderTx(request, totalAmount, txid)
        reserveItemsTx(order, reserveTtlSeconds)

        // 4) roteia por método
        return when (request.payment.lowercase()) {
            "pix"  -> processPix(order, totalAmount, request, txid)
            "card" -> processCard(order, totalAmount, request, txid)
            else   -> throw IllegalArgumentException("Método de pagamento inválido: ${request.payment}")
        }
    }

    // ------------------- PIX -------------------
    private fun processPix(order: Order, totalAmount: BigDecimal, request: CheckoutRequest, txid: String): CheckoutResponse {
        log.info("Iniciando cobrança PIX orderId={}, txid={}, total={}", order.id, txid, totalAmount)

        val qr = try {
            createPixQr(totalAmount, request, txid)
        } catch (e: Exception) {
            log.error("Falha ao criar QR Pix txid={}, msg={}", txid, e.message)
            releaseReservationTx(order.id!!)
            throw e
        }

        updateOrderWithQrTx(order.id!!, qr.qrCode, qr.qrCodeBase64)
        val expiresAt = requireNotNull(order.reserveExpiresAt).toInstant()
        log.info("PIX watcher iniciado txid={}, expiraEm={}", txid, order.reserveExpiresAt)
        pixWatcher.watch(txid, expiresAt)

        return CheckoutResponse(
            qrCode = qr.qrCode,
            qrCodeBase64 = qr.qrCodeBase64,
            message = "Pedido gerado com sucesso via Pix",
            orderId = order.id.toString(),
            txid = txid,
            reserveExpiresAt = order.reserveExpiresAt?.toString(),
            ttlSeconds = reserveTtlSeconds
        )
    }

    // ------------------- CARTÃO -------------------
    private fun processCard(order: Order, totalAmount: BigDecimal, request: CheckoutRequest, txid: String): CheckoutResponse {
        log.info("Iniciando cobrança CARTÃO orderId={}, total={}", order.id, totalAmount)

        val cardResult = try {
            createCardCharge(totalAmount, request, txid)
        } catch (e: IllegalStateException) {
            // Recusado pelo emissor
            log.warn("Pagamento cartão recusado orderId={}, motivo={}", order.id, e.message)
            releaseReservationTx(order.id!!)
            runCatching {
                emailService.sendClientCardDeclined(order)
                emailService.sendAuthorCardDeclined(order)
            }
            throw e
        }

        // sempre salvar chargeId para correlacionar em webhook/polling
        if (cardResult.chargeId != null) {
            order.chargeId = cardResult.chargeId
            orderRepository.save(order)
        }

        if (cardResult.paid) {
            // Centraliza notificação/e-mails no PaymentProcessor
            paymentProcessor.markPaidIfNeededByChargeId(cardResult.chargeId!!, "PAID")
        } else {
            val expiresAt = requireNotNull(order.reserveExpiresAt).toInstant()
            log.info("CARD watcher iniciado chargeId={}, expiraEm={}", cardResult.chargeId, order.reserveExpiresAt)
            cardWatcher.watch(cardResult.chargeId!!, expiresAt)
        }

        return CheckoutResponse(
            message = if (cardResult.paid) "Pagamento aprovado" else "Pagamento em processamento",
            orderId = order.id.toString(),
            txid = txid,
            reserveExpiresAt = order.reserveExpiresAt?.toString(),
            ttlSeconds = reserveTtlSeconds,
            paid = cardResult.paid,
            chargeId = cardResult.chargeId
        )
    }

    // ------------------- HELPERS -------------------
    private data class QrPayload(val qrCode: String, val qrCodeBase64: String)
    private data class CardChargeResult(val paid: Boolean, val chargeId: String?)

    private fun createPixQr(totalAmount: BigDecimal, request: CheckoutRequest, txid: String): QrPayload {
        val baseUrl = if (sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val token = efiAuthService.getAccessToken()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        val cpfNum = request.cpf.replace(Regex("[^\\d]"), "").takeIf { it.isNotBlank() }
        val cobrancaBody = buildMap<String, Any> {
            put("calendario", mapOf("expiracao" to reserveTtlSeconds.toInt()))
            put("valor", mapOf("original" to totalAmount.setScale(2).toPlainString()))
            put("chave", chavePix)
            put("solicitacaoPagador", "Pedido $txid")
            if (cpfNum != null) {
                put("devedor", mapOf("nome" to "${request.firstName} ${request.lastName}", "cpf" to cpfNum))
            }
        }

        val cobrancaResp = restTemplate.exchange(
            "$baseUrl/v2/cob/$txid",
            HttpMethod.PUT,
            HttpEntity(cobrancaBody, headers),
            String::class.java
        )
        require(cobrancaResp.statusCode.is2xxSuccessful) { "Erro ao criar cobrança Pix: ${cobrancaResp.statusCode}" }

        val locId = objectMapper.readTree(cobrancaResp.body).path("loc").path("id").asText()
        val qrResp = restTemplate.exchange(
            "$baseUrl/v2/loc/$locId/qrcode",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )
        require(qrResp.statusCode.is2xxSuccessful) { "Erro ao obter QRCode Pix: ${qrResp.statusCode}" }

        val qrJson = objectMapper.readTree(qrResp.body)
        return QrPayload(
            qrCode = qrJson.path("qrcode").asText(),
            qrCodeBase64 = qrJson.path("imagemQrcode").asText()
        )
    }

    private fun createCardCharge(totalAmount: BigDecimal, request: CheckoutRequest, txid: String): CardChargeResult {
        val baseUrl = if (sandbox) "https://sandbox.efi.com.br" else "https://api.efi.com.br"
        val token = efiAuthService.getAccessToken()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        val cardToken = request.cardToken ?: error("cardToken é obrigatório para pagamento com cartão")
        val installments = request.installments ?: 1

        val body = mapOf(
            "payment" to mapOf(
                "credit_card" to mapOf(
                    "card_token" to cardToken,
                    "installments" to installments
                )
            ),
            "items" to request.cartItems.map {
                mapOf(
                    "name" to it.title,
                    "value" to (it.price * 100).toInt(),
                    "amount" to it.quantity
                )
            },
            "metadata" to mapOf("txid" to txid),
            "amount" to mapOf("value" to (totalAmount * BigDecimal(100)).toInt())
        )

        val response = restTemplate.exchange(
            "$baseUrl/v1/charge/card",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        require(response.statusCode.is2xxSuccessful) { "Falha ao criar cobrança cartão: ${response.statusCode}" }

        val json = objectMapper.readTree(response.body)
        val status = json.path("status").asText().uppercase()
        val chargeId = json.path("charge_id").asText().takeIf { it.isNotBlank() }

        log.info("CARD CHARGE criado status={}, chargeId={}", status, chargeId)

        return when (status) {
            "PAID", "APPROVED" -> CardChargeResult(true, chargeId)
            "DECLINED", "FAILED" -> throw IllegalStateException("Pagamento recusado pelo emissor do cartão")
            else -> CardChargeResult(false, chargeId) // AUTHORIZED/PROCESSING/ETC
        }
    }

    // ------------------- TRANSACIONAIS -------------------
    @Transactional
    fun createOrderTx(request: CheckoutRequest, totalAmount: BigDecimal, txid: String): Order {
        val order = Order(
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            cpf = request.cpf,
            number = request.number,
            complement = request.complement,
            district = request.district,
            address = request.address,
            city = request.city,
            state = request.state,
            cep = request.cep,
            phone = request.phone,
            note = request.note,
            total = totalAmount,
            shipping = request.shipping.toBigDecimal(),
            paid = false,
            txid = txid,
            items = mutableListOf(),
            status = OrderStatus.CRIADO,
            paymentMethod = request.payment,
            installments = request.installments ?: 1
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

        return orderRepository.save(order)
    }

    @Transactional
    fun reserveItemsTx(order: Order, ttlSeconds: Long) {
        order.items.forEach { item ->
            bookService.reserveOrThrow(item.bookId, item.quantity)
        }
        order.status = OrderStatus.RESERVADO
        order.reserveExpiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds)
        orderRepository.save(order)
    }

    @Transactional
    fun releaseReservationTx(orderId: Long) {
        val order = orderRepository.findWithItemsById(orderId)
            ?: throw IllegalArgumentException("Pedido não encontrado: $orderId")

        if (order.status == OrderStatus.RESERVADO && !order.paid) {
            order.items.forEach { item ->
                bookService.release(item.bookId, item.quantity)
            }
            order.status = OrderStatus.RESERVA_EXPIRADA
            order.reserveExpiresAt = null
            orderRepository.save(order)
        }
    }

    @Transactional
    fun updateOrderWithQrTx(orderId: Long, qrCode: String, qrB64: String) {
        val order = orderRepository.findById(orderId).orElseThrow()
        order.qrCode = qrCode
        order.qrCodeBase64 = qrB64
        orderRepository.save(order)
    }

    private fun calculateTotalAmount(request: CheckoutRequest): BigDecimal {
        val totalBooks = request.cartItems.fold(BigDecimal.ZERO) { acc, it ->
            acc + (it.price.toBigDecimal() * BigDecimal(it.quantity))
        }
        return totalBooks + request.shipping.toBigDecimal()
    }
}
