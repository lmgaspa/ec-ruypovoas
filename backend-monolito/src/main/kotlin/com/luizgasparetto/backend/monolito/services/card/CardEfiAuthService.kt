package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.config.efi.CardEfiProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@Service
class CardEfiAuthService(
    private val props: CardEfiProperties,
    private val plainRestTemplate: RestTemplate // sem mTLS
) {
    private val log = LoggerFactory.getLogger(CardEfiAuthService::class.java)

    private data class Token(val accessToken: String, val expiresAtMs: Long)
    private val cached = AtomicReference<Token?>(null)

    private fun apiBase(): String =
        if (props.sandbox) "https://cobrancas-h.api.efipay.com.br"
        else               "https://cobrancas.api.efipay.com.br"

    private fun authorizeUrl(): String = "${apiBase()}/v1/authorize"

    /** Bearer válido para chamadas de cartão (charges). */
    fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        cached.get()?.let { if (it.expiresAtMs - 5_000 > now) return it.accessToken }
        return fetchNewToken()
    }

    private fun fetchNewToken(): String {
        val url = authorizeUrl()

        val basic = Base64.getEncoder().encodeToString(
            "${props.clientId}:${props.clientSecret}".toByteArray(StandardCharsets.UTF_8)
        )

        // Conforme docs: JSON com grant_type
        val body = mapOf("grant_type" to "client_credentials")

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set(HttpHeaders.AUTHORIZATION, "Basic $basic")
        }

        try {
            val resp = plainRestTemplate.postForEntity(url, HttpEntity(body, headers), Map::class.java)
            val map = resp.body ?: error("Resposta vazia do /v1/authorize")
            val token = (map["access_token"] as? String).orEmpty()
            val expiresIn = (map["expires_in"] as? Number)?.toLong() ?: 600L // padrão da doc
            require(token.isNotBlank()) { "access_token ausente na resposta" }

            cached.set(Token(token, System.currentTimeMillis() + expiresIn * 1000))
            log.info("EFI CARD AUTH OK: url={}", url)
            return token
        } catch (e: HttpStatusCodeException) {
            log.warn("EFI CARD AUTH: HTTP={} url={} body={}", e.statusCode, url, e.responseBodyAsString)
            throw IllegalStateException("Falha ao obter token de cartão: ${e.statusCode}", e)
        } catch (e: Exception) {
            log.warn("EFI CARD AUTH: falha ao chamar {}: {}", url, e.message)
            throw IllegalStateException("Falha ao obter token de cartão: ${e.message}", e)
        }
    }

    /** Base pública caso precise montar URLs das charges em outros serviços. */
    fun chargesBaseUrl(): String = apiBase()
}
