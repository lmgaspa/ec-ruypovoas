package com.luizgasparetto.backend.monolito.services.efi

import com.luizgasparetto.backend.monolito.config.efi.EfiProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Service
class EfiAuthService(
    @Qualifier("efiRestTemplate") private val rt: RestTemplate,
    private val props: EfiProperties
) {
    enum class Api { PIX, CHARGES }

    private data class Cache(val token: String, val expiresAtMillis: Long)
    private val caches = ConcurrentHashMap<Api, Cache>()

    private fun oauthBase(api: Api): String {
        val sandbox = props.sandbox
        return when (api) {
            Api.PIX     -> if (sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
            Api.CHARGES -> if (sandbox) "https://cobrancas-h.api.efipay.com.br" else "https://cobrancas.api.efipay.com.br"
        }
    }

    fun getAccessToken(api: Api): String {
        val now = System.currentTimeMillis()
        caches[api]?.let { c ->
            if (now < c.expiresAtMillis - 5_000) return c.token
        }

        val url = "${oauthBase(api)}/oauth/token"
        val basic = Base64.getEncoder()
            .encodeToString("${props.clientId}:${props.clientSecret}".toByteArray())

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Basic $basic")
            set("Accept-Encoding", "gzip")
        }
        val body = mapOf("grant_type" to "client_credentials")

        val res = rt.postForEntity<Map<String, Any>>(url, HttpEntity(body, headers)).body
            ?: error("OAuth sem body")
        val token = res["access_token"] as? String ?: error("access_token ausente")
        val expiresIn = (res["expires_in"] as? Number)?.toLong() ?: 600L

        caches[api] = Cache(token, now + expiresIn * 1000)
        return token
    }
}
