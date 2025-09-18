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
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

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
    @Volatile private var pixExpiresAtMs: Long = 0L
    @Volatile private var chargesToken: String? = null
    @Volatile private var chargesExpiresAtMs: Long = 0L

    private val pixLock = Any()
    private val chargesLock = Any()

    fun getAccessToken(api: Api): String {
        val now = System.currentTimeMillis()
        return when (api) {
            Api.PIX -> {
                val cached = pixToken
                if (cached != null && now < pixExpiresAtMs) return cached
                synchronized(pixLock) {
                    val again = pixToken
                    if (again != null && now < pixExpiresAtMs) return@synchronized again
                    val (t, exp) = fetchNewToken(api)
                    pixToken = t
                    pixExpiresAtMs = now + ((exp - 15).coerceAtLeast(30)) * 1000L // margem 15s
                    t
                }
            }
            Api.CHARGES -> {
                val cached = chargesToken
                if (cached != null && now < chargesExpiresAtMs) return cached
                synchronized(chargesLock) {
                    val again = chargesToken
                    if (again != null && now < chargesExpiresAtMs) return@synchronized again
                    val (t, exp) = fetchNewToken(api)
                    chargesToken = t
                    chargesExpiresAtMs = now + ((exp - 15).coerceAtLeast(30)) * 1000L
                    t
                }
            }
        }
    }

    /** Retorna Pair<access_token, expires_in_seconds> */
    private fun fetchNewToken(api: Api): Pair<String, Int> {
        val (url, rt) = when (api) {
            Api.PIX ->
                (if (props.sandbox)
                    "https://pix-h.api.efipay.com.br/oauth/token"
                else
                    "https://pix.api.efipay.com.br/oauth/token") to rtPix

            Api.CHARGES ->
                (if (props.sandbox)
                    "https://cobrancas-h.api.efipay.com.br/oauth/token"
                else
                    "https://cobrancas.api.efipay.com.br/oauth/token") to rtPlain
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            // A Efí exige Basic Auth (clientId:clientSecret) — secret pode ser vazio ("")
            setBasicAuth(props.clientId, props.clientSecret)
        }

        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
        }

        try {
            val resp = rt.postForEntity(url, HttpEntity(form, headers), String::class.java)
            if (!resp.statusCode.is2xxSuccessful) {
                log.warn("EFI AUTH: HTTP={} url={} body={}", resp.statusCode, url, resp.body)
                throw IllegalStateException("Falha ao obter token ($api): HTTP ${resp.statusCode.value()}")
            }

            val json: JsonNode = mapper.readTree(resp.body ?: "{}")
            val token = json.path("access_token").asText(null).ifNullOrBlank {
                json.path("token").asText(null)
            } ?: run {
                log.warn("EFI AUTH: resposta sem access_token url={} body={}", url, resp.body)
                throw IllegalStateException("Resposta sem access_token ($api)")
            }
            val exp = json.path("expires_in").asInt(3600)

            log.info("EFI AUTH OK: api={} url={}", api, url)
            return token to exp
        } catch (e: HttpStatusCodeException) {
            log.warn("EFI AUTH: HTTP={} url={} body={}", e.statusCode, url, e.responseBodyAsString)
            throw IllegalStateException(
                "Falha ao obter token para $api (HTTP ${e.statusCode.value()})",
                e
            )
        } catch (e: ResourceAccessException) {
            log.warn("EFI AUTH: erro de acesso ao recurso url={}: {}", url, e.message)
            throw IllegalStateException(
                "Falha de rede/host ao obter token para $api ($url): ${e.message}",
                e
            )
        } catch (e: Exception) {
            log.warn("EFI AUTH: erro geral url={}: {}", url, e.message)
            throw IllegalStateException(
                "Erro ao obter token para $api: ${e.message}",
                e
            )
        }
    }

    private inline fun String?.ifNullOrBlank(block: () -> String?): String? =
        if (this.isNullOrBlank()) block() else this
}
