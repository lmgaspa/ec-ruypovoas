package com.luizgasparetto.backend.monolito.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.EfiProperties
import com.luizgasparetto.backend.monolito.dto.CobrancaPixResponse
import com.luizgasparetto.backend.monolito.dto.PixCobResponse
import com.luizgasparetto.backend.monolito.dto.PixQrResponse
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

@Service
class PixService(
    private val auth: EfiAuthService,
    private val props: EfiProperties,
    @Qualifier("efiRestTemplate") private val rt: RestTemplate,
    private val mapper: ObjectMapper
) {
    fun criarCobrancaPix(valor: BigDecimal, chavePix: String, descricao: String, txid: String): CobrancaPixResponse {
        val token = auth.getAccessToken()
        val base = if (props.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"

        val valorStr = valor.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val body = mapOf(
            "calendario" to mapOf("expiracao" to 3600),
            "valor" to mapOf("original" to valorStr),
            "chave" to chavePix,
            "solicitacaoPagador" to descricao
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        // cria a cobrança usando o mesmo txid
        val resp = rt.exchange(
            "$base/v2/cob/$txid",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            String::class.java
        )

        val root = mapper.readTree(resp.body)
        val locId = root.path("loc").path("id").asText(null) ?: error("loc.id não encontrado")

        val qr = rt.exchange("$base/v2/loc/$locId/qrcode", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        val qrJson = mapper.readTree(qr.body)

        return CobrancaPixResponse(
            pixCopiaECola = qrJson.path("qrcode").asText(""),
            imagemQrcodeBase64 = qrJson.path("imagemQrcode").asText(""),
            txid = txid // <-- agora retorna também o txid usado
        )
    }
}
