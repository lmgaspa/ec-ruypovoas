package com.luizgasparetto.backend.monolito.dto.card

import com.luizgasparetto.backend.monolito.dto.checkout.CartItemDto

data class CardCheckoutRequest(
    val firstName: String,
    val lastName: String,
    val cpf: String,
    val country: String?,
    val cep: String,
    val address: String,
    val number: String,
    val complement: String?,
    val district: String,
    val city: String,
    val state: String,
    val phone: String,
    val email: String,
    val note: String?,
    val shipping: Double,
    val cartItems: List<CartItemDto>,
    val total: Double,           // conferido no servidor (não confiamos cegamente)
    val paymentToken: String,    // 🔑 token gerado no frontend pela SDK Efí
    val installments: Int = 1    // número de parcelas
)

data class CardCheckoutResponse(
    val success: Boolean,
    val message: String,
    val orderId: String,
    val chargeId: String?,
    val status: String
)
