package com.luizgasparetto.backend.monolito.controllers.card

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.card.CardPaymentProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/efi-webhook/card")
class CardEfiWebhookController(
    private val mapper: ObjectMapper,
    private val orders: OrderRepository,
    private val processor: CardPaymentProcessor
) {
    private val log = LoggerFactory.getLogger(CardEfiWebhookController::class.java)

    @PostMapping(consumes = ["application/json"])
    fun handle(@RequestBody rawBody: String): ResponseEntity<String> {
        log.info("EFI CARD WEBHOOK RAW={}", rawBody.take(4000))

        val root = runCatching { mapper.readTree(rawBody) }.getOrElse {
            log.warn("CARD WEBHOOK: JSON inválido: {}", it.message)
            return ResponseEntity.ok("ignored: invalid json")
        }

        // Tenta vários formatos comuns do payload
        val chargeId = listOf(
            root.path("charge_id"),
            root.path("data").path("charge_id"),
            root.path("identifiers").path("charge_id"),
            root.path("charge").path("id"),
            root.path("data").path("charge").path("id"),
            root.path("payment").path("charge_id")
        )
            .firstOrNull { !it.isMissingNode && !it.isNull && it.asText().isNotBlank() }
            ?.asText()

        val status = listOf(
            root.path("status"),
            root.path("data").path("status"),
            root.path("payment").path("status"),
            root.path("charge").path("status"),
            root.path("data").path("charge").path("status"),
            root.path("transaction").path("status")
        )
            .firstOrNull { !it.isMissingNode && !it.isNull && it.asText().isNotBlank() }
            ?.asText()

        if (chargeId == null) return ResponseEntity.ok("ignored: no charge_id")
        if (status == null)   return ResponseEntity.ok("ignored: no status")

        val order = orders.findWithItemsByChargeId(chargeId)
            ?: run {
                log.info("CARD WEBHOOK: order not found for chargeId={}, status={}", chargeId, status)
                return ResponseEntity.ok("ignored: order not found")
            }

        val applied = if (processor.isCardPaidStatus(status)) {
            processor.markPaidIfNeededByChargeId(chargeId)
        } else {
            false
        }

        log.info("CARD WEBHOOK: chargeId={}, status={}, applied={}", chargeId, status, applied)
        return ResponseEntity.ok("status=$status; applied=$applied")
    }
}
