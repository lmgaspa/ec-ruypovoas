package com.luizgasparetto.backend.monolito.services.card

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class CardService(
    private val client: CardClient,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(CardService::class.java)

    data class CardChargeResult(
        val paid: Boolean,
        val chargeId: String?,
        val status: String
    )

    fun isCardPaidStatus(status: String?): Boolean {
        if (status.isNullOrBlank()) return false
        return when (status.uppercase()) {
            "PAID", "APPROVED", "CAPTURED", "CONFIRMED" -> true
            else -> false
        }
    }

    /** One-step: cria e tenta capturar a cobrança em uma única chamada. */
    fun createOneStepCharge(
        totalAmount: BigDecimal,
        items: List<Map<String, Any>>,
        paymentToken: String,
        installments: Int,
        customer: Map<String, Any?>,
        txid: String
    ): CardChargeResult {
        val amountCents = totalAmount
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toBigInteger()
            .toInt()

        val body = mapOf(
            "items" to items,
            "payment" to mapOf(
                "credit_card" to mapOf(
                    "payment_token" to paymentToken,
                    "installments" to installments.coerceAtLeast(1),
                    "customer" to customer.filterValues { it != null }
                )
            ),
            "metadata" to mapOf(
                "custom_id" to txid
                // opcional:
                // "notification_url" to "https://SEU_HOST/api/efi-webhook/card"
            ),
            "amount" to mapOf("value" to amountCents)
        )

        val json: JsonNode = client.oneStep(body)
        val data = json.path("data")                 // <--- respostas vêm dentro de "data"
        val status = data.path("status").asText("").uppercase()
        val chargeId = data.path("charge_id").asText(null)

        log.info("CARD ONE-STEP: status={}, chargeId={}", status, chargeId)
        return CardChargeResult(
            paid = isCardPaidStatus(status),
            chargeId = chargeId,
            status = status
        )
    }

    /** Consulta status por charge_id. */
    fun getChargeStatus(chargeId: String): String? {
        val json = client.getCharge(chargeId)
        val data = json.path("data")
        return data.path("status").asText(null)
    }

    /** Cancela/void/refunda a cobrança (dependendo do estágio). */
    fun cancelCharge(chargeId: String): Boolean =
        client.cancel(chargeId)
}
