package com.luizgasparetto.backend.monolito.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.BookService
import com.luizgasparetto.backend.monolito.services.EmailService
import com.luizgasparetto.backend.monolito.services.OrderEventsPublisher
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/efi-webhook")
class EfiWebhookController(
    private val orderRepository: OrderRepository,
    private val emailService: EmailService,
    private val bookService: BookService,
    private val mapper: ObjectMapper,
    private val events: OrderEventsPublisher
) {
    private val log = LoggerFactory.getLogger(EfiWebhookController::class.java)

    @PostMapping(consumes = ["application/json"])
    @Transactional
    fun handle(@RequestBody rawBody: String): ResponseEntity<String> {
        log.info("EFI WEBHOOK RAW={}", rawBody.take(5000))

        val root = try { mapper.readTree(rawBody) } catch (e: Exception) {
            log.warn("EFI WEBHOOK: JSON inválido: {}", e.message)
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

        log.info("EFI WEBHOOK PARSED txid={}, status={}", txid, status)
        if (txid == null) return ResponseEntity.ok("⚠️ Ignorado: txid ausente")

        // garante items carregados
        val order = orderRepository.findWithItemsByTxid(txid)
            ?: return ResponseEntity.ok("⚠️ Ignorado: pedido não encontrado para txid=$txid")

        val paidStatuses = setOf("CONCLUIDA","LIQUIDADO","LIQUIDADA","ATIVA-RECEBIDA","COMPLETED","PAID")
        val shouldMarkPaid = status.isNullOrBlank() || paidStatuses.contains(status.uppercase())
        if (!shouldMarkPaid) return ResponseEntity.ok("ℹ️ Ignorado: status=$status não indica pagamento")
        if (order.paid) return ResponseEntity.ok("ℹ️ Ignorado: pedido já estava pago")

        order.paid = true
        orderRepository.save(order)
        log.info("EFI WEBHOOK: order {} marcado como pago (txid={})", order.id, txid)

        try {
            order.items.forEach { item -> bookService.updateStock(item.bookId, item.quantity) }
            log.info("EFI WEBHOOK: estoque baixado para order {}", order.id)
        } catch (e: Exception) {
            log.error("EFI WEBHOOK: falha ao baixar estoque do order {}: {}", order.id, e.message, e)
        }

        try {
            emailService.sendClientEmail(order)
            emailService.sendAuthorEmail(order)
            log.info("EFI WEBHOOK: e-mails enviados para order {}", order.id)
        } catch (e: Exception) {
            log.error("EFI WEBHOOK: falha ao enviar e-mails do order {}: {}", order.id, e.message, e)
        }

        val orderId = order.id ?: return ResponseEntity.ok("✅ Pago; mas orderId nulo (sem SSE)")
        try {
            events.publishPaid(orderId)
            log.info("EFI WEBHOOK: SSE publicado para order {}", orderId)
        } catch (e: Exception) {
            log.warn("EFI WEBHOOK: falha ao publicar SSE para order {}: {}", orderId, e.message)
        }

        return ResponseEntity.ok("✅ Pago; estoque baixado; e-mails enviados")
    }

    @PostMapping("/pix", consumes = ["application/json"])
    fun handlePix(@RequestBody rawBody: String): ResponseEntity<String> = handle(rawBody)
}
