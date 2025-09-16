package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.EfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class CardClient(
    @Qualifier("efiRestTemplate") private val rt: RestTemplate,
    private val auth: EfiAuthService,
    private val props: EfiProperties,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(CardClient::class.java)

    private fun baseUrl(): String =
        if (props.sandbox) "https://cobrancas-h.api.efipay.com.br" else "https://cobrancas.api.efipay.com.br"

    fun getCharge(chargeId: String): JsonNode {
        val url = "${baseUrl()}/v1/charge/card/$chargeId"
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }
        val res = rt.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        val body = res.body ?: "{}"
        return mapper.readTree(body)
    }

    fun status(chargeId: String): String? {
        val json = getCharge(chargeId)
        val status = json.path("status").asText(null)
        log.info("CARD STATUS: chargeId={}, status={}", chargeId, status)
        return status
    }

    fun cancel(chargeId: String): Boolean {
        val url = "${baseUrl()}/v1/charge/card/$chargeId"
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }
        val body = mapOf("status" to "CANCELED")
        val res = rt.exchange(url, HttpMethod.PATCH, HttpEntity(body, headers), String::class.java)
        val ok = res.statusCode.is2xxSuccessful
        log.info("CARD CANCEL: chargeId={}, ok={}, httpStatus={}", chargeId, ok, res.statusCode)
        return ok
    }
}
