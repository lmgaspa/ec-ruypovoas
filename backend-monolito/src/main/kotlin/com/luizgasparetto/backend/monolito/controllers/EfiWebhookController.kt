package com.luizgasparetto.backend.monolito.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.models.WebhookEvent
import com.luizgasparetto.backend.monolito.repositories.WebhookEventRepository
import com.luizgasparetto.backend.monolito.services.PaymentProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/efi-webhook")
class EfiWebhookController(
    private val mapper: ObjectMapper,
    private val webhookRepo: WebhookEventRepository,
    private val processor: PaymentProcessor
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

        val chargeId = when {
            !root.path("charge_id").isMissingNode -> root.path("charge_id").asText()
            else -> null
        }?.takeIf { it.isNotBlank() }

        val status = when {
            !root.path("status").isMissingNode -> root.path("status").asText()
            pix0 != null && !pix0.path("status").isMissingNode -> pix0.path("status").asText()
            else -> null
        }

        // 🔹 Salva histórico
        webhookRepo.save(
            WebhookEvent(
                txid = txid,
                chargeId = chargeId,
                status = status,
                rawBody = rawBody
            )
        )

        log.info("EFI WEBHOOK PARSED txid={}, chargeId={}, status={}", txid, chargeId, status)

        // 🔹 Decide se trata via txid (Pix) ou chargeId (Cartão)
        val applied = when {
            txid != null -> processor.markPaidIfNeededByTxid(txid, status)
            chargeId != null -> processor.markPaidIfNeededByChargeId(chargeId, status)
            else -> false
        }

        return if (applied) {
            ResponseEntity.ok("✅ Pedido atualizado com sucesso (status=$status)")
        } else {
            ResponseEntity.ok("ℹ️ Webhook recebido, mas sem alteração (status=$status)")
        }
    }

    @PostMapping("/pix", consumes = ["application/json"])
    fun handlePix(@RequestBody rawBody: String): ResponseEntity<String> = handle(rawBody)
}
