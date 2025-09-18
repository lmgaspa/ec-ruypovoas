package com.luizgasparetto.backend.monolito.services.card

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.efi.CardEfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class CardService(
    private val auth: CardEfiAuthService,                 // serviço de auth (card)
    private val props: CardEfiProperties,                 // propriedades de card (efi.card.*)
    private val mapper: ObjectMapper,
    @Qualifier("plainRestTemplate") private val rt: RestTemplate // plain, sem mTLS
) {
    private val log = LoggerFactory.getLogger(CardService::class.java)

    data class CardChargeResult(
        val paid: Boolean,
        val chargeId: String?,
        val status: String
    )

    private fun baseUrl(): String =
        if (props.sandbox) "https://cobrancas-h.api.efipay.com.br"
        else "https://cobrancas.api.efipay.com.br"

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
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

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
            "metadata" to mapOf("txid" to txid),
            "amount" to mapOf("value" to amountCents)
        )

        try {
            val resp = rt.exchange(
                "${baseUrl()}/v1/charge/one-step",
                HttpMethod.POST,
                HttpEntity(body, headers),
                String::class.java
            )
            require(resp.statusCode.is2xxSuccessful) {
                "Falha ao criar cobrança (card): ${resp.statusCode}"
            }

            val json: JsonNode = mapper.readTree(resp.body ?: "{}")
            val status = json.path("status").asText("").uppercase()
            val chargeId = json.path("charge_id").asText(null)

            log.info("CARD ONE-STEP: status={}, chargeId={}", status, chargeId)

            val paid = isCardPaidStatus(status)
            return CardChargeResult(paid = paid, chargeId = chargeId, status = status)
        } catch (e: HttpStatusCodeException) {
            log.warn("CARD ONE-STEP: HTTP={} body={}", e.statusCode, e.responseBodyAsString)
            throw e
        } catch (e: Exception) {
            log.warn("CARD ONE-STEP: erro inesperado: {}", e.message, e)
            throw e
        }
    }

    /** Consulta status por charge_id. */
    fun getChargeStatus(chargeId: String): String? {
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply { setBearerAuth(token) }
        val resp = rt.exchange(
            "${baseUrl()}/v1/charge/$chargeId",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )
        if (!resp.statusCode.is2xxSuccessful) return null
        return mapper.readTree(resp.body ?: "{}").path("status").asText(null)
    }

    /** Cancela/void/refunda a cobrança (dependendo do estágio). Retorna true em caso de 2xx. */
    fun cancelCharge(chargeId: String): Boolean {
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        return try {
            val resp = rt.exchange(
                "${baseUrl()}/v1/charge/$chargeId/cancel",
                HttpMethod.POST,
                HttpEntity(mapOf<String, Any>(), headers),
                String::class.java
            )
            val ok = resp.statusCode.is2xxSuccessful
            if (!ok) log.warn("CARD CANCEL: HTTP={} chargeId={}", resp.statusCode, chargeId)
            ok
        } catch (e: Exception) {
            log.warn("CARD CANCEL: falha ao cancelar chargeId={}: {}", chargeId, e.message)
            false
        }
    }
}
