package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.config.EfiProperties
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
    /**
     * Tenta obter access_token tanto no cluster Pix quanto no de Cobranças.
     * Isso evita 401 quando o tenant da Efí espera o OAuth no domínio de cobranças.
     */
    fun getAccessToken(): String {
        val pixBase = if (props.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val cobrBase = if (props.sandbox) "https://cobrancas-h.api.efipay.com.br" else "https://cobrancas.api.efipay.com.br"
        val candidates = listOf(pixBase, cobrBase)

        val basic = Base64.getEncoder().encodeToString("${props.clientId}:${props.clientSecret}".toByteArray())
        val hdr = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Basic $basic")
            set("Accept-Encoding", "gzip")
        }
        val body = mapOf("grant_type" to "client_credentials")

        var lastErr: Exception? = null
        for (base in candidates) {
            try {
                val url = "$base/oauth/token"
                val res = rt.postForEntity<Map<String, Any>>(url, HttpEntity(body, hdr)).body
                    ?: error("OAuth sem body em $base")
                val token = res["access_token"] as? String
                if (!token.isNullOrBlank()) return token
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw IllegalStateException("Falha ao obter access_token na Efí (pix/cobranças).", lastErr)
    }
}
