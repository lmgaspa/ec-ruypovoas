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

    /**
     * One-step: cria e tenta capturar a cobrança em uma única chamada.
     * `items` deve estar em centavos (value) e `amount` é a quantidade.
     */
    fun createOneStepCharge(
        totalAmount: BigDecimal,
        items: List<Map<String, Any>>,
        paymentToken: String,
        installments: Int,
        customer: Map<String, Any?>,
        txid: String,
        // frete em centavos (opcional). Se você já incluiu um item "Frete" em `items`, pode deixar null.
        shippingCents: Int? = null
    ): CardChargeResult {
        // soma dos itens (centavos)
        val itemsTotalCents = items.sumOf { (it["value"] as Number).toInt() * (it["amount"] as Number).toInt() }
        val totalCents = totalAmount.setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toInt()

        if (itemsTotalCents != totalCents) {
            log.warn(
                "CARD ONE-STEP: soma dos itens ({}) difere do total informado ({}). " +
                        "A Efí usa o somatório dos itens para calcular a transação.",
                itemsTotalCents, totalCents
            )
        }

        // monta o corpo da Efí
        val body = mutableMapOf<String, Any>(
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
                // "notification_url" -> "https://SEU_HOST/api/efi-webhook/card"  // se quiser
            )
        )

        // se quiser enviar o frete no campo próprio (alternativo a criar um item "Frete")
        if (shippingCents != null && shippingCents > 0) {
            body["shippings"] = listOf(
                mapOf(
                    "name" to "Frete",
                    "value" to shippingCents
                )
            )
        }

        val json: JsonNode = client.oneStep(body)
        // Respostas da Efí vêm em `data`
        val data = json.path("data")
        val status = data.path("status").asText("").uppercase()
        val chargeId = data.path("charge_id").asText(null)

        log.info("CARD ONE-STEP: status={}, chargeId={}", status, chargeId)
        return CardChargeResult(
            paid = isCardPaidStatus(status),
            chargeId = chargeId,
            status = status
        )
    }

    /** Consulta status por charge_id (retorna, por exemplo, APPROVED, PAID etc.) */
    fun getChargeStatus(chargeId: String): String? {
        val json = client.getCharge(chargeId)
        val data = json.path("data")
        return data.path("status").asText(null)
    }

    /** Cancela/void/refunda a cobrança (dependendo do estágio) e retorna true se 2xx. */
    fun cancelCharge(chargeId: String): Boolean =
        client.cancel(chargeId)
}
