package com.luizgasparetto.backend.monolito.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.models.OrderStatus
import com.luizgasparetto.backend.monolito.models.WebhookEvent
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.repositories.WebhookEventRepository
import com.luizgasparetto.backend.monolito.services.EmailService
import com.luizgasparetto.backend.monolito.services.OrderEventsPublisher
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/efi-webhook")
class EfiWebhookController(
    private val orderRepository: OrderRepository,
    private val emailService: EmailService,
    private val mapper: ObjectMapper,
    private val events: OrderEventsPublisher,
    private val webhookRepo: WebhookEventRepository
) {
    private val log = LoggerFactory.getLogger(EfiWebhookController::class.java)

    @PostMapping(consumes = ["application/json"])
    @Transactional
    fun handle(@RequestBody rawBody: String): ResponseEntity<String> {
        log.info("EFI WEBHOOK RAW={}", rawBody.take(5000))

        val root = runCatching { mapper.readTree(rawBody) }.getOrElse {
            log.warn("EFI WEBHOOK: JSON inválido: {}", it.message)
            return ResponseEntity.ok("⚠️ Ignorado: JSON inválido")
        }

        val pix0 = root.path("pix").takeIf { it.isArray && it.size() > 0 }?.get(0)
        val txid = when {
            !root.path("txid").isMissingNode -> root.path("txid").asText()
            pix0 != null && !pix0.path("txid").isMissingNode -> pix0.path("txid").asText()
            else -> null
        }?.takeIf { it.isNotBlank() }

        val status = when {
            !root.path("status").isMissingNode -> root.path("status").asText()
            pix0 != null && !pix0.path("status").isMissingNode -> pix0.path("status").asText()
            else -> null
        }

        webhookRepo.save(WebhookEvent(txid = txid, status = status, rawBody = rawBody))
        log.info("EFI WEBHOOK PARSED txid={}, status={}", txid, status)
        if (txid == null) return ResponseEntity.ok("⚠️ Ignorado: txid ausente")

        val order = orderRepository.findWithItemsByTxid(txid)
            ?: return ResponseEntity.ok("⚠️ Ignorado: pedido não encontrado para txid=$txid")

        val paidStatuses = setOf("CONCLUIDA","LIQUIDADO","LIQUIDADA","ATIVA-RECEBIDA","COMPLETED","PAID")
        val shouldMarkPaid = status.isNullOrBlank() || paidStatuses.contains(status.uppercase())
        if (!shouldMarkPaid) return ResponseEntity.ok("ℹ️ Ignorado: status=$status não indica pagamento")
        if (order.paid) return ResponseEntity.ok("ℹ️ Ignorado: pedido já estava pago")

        val now = OffsetDateTime.now()
        val reservaValida = order.status == OrderStatus.RESERVADO &&
                (order.reserveExpiresAt == null || now.isBefore(order.reserveExpiresAt))

        return if (reservaValida) {
            order.paid = true
            order.paidAt = now
            order.status = OrderStatus.CONFIRMADO
            orderRepository.save(order)

            runCatching {
                emailService.sendClientEmail(order)
                emailService.sendAuthorEmail(order)
            }.onFailure { e ->
                log.error("EFI WEBHOOK: falha ao enviar e-mails do order {}: {}", order.id, e.message, e)
            }

            order.id?.let { runCatching { events.publishPaid(it) } }
            ResponseEntity.ok("✅ Pago; reserva válida; pedido confirmado; e-mails enviados")
        } else {
            order.status = OrderStatus.CANCELADO_ESTORNADO
            orderRepository.save(order)
            log.warn("Pagamento recebido após expiração da reserva. txid={}, orderId={}", txid, order.id)
            ResponseEntity.ok("⚠️ Pago após expiração; pedido cancelado/estorno")
        }
    }

    @PostMapping("/pix", consumes = ["application/json"])
    fun handlePix(@RequestBody rawBody: String): ResponseEntity<String> = handle(rawBody)
}
