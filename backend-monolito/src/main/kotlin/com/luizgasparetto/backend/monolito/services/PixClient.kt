package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.EfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class PixClient(
    @Qualifier("efiRestTemplate") private val rt: RestTemplate,
    private val auth: EfiAuthService,
    private val props: EfiProperties,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(PixClient::class.java)

    fun getCobranca(txid: String): JsonNode {
        val base = if (props.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val url = "$base/v2/cob/$txid"
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply { setBearerAuth(token) }
        val res = rt.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        val body = res.body ?: "{}"
        return mapper.readTree(body)
    }

    fun status(txid: String): String? =
        getCobranca(txid).path("status").asText(null)
}
