    package com.luizgasparetto.backend.monolito.services
    
    import com.fasterxml.jackson.databind.ObjectMapper
    import com.luizgasparetto.backend.monolito.dto.CheckoutRequest
    import com.luizgasparetto.backend.monolito.dto.CheckoutResponse
    import com.luizgasparetto.backend.monolito.models.Order
    import com.luizgasparetto.backend.monolito.models.OrderItem
    import com.luizgasparetto.backend.monolito.models.OrderStatus
    import com.luizgasparetto.backend.monolito.repositories.OrderRepository
    import org.springframework.beans.factory.annotation.Value
    import org.springframework.http.*
    import org.springframework.stereotype.Service
    import org.springframework.web.client.RestTemplate
    import org.springframework.beans.factory.annotation.Qualifier
    import org.springframework.transaction.annotation.Transactional
    import java.math.BigDecimal
    import java.time.OffsetDateTime
    import java.util.*
    
    @Service
    class CheckoutService(
        private val efiAuthService: EfiAuthService,
        private val objectMapper: ObjectMapper,
        private val orderRepository: OrderRepository,
        private val bookService: BookService,
        private val pixWatcher: PixWatcher,
        private val cardWatcher: CardWatcher,
        private val emailService: EmailService,
        @Qualifier("efiRestTemplate") private val restTemplate: RestTemplate,
        @Value("\${efi.pix.sandbox}") private val sandbox: Boolean,
        @Value("\${efi.pix.chave}") private val chavePix: String,
        @Value("\${checkout.reserve.ttl-seconds:900}") private val reserveTtlSeconds: Long
    ) {
        private val log = org.slf4j.LoggerFactory.getLogger(CheckoutService::class.java)
    
        fun processCheckout(request: CheckoutRequest): CheckoutResponse {
            // 0) valida estoque
            request.cartItems.forEach { item -> bookService.validateStock(item.id, item.quantity) }
    
            val totalAmount = calculateTotalAmount(request)
            val txid = UUID.randomUUID().toString().replace("-", "").take(35)
    
            // 1) cria pedido
            val order = createOrderTx(request, totalAmount, txid)
    
            // 2) reserva estoque
            reserveItemsTx(order, reserveTtlSeconds)
    
            return when (request.payment?.lowercase()) {
                "pix" -> processPix(order, totalAmount, request, txid)
                "card" -> processCard(order, totalAmount, request, txid)
                else -> throw IllegalArgumentException("Método de pagamento inválido: ${request.payment}")
            }
        }
    
        // ------------------- PIX -------------------
        private fun processPix(order: Order, totalAmount: BigDecimal, request: CheckoutRequest, txid: String): CheckoutResponse {
            val qr = try {
                createPixQr(totalAmount, request, txid)
            } catch (e: Exception) {
                log.error("Falha ao criar QR Pix. orderId={}, txid={}", order.id, txid, e)
                releaseReservationTx(order.id!!)
                throw e
            }
    
            updateOrderWithQrTx(order.id!!, qr.qrCode, qr.qrCodeBase64)
            pixWatcher.watch(txid, requireNotNull(order.reserveExpiresAt).toInstant())
    
            log.info("CHECKOUT PIX OK: orderId={}, txid={}", order.id, txid)
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
            val cardResult = try {
                createCardCharge(totalAmount, request, txid)
            } catch (e: IllegalStateException) {
                releaseReservationTx(order.id!!)
                runCatching {
                    emailService.sendClientCardDeclined(order)
                    emailService.sendAuthorCardDeclined(order)
                }
                throw e
            }

            if (cardResult.paid) {
                order.paid = true
                order.status = OrderStatus.CONFIRMADO
                order.chargeId = cardResult.chargeId
                orderRepository.save(order)
            } else {
                // 🔹 caso AUTHORIZED ou PROCESSING
                order.chargeId = cardResult.chargeId
                orderRepository.save(order)

                // TTL = 5 minutos (ou o que vc configurou em application.yml)
                val expiresAt = requireNotNull(order.reserveExpiresAt).toInstant()
                cardWatcher.watch(cardResult.chargeId!!, expiresAt)
            }

            return CheckoutResponse(
                qrCode = null,
                qrCodeBase64 = null,
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
        data class QrPayload(val qrCode: String, val qrCodeBase64: String)
        data class CardChargeResult(val paid: Boolean, val chargeId: String?)
    
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
                if (cpfNum != null) put("devedor", mapOf("nome" to "${request.firstName} ${request.lastName}", "cpf" to cpfNum))
            }
    
            val cobrancaResp = restTemplate.exchange(
                "$baseUrl/v2/cob/$txid",
                HttpMethod.PUT,
                HttpEntity(cobrancaBody, headers),
                String::class.java
            )
            require(cobrancaResp.statusCode.is2xxSuccessful)
    
            val locId = objectMapper.readTree(cobrancaResp.body).path("loc").path("id").asText()
            val qrResp = restTemplate.exchange(
                "$baseUrl/v2/loc/$locId/qrcode",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java
            )
            require(qrResp.statusCode.is2xxSuccessful)
    
            val qrJson = objectMapper.readTree(qrResp.body)
            return QrPayload(qrJson.path("qrcode").asText(), qrJson.path("imagemQrcode").asText())
        }
    
        private fun createCardCharge(totalAmount: BigDecimal, request: CheckoutRequest, txid: String): CardChargeResult {
            val baseUrl = if (sandbox) "https://sandbox.efi.com.br" else "https://api.efi.com.br"
            val token = efiAuthService.getAccessToken()
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                setBearerAuth(token)
            }
            val cardToken = request.cardToken ?: error("Token do cartão não enviado")
    
            val body = mapOf(
                "payment" to mapOf(
                    "credit_card" to mapOf(
                        "card_token" to cardToken,
                        "installments" to request.installments
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
            require(response.statusCode.is2xxSuccessful)
    
            val json = objectMapper.readTree(response.body)
            val status = json.path("status").asText().uppercase()
            val chargeId = json.path("charge_id").asText()
    
            return when (status) {
                "PAID", "APPROVED" -> CardChargeResult(true, chargeId.takeIf { it.isNotBlank() })
                "DECLINED", "FAILED" -> throw IllegalStateException("Pagamento recusado pelo emissor do cartão")
                else -> CardChargeResult(false, chargeId.takeIf { it.isNotBlank() })
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
                paymentMethod = request.payment
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
            order.items.forEach { item -> bookService.reserveOrThrow(item.bookId, item.quantity) }
            order.status = OrderStatus.RESERVADO
            order.reserveExpiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds)
            orderRepository.save(order)
        }
    
        @Transactional
        fun releaseReservationTx(orderId: Long) {
            val order = orderRepository.findById(orderId).orElseThrow()
            if (order.status == OrderStatus.RESERVADO && !order.paid) {
                order.items.forEach { item -> bookService.release(item.bookId, item.quantity) }
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
            val totalBooks = request.cartItems.sumOf { it.price.toBigDecimal() * BigDecimal(it.quantity) }
            return totalBooks + request.shipping.toBigDecimal()
        }
    }
