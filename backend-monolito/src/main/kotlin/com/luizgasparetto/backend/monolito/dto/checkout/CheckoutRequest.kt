package com.luizgasparetto.backend.monolito.dto.checkout

import com.fasterxml.jackson.annotation.JsonAlias

data class CheckoutRequest(
    val firstName: String,
    val lastName: String,
    val cpf: String,
    val country: String,
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
    val payment: String?,          // "pix" | "card"
    val shipping: Double,
    val cartItems: List<CartItemDto>,
    val total: Double,

    // ---- campos específicos p/ cartão (opcionais) ----
    @JsonAlias("payment_token","cardToken","card_token")
    val cardPaymentToken: String? = null,
    @JsonAlias("brand")
    val cardBrand: String? = null,
    @JsonAlias("installments")
    val installments: Int? = 1
)

data class CartItemDto(
    val id: String,
    val title: String,
    val price: Double,
    val quantity: Int,
    val imageUrl: String
)
