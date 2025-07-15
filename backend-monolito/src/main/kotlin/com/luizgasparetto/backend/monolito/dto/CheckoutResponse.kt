package com.luizgasparetto.backend.monolito.dto

data class CheckoutResponse(
    val message: String,
    val orderId: String,
    val qrCode: String? = null,
    val qrCodeBase64: String? = null
)