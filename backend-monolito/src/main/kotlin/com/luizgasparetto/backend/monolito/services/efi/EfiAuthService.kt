// src/main/kotlin/com/luizgasparetto/backend/monolito/services/efi/EfiAuthService.kt
package com.luizgasparetto.backend.monolito.services.efi

import com.luizgasparetto.backend.monolito.config.efi.EfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.util.Base64

@Service
class EfiAuthService(
    private val props: EfiProperties,
    @Qualifier("efiRestTemplate") private val rtPix: RestTemplate,       // mTLS (PIX)
    @Qualifier("plainRestTemplate") private val rtPlain: RestTemplate    // sem mTLS (CHARGES)
) {
    enum class Api { PIX, CHARGES }

    private val log = LoggerFactory.getLogger(EfiAuthService::class.java)

    @Volatile private var pixToken: String? = null
    @Volatile private var chargesToken: String? = null
    @Volatile private var pixExpMillis: Long = 0L
    @Volatile private var chargesExpMillis: Long = 0L

    fun getAccessToken(api: Api): String {
        val now = System.currentTimeMillis()
        return when (api) {
            Api.PIX -> {
                pixToken?.takeIf { now < pixExpMillis } ?: fetchAndCache(api).also { pixToken = it.first; pixExpMillis = now + it.second }
                pixToken!!
            }
            Api.CHARGES -> {
                chargesToken?.takeIf { now < chargesExpMillis } ?: fetchAndCache(api).also { chargesToken = it.first; chargesExpMillis = now + it.second }
                chargesToken!!
            }
        }
    }

    /** Retorna Pair<access_token, expiresInMillis> */
    private fun fetchAndCache(api: Api): Pair<String, Long> {
        val sandbox = props.sandbox
        val (url, rt, useBasic) = when (api) {
            Api.PIX ->
                Triple(
                    if (sandbox) "https://pix-h.api.efipay.com.br/oauth/token"
                    else "https://pix.api.efipay.com.br/oauth/token",
                    rtPix,
                    false // mTLS resolve; se seu contrato exigir Basic também para PIX, mude para true
                )
            Api.CHARGES ->
                Triple(
                    if (sandbox) "https://cobrancas-h.api.efipay.com.br/oauth/token"
                    else "https://cobrancas.api.efipay.com.br/oauth/token",
                    rtPlain,
                    true
                )
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            if (useBasic) {
                val basic = Base64.getEncoder().encodeToString("${props.clientId}:${props.clientSecret}".toByteArray())
                set("Authorization", "Basic $basic")
            }
        }

        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
        }

        val resp = rt.postForEntity(url, HttpEntity(form, headers), Map::class.java)
        require(resp.statusCode.is2xxSuccessful) {
            "Falha ao obter token para $api ($url): HTTP=${resp.statusCode}"
        }

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        val token = (body["access_token"] as? String) ?: error("Resposta sem access_token para $api")
        val expiresSec = (body["expires_in"] as? Number)?.toLong() ?: 3600L
        log.info("EFI AUTH OK: api={} url={}", api, url)

        // guarda com margem de 10s
        return token to ((expiresSec - 10).coerceAtLeast(30) * 1000L)
    }
}
