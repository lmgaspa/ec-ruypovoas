package com.luizgasparetto.backend.monolito.dto

import java.math.BigDecimal

data class CheckoutResponse(
    val qrCode: String? = null,          // só preenchido se for Pix
    val qrCodeBase64: String? = null,    // só preenchido se for Pix
    val message: String? = null,                 // mensagem do resultado (Pix ou Cartão)
    val orderId: String? = null,                 // sempre presente
    val txid: String? = null,                    // sempre presente
    val reserveExpiresAt: String? = null, // se houver reserva (Pix ou cartão)
    val ttlSeconds: Long? = null,         // TTL da reserva
    val paid: Boolean? = null,            // status do pagamento (cartão)
    val chargeId: String? = null ,
    val installments: Int? = null,
    val total: BigDecimal? = null
)
