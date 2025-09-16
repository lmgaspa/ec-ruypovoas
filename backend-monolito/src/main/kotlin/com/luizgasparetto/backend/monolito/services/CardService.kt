package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.dto.CheckoutRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class CardService(
    private val efiAuthService: EfiAuthService,
    private val objectMapper: ObjectMapper,
    @Qualifier("efiRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${efi.pix.sandbox}") private val sandbox: Boolean
) {
    private val log = LoggerFactory.getLogger(CardService::class.java)

    data class CardChargeResult(val paid: Boolean, val chargeId: String?)

    private fun baseUrl(): String =
        if (sandbox) "https://sandbox.efi.com.br" else "https://api.efi.com.br"

    fun createCardCharge(
        totalAmount: BigDecimal,
        request: CheckoutRequest,
        txid: String
    ): CardChargeResult {
        val token = efiAuthService.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        val cardToken = request.cardToken ?: error("cardToken é obrigatório para pagamento com cartão")
        val installments = (request.installments ?: 1).coerceAtLeast(1)

        val amountCents = totalAmount.setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100)).toBigInteger().toInt()

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
                    "value" to (BigDecimal(it.price).setScale(2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100)).toBigInteger().toInt()),
                    "amount" to it.quantity
                )
            },
            "metadata" to mapOf("txid" to txid),
            "amount" to mapOf("value" to amountCents)
        )

        val resp = restTemplate.exchange(
            "${baseUrl()}/v1/charge/card",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        require(resp.statusCode.is2xxSuccessful) {
            "Falha ao criar cobrança cartão: ${resp.statusCode}"
        }

        val json: JsonNode = objectMapper.readTree(resp.body)
        val status = json.path("status").asText("").uppercase()
        val chargeId = json.path("charge_id").asText(null)

        log.info("CARD CHARGE: status={}, chargeId={}", status, chargeId)

        return when (status) {
            "PAID", "APPROVED" -> CardChargeResult(true, chargeId)
            "DECLINED", "FAILED", "CANCELED" -> error("Pagamento recusado pelo emissor do cartão")
            else -> CardChargeResult(false, chargeId) // AUTHORIZED / PROCESSING...
        }
    }
}
