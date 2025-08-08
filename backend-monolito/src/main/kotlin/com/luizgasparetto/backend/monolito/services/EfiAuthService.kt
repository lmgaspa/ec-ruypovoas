package com.luizgasparetto.backend.monolito.services


import com.luizgasparetto.backend.monolito.config.EfiProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
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
    fun getAccessToken(): String {
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
        return res["access_token"] as? String ?: error("access_token ausente")
    }
}