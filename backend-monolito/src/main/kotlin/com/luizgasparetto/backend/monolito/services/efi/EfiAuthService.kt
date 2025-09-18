// src/main/kotlin/com/luizgasparetto/backend/monolito/services/efi/EfiAuthService.kt
package com.luizgasparetto.backend.monolito.services.efi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.luizgasparetto.backend.monolito.config.efi.EfiProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class EfiAuthService(
    private val props: EfiProperties,
    private val mapper: ObjectMapper,
    private val rt: RestTemplate // usa o "efiRestTemplate" por @Qualifier se quiser
) {
    enum class Api { PIX, CHARGES }

    private val log = LoggerFactory.getLogger(EfiAuthService::class.java)

    private data class TokenHolder(val token: String, val exp: Instant)
    private var pixToken: TokenHolder? = null
    private var chargesToken: TokenHolder? = null

    fun getAccessToken(api: Api = Api.PIX): String {
        val now = Instant.now()
        val cached = when (api) {
            Api.PIX -> pixToken
            Api.CHARGES -> chargesToken
        }
        if (cached != null && cached.exp.isAfter(now.plusSeconds(10))) {
            return cached.token
        }
        val fresh = fetchNewToken(api)
        when (api) {
            Api.PIX -> pixToken = fresh
            Api.CHARGES -> chargesToken = fresh
        }
        return fresh.token
    }

    private fun baseUrl(api: Api): String = when {
        api == Api.PIX && props.sandbox -> "https://pix-h.api.efipay.com.br"
        api == Api.PIX && !props.sandbox -> "https://pix.api.efipay.com.br"
        api == Api.CHARGES && props.sandbox -> "https://cobrancas-h.api.efipay.com.br"
        else -> "https://cobrancas.api.efipay.com.br"
    }

    /**
     * Algumas instalações expõem o token em caminhos diferentes.
     * Tentamos em ordem até obter 2xx.
     */
    private fun tokenPaths(api: Api): List<String> = when (api) {
        Api.PIX -> listOf("/oauth/token", "/auth/oauth/token")
        Api.CHARGES -> listOf("/auth/oauth/token", "/oauth/token", "/auth/oauth/v2/token")
    }

    private fun fetchNewToken(api: Api): TokenHolder {
        val urlBase = baseUrl(api)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            setBasicAuth(props.clientId, props.clientSecret)
        }

        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            // Se precisar de escopo no seu ambiente, descomente e ajuste:
            // add("scope", if (api == Api.PIX) "cob.write cob.read pix.write pix.read" else "charges.read charges.write")
        }

        var lastErr: Exception? = null
        for (path in tokenPaths(api)) {
            val url = "$urlBase$path"
            try {
                val resp = rt.postForEntity(url, HttpEntity(form, headers), String::class.java)
                if (!resp.statusCode.is2xxSuccessful) {
                    log.warn("EFI AUTH: HTTP={} url={} body={}", resp.statusCode, url, resp.body)
                    continue
                }
                val json = mapper.readTree(resp.body)
                val accessToken = json.path("access_token").asText(null)
                    ?: error("access_token ausente: $json")
                val expiresIn = json.path("expires_in").asLong(3600)
                val exp = Instant.now().plus(expiresIn - 30, ChronoUnit.SECONDS) // margem
                log.info("EFI AUTH OK: api={} url={}", api, url)
                return TokenHolder(accessToken, exp)
            } catch (e: HttpStatusCodeException) {
                log.warn("EFI AUTH: HTTP={} url={} body={}", e.statusCode, url, e.responseBodyAsString)
                lastErr = e
            } catch (e: Exception) {
                log.warn("EFI AUTH: falha url={} err={}", url, e.message)
                lastErr = e
            }
        }
        throw IllegalStateException("Falha ao obter token para $api ($urlBase). Último erro: ${lastErr?.message}", lastErr)
    }
}
