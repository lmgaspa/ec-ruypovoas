package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.luizgasparetto.backend.monolito.dto.CheckoutRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.beans.factory.annotation.Qualifier
import java.math.BigDecimal

@Service
class CardService(
    private val efiAuthService: EfiAuthService,
    private val objectMapper: ObjectMapper,
    @Qualifier("efiRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${efi.pix.sandbox}") private val sandbox: Boolean
) {
    private val log = org.slf4j.LoggerFactory.getLogger(CardService::class.java)

    data class CardChargeResult(val paid: Boolean, val chargeId: String?)

    /**
     * Cria cobrança de cartão na Efí usando cardToken seguro.
     */
    fun createCardCharge(totalAmount: BigDecimal, request: CheckoutRequest, txid: String): CardChargeResult {
        val baseUrl = if (sandbox) "https://sandbox.efi.com.br" else "https://api.efi.com.br"
        val token = efiAuthService.getAccessToken()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        val cardToken = request.cardToken ?: error("cardToken é obrigatório para pagamento com cartão")
        val installments = request.installments ?: 1

        // corpo da requisição
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
                    "value" to (it.price * 100).toInt(), // em centavos
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
        require(response.statusCode.is2xxSuccessful) { "Falha ao criar cobrança cartão: ${response.statusCode}" }

        val json: JsonNode = objectMapper.readTree(response.body)
        val status = json.path("status").asText().uppercase() // ✅ ALTERADO: normaliza para maiúsculo
        val chargeId = json.path("charge_id").asText()

        log.info("CARD CHARGE: status={}, chargeId={}", status, chargeId)

        // 🔹 Novo fluxo de decisão alinhado ao CheckoutService // ALTERADO
        return when (status) {
            "PAID", "APPROVED" -> CardChargeResult(
                paid = true,
                chargeId = chargeId.takeIf { it.isNotBlank() }
            )
            "DECLINED", "FAILED" -> {
                throw IllegalStateException("Pagamento recusado pelo emissor do cartão") // ✅ ALTERADO
            }
            else -> CardChargeResult(
                paid = false, // aguardando confirmação (AUTHORIZED, PROCESSING, etc) // ✅ ALTERADO
                chargeId = chargeId.takeIf { it.isNotBlank() }
            )
        }
    }
}
