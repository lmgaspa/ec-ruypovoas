package com.luizgasparetto.backend.monolito.dto

// Requisição de checkout (vinda do frontend)
data class CheckoutRequest(
    val firstName: String,
    val lastName: String,
    val cpf: String,
    val country: String,
    val cep: String,
    val address: String,
    val number: String,
    val complement: String? = null,
    val district: String,
    val city: String,
    val state: String,
    val phone: String,
    val email: String,
    val note: String? = null,

    val payment: String,          // "pix" | "card"
    val shipping: Double,
    val cartItems: List<CartItemDto>,

    // Somente para cartão:
    val cardToken: String? = null, // token seguro gerado pela SDK
    val installments: Int? = 1     // até 6 sem juros (ou conforme sua regra)
)

data class CartItemDto(
    val id: String,
    val title: String,
    val price: Double,
    val quantity: Int,
    val imageUrl: String
)
