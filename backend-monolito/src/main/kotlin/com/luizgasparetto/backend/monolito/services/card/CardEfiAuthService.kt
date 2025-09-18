package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.config.efi.CardEfiProperties
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@Service
class CardEfiAuthService(
    private val props: CardEfiProperties,
    private val plainRestTemplate: RestTemplate   // sem mTLS
) {
    private val log = LoggerFactory.getLogger(CardEfiAuthService::class.java)

    private data class Token(val accessToken: String, val expiresAtMs: Long)
    private val cached = AtomicReference<Token?>(null)

    private fun base(): String =
        if (props.sandbox) "https://cobrancas-h.api.efipay.com.br"
        else "https://cobrancas.api.efipay.com.br"

    /** Retorna um bearer válido para chamadas de cartão (“charges”). */
    fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        cached.get()?.let { if (it.expiresAtMs - 5_000 > now) return it.accessToken }
        return fetchNewToken()
    }

    private fun fetchNewToken(): String {
        val url = "${base()}/oauth/token" // <-- caminho correto

        val auth = Base64.getEncoder().encodeToString(
            "${props.clientId}:${props.clientSecret}".toByteArray(StandardCharsets.UTF_8)
        )

        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("scope", "charges") // ajuste se seu contrato exigir outro escopo
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            set("Authorization", "Basic $auth")
        }

        try {
            val resp = plainRestTemplate.postForEntity(url, HttpEntity(body, headers), Map::class.java)
            val map = resp.body ?: error("Resposta vazia do /oauth/token")
            val token = (map["access_token"] as? String).orEmpty()
            val expiresIn = (map["expires_in"] as? Number)?.toLong() ?: 300L
            require(token.isNotBlank()) { "access_token ausente na resposta" }

            val exp = System.currentTimeMillis() + expiresIn * 1000
            cached.set(Token(token, exp))
            log.info("EFI CARD AUTH OK: url={}", url)
            return token
        } catch (e: HttpStatusCodeException) {
            log.warn("EFI CARD AUTH: HTTP={} url={} body={}", e.statusCode, url, e.responseBodyAsString)
            throw IllegalStateException("Falha ao obter token de cartão (charges): ${e.statusCode}", e)
        } catch (e: Exception) {
            log.warn("EFI CARD AUTH: falha ao chamar {}: {}", url, e.message)
            throw IllegalStateException("Falha ao obter token de cartão: ${e.message}", e)
        }
    }
}
