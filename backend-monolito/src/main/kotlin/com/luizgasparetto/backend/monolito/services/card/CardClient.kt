package com.luizgasparetto.backend.monolito.services.card

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.efi.EfiProperties
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutRequest
import com.luizgasparetto.backend.monolito.services.efi.EfiAuthService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class CardClient(
    @Qualifier("efiRestTemplate") private val rt: RestTemplate,
    private val auth: EfiAuthService,
    private val props: EfiProperties,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(CardClient::class.java)

    data class CreateChargeResult(
        val chargeId: String?,
        val status: String?   // PAID, APPROVED, AUTHORIZED, PROCESSING, DECLINED, FAILED, CANCELED...
    )

    private fun baseUrl(): String =
        if (props.sandbox) "https://cobrancas-h.api.efipay.com.br" else "https://cobrancas.api.efipay.com.br"

    /** One-step: cria e tenta capturar a cobrança em uma única chamada. */
    fun createOneStepCharge(totalAmount: BigDecimal, req: CardCheckoutRequest, txid: String): CreateChargeResult {
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        val totalCents = totalAmount
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toBigInteger()
            .toInt()

        val items = req.cartItems.map {
            mapOf(
                "name" to it.title,
                "value" to BigDecimal.valueOf(it.price)
                    .setScale(2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .toBigInteger()
                    .toInt(),
                "amount" to it.quantity
            )
        }

        val phoneDigits = req.phone.filter { it.isDigit() }
        val cpfDigits = req.cpf.filter { it.isDigit() }

        val body = mapOf(
            "payment" to mapOf(
                "credit_card" to mapOf(
                    // Efí usa "payment_token"
                    "payment_token" to (req.paymentToken),
                    "installments" to (req.installments.coerceAtLeast(1))
                )
            ),
            "items" to items,
            "amount" to mapOf("value" to totalCents),
            "metadata" to mapOf("txid" to txid),
            "customer" to mapOf(
                "name" to "${req.firstName} ${req.lastName}",
                "email" to req.email,
                "cpf" to cpfDigits,
                "phone_number" to phoneDigits.ifBlank { null }
            )
        )

        val url = "${baseUrl()}/v1/charge/one-step"
        val res = rt.exchange(url, HttpMethod.POST, HttpEntity(body, headers), String::class.java)
        val json: JsonNode = mapper.readTree(res.body ?: "{}")

        val status = json.path("status").asText(null)?.uppercase()
        val chargeId = json.path("charge_id").asText(null)
        log.info("CARD create: status={}, chargeId={}", status, chargeId)
        return CreateChargeResult(chargeId = chargeId, status = status)
    }

    /** Consulta status por charge_id. */
    fun getChargeStatus(chargeId: String): String? {
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply { setBearerAuth(token) }
        val url = "${baseUrl()}/v1/charge/$chargeId"
        val res = rt.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        val body = res.body ?: "{}"
        val status = mapper.readTree(body).path("status").asText(null)
        log.debug("CARD status chargeId={} -> {}", chargeId, status)
        return status
    }

    /** Cancela/estorna a cobrança (retorna true em caso de 2xx). */
    fun cancelCharge(chargeId: String): Boolean {
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }
        return try {
            val res = rt.exchange(
                "${baseUrl()}/v1/charge/$chargeId/cancel",
                HttpMethod.POST,
                HttpEntity(mapOf<String, Any>(), headers),
                String::class.java
            )
            val ok = res.statusCode.is2xxSuccessful
            if (!ok) log.warn("CARD CANCEL: HTTP={} chargeId={}", res.statusCode, chargeId)
            ok
        } catch (e: Exception) {
            log.warn("CARD CANCEL: falha ao cancelar chargeId={}: {}", chargeId, e.message)
            false
        }
    }
}
