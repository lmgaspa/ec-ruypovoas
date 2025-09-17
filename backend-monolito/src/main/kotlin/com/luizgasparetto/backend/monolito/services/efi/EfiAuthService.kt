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

@Service
class EfiAuthService(
    @Qualifier("efiRestTemplate") private val rt: RestTemplate,
    private val props: EfiProperties
) {
    @Volatile private var cachedToken: String? = null
    @Volatile private var expiresAtMillis: Long = 0

    fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        cachedToken?.let { t ->
            if (now < expiresAtMillis - 5_000) return t // margem de 5s
        }

        val base = if (props.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val url = "$base/oauth/token"
        val basic = Base64.getEncoder().encodeToString("${props.clientId}:${props.clientSecret}".toByteArray())

        val hdr = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Basic $basic")
            set("Accept-Encoding", "gzip")
        }
        val body = mapOf("grant_type" to "client_credentials")

        val res = rt.postForEntity<Map<String, Any>>(url, HttpEntity(body, hdr)).body
            ?: error("OAuth sem body")
        val token = res["access_token"] as? String ?: error("access_token ausente")
        val expiresIn = (res["expires_in"] as? Number)?.toLong() ?: 600L

        cachedToken = token
        expiresAtMillis = now + expiresIn * 1000
        return token
    }
}