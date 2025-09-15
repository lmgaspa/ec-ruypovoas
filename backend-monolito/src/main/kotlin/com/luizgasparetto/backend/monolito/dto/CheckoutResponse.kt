package com.luizgasparetto.backend.monolito.dto

data class CheckoutResponse(
    val qrCode: String? = null,          // só preenchido se for Pix
    val qrCodeBase64: String? = null,    // só preenchido se for Pix
    val message: String,                 // mensagem do resultado (Pix ou Cartão)
    val orderId: String,                 // sempre presente
    val txid: String,                    // sempre presente
    val reserveExpiresAt: String? = null, // se houver reserva (Pix ou cartão)
    val ttlSeconds: Long? = null,         // TTL da reserva
    val paid: Boolean? = null,            // status do pagamento (cartão)
    val chargeId: String? = null          // id da cobrança no cartão
)
