package com.luizgasparetto.backend.monolito.services.card

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.lang.IllegalStateException

/**
 * Propriedades específicas de CHARGES (cartão). Não usa mTLS.
 * Configure em application.yml/properties como "efi.card.*".
 */
@ConfigurationProperties("efi.card")
data class CardEfiProperties(
    var clientId: String = "",
    var clientSecret: String = "",
    var sandbox: Boolean = true
)

@Service
class CardEfiAuthService(
    private val props: CardEfiProperties, // efi.card.*
    private val mapper: ObjectMapper,
    @Qualifier("plainRestTemplate") private val rtPlain: RestTemplate // sem mTLS
) {
    private val log = LoggerFactory.getLogger(CardEfiAuthService::class.java)

    @Volatile private var token: String? = null
    @Volatile private var expMillis: Long = 0

    fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        token?.let { cached ->
            if (now < expMillis && cached.isNotBlank()) return cached
        }
        val (newToken, expSeconds) = fetchNewToken()
        token = newToken
        expMillis = now + (expSeconds - 10).coerceAtLeast(30) * 1000L
        return newToken
    }

    /** Pair<access_token, expires_inSeconds> */
    private fun fetchNewToken(): Pair<String, Int> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            setBasicAuth(props.clientId, props.clientSecret)
        }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
        }

        // A API de CHARGES historicamente responde em URLs variadas; tentamos todas.
        val base = if (props.sandbox) "cobrancas-h.efipay.com.br" else "cobrancas.efipay.com.br"
        val baseApi = if (props.sandbox) "cobrancas-h.api.efipay.com.br" else "cobrancas.api.efipay.com.br"
        val urls = listOf(
            "https://$baseApi/oauth/token",
            "https://$baseApi/auth/oauth/token",
            "https://$base/oauth/token",
            "https://$base/auth/oauth/token",
            "https://$baseApi/auth/oauth/v2/token",
            "https://$base/auth/oauth/v2/token"
        )

        var lastErr: Exception? = null
        for (u in urls) {
            try {
                val resp = rtPlain.postForEntity(u, HttpEntity(form, headers), String::class.java)
                if (!resp.statusCode.is2xxSuccessful) {
                    log.warn("EFI CARD AUTH: HTTP={} url={} body={}", resp.statusCode, u, resp.body)
                    continue
                }
                val json: JsonNode = mapper.readTree(resp.body ?: "{}")
                val token = json.path("access_token").asText(null)
                    ?: json.path("token").asText(null)
                val exp = json.path("expires_in").asInt(3600)
                if (token.isNullOrBlank()) {
                    log.warn("EFI CARD AUTH: resposta sem token url={} body={}", u, resp.body)
                    continue
                }
                log.info("EFI CARD AUTH OK: url={}", u)
                return token to exp
            } catch (e: HttpStatusCodeException) {
                log.warn("EFI CARD AUTH: HTTP={} url={} body={}", e.statusCode, u, e.responseBodyAsString)
                lastErr = e
            } catch (e: Exception) {
                log.warn("EFI CARD AUTH: falha ao chamar {}: {}", u, e.message)
                lastErr = e
            }
        }
        throw IllegalStateException("Falha ao obter token para CHARGES. Último erro: ${lastErr?.message}", lastErr)
    }
}
