// src/main/kotlin/com/luizgasparetto/backend/monolito/services/efi/EfiAuthService.kt
package com.luizgasparetto.backend.monolito.services.efi

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.efi.EfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Service
class EfiAuthService(
    private val props: EfiProperties,
    private val mapper: ObjectMapper,
    @Qualifier("efiRestTemplate") private val rt: RestTemplate
) {
    enum class Api { PIX, CHARGES }

    private val log = LoggerFactory.getLogger(EfiAuthService::class.java)

    private data class CachedToken(
        val token: String,
        val expiresAt: Instant
    )

    private val cache = ConcurrentHashMap<Api, CachedToken>()

    /** Obtém um access_token com cache por API (PIX ou CHARGES). */
    @Synchronized
    fun getAccessToken(api: Api = Api.CHARGES): String {
        // cache válido?
        cache[api]?.let { ct ->
            if (Instant.now().isBefore(ct.expiresAt)) return ct.token
        }

        val token = fetchNewToken(api)
        cache[api] = token
        return token.token
    }

    private fun fetchNewToken(api: Api): CachedToken {
        val base = when (api) {
            Api.PIX     -> if (props.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
            Api.CHARGES -> if (props.sandbox) "https://cobrancas-h.api.efipay.com.br" else "https://cobrancas.api.efipay.com.br"
        }

        // ATENÇÃO: para CHARGES o endpoint correto inclui /auth/
        val tokenUrl = when (api) {
            Api.PIX     -> "$base/oauth/token"
            Api.CHARGES -> "$base/auth/oauth/token"
        }

        val clientId = props.clientId
        val clientSecret = props.clientSecret

        val basic = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8))

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            accept = listOf(MediaType.APPLICATION_JSON)
            set(HttpHeaders.AUTHORIZATION, "Basic $basic")
        }

        val body = "grant_type=client_credentials"

        try {
            val resp = rt.postForEntity(tokenUrl, HttpEntity(body, headers), String::class.java)
            val status = resp.statusCode.value()
            val payload = resp.body ?: "{}"

            if (status !in 200..299) {
                log.warn("EFI AUTH: HTTP={} body={}", status, payload)
                error("Falha ao obter token: HTTP $status")
            }

            val json = mapper.readTree(payload)
            val accessToken = json.path("access_token").asText(null)
                ?: error("access_token ausente na resposta")
            val expiresIn = max(1, json.path("expires_in").asInt(3600)) // segundos

            // margem de segurança de 30s
            val expiresAt = Instant.now().plusSeconds(max(1, expiresIn - 30).toLong())

            log.info("EFI AUTH: token obtido para {} expira em {}s", api, expiresIn)
            return CachedToken(accessToken, expiresAt)
        } catch (e: HttpStatusCodeException) {
            log.warn("EFI AUTH: HTTP={} body={}", e.statusCode, e.responseBodyAsString)
            throw e
        } catch (e: Exception) {
            log.warn("EFI AUTH: erro inesperado: {}", e.message, e)
            throw e
        }
    }
}
