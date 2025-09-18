// src/main/kotlin/com/luizgasparetto/backend/monolito/services/efi/EfiAuthService.kt
package com.luizgasparetto.backend.monolito.services.efi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.efi.EfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.lang.IllegalStateException

@Service
class EfiAuthService(
    private val props: EfiProperties,
    private val mapper: ObjectMapper,
    @Qualifier("efiRestTemplate") private val rtPix: RestTemplate,       // mTLS (PIX)
    @Qualifier("plainRestTemplate") private val rtPlain: RestTemplate    // sem mTLS (CHARGES)
) {
    enum class Api { PIX, CHARGES }

    private val log = LoggerFactory.getLogger(EfiAuthService::class.java)

    @Volatile private var pixToken: String? = null
    @Volatile private var chargesToken: String? = null
    @Volatile private var pixExpMillis: Long = 0
    @Volatile private var chargesExpMillis: Long = 0

    fun getAccessToken(api: Api): String {
        val now = System.currentTimeMillis()
        return when (api) {
            Api.PIX -> {
                val cached = pixToken
                if (cached != null && now < pixExpMillis) return cached
                val pair = fetchNewToken(api)
                pixToken = pair.first
                pixExpMillis = now + (pair.second - 10).coerceAtLeast(30) * 1000L
                pair.first
            }
            Api.CHARGES -> {
                val cached = chargesToken
                if (cached != null && now < chargesExpMillis) return cached
                val pair = fetchNewToken(api)
                chargesToken = pair.first
                chargesExpMillis = now + (pair.second - 10).coerceAtLeast(30) * 1000L
                pair.first
            }
        }
    }

    /** Retorna Pair<access_token, expires_inSeconds> */
    private fun fetchNewToken(api: Api): Pair<String, Int> {
        val isSandbox = props.sandbox
        val clientId = props.clientId
        val clientSecret = props.clientSecret   // pode ser ""

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            setBasicAuth(clientId, clientSecret)
        }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
        }

        val (urls, rt) = when (api) {
            Api.PIX -> {
                val host = if (isSandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
                listOf("$host/oauth/token") to rtPix
            }
            Api.CHARGES -> {
                val base = if (isSandbox) "cobrancas-h.efipay.com.br" else "cobrancas.efipay.com.br"
                val baseApi = if (isSandbox) "cobrancas-h.api.efipay.com.br" else "cobrancas.api.efipay.com.br"
                listOf(
                    "https://$baseApi/oauth/token",
                    "https://$baseApi/auth/oauth/token",
                    "https://$base/oauth/token",
                    "https://$base/auth/oauth/token",
                    "https://$baseApi/auth/oauth/v2/token",
                    "https://$base/auth/oauth/v2/token"
                ) to rtPlain
            }
        }

        var lastErr: Exception? = null
        for (u in urls) {
            try {
                val resp = rt.postForEntity(u, HttpEntity(form, headers), String::class.java)
                if (!resp.statusCode.is2xxSuccessful) {
                    log.warn("EFI AUTH: HTTP={} url={} body={}", resp.statusCode, u, resp.body)
                    continue
                }
                val json: JsonNode = mapper.readTree(resp.body ?: "{}")
                val token = json.path("access_token").asText(null)
                    ?: json.path("token").asText(null)
                val exp = json.path("expires_in").asInt(3600)
                if (token.isNullOrBlank()) {
                    log.warn("EFI AUTH: resposta sem token url={} body={}", u, resp.body)
                    continue
                }
                log.info("EFI AUTH OK: api={} url={}", api, u)
                return token to exp
            } catch (e: HttpStatusCodeException) {
                log.warn("EFI AUTH: HTTP={} url={} body={}", e.statusCode, u, e.responseBodyAsString)
                lastErr = e
            } catch (e: Exception) {
                log.warn("EFI AUTH: falha ao chamar {}: {}", u, e.message)
                lastErr = e
            }
        }
        throw IllegalStateException(
            "Falha ao obter token para $api. Último erro: ${lastErr?.message}",
            lastErr
        )
    }
}
