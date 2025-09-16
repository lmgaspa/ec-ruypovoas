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
    val complement: String?,
    val district: String,
    val city: String,
    val state: String,
    val phone: String,
    val email: String,
    val note: String?,
    val payment: String, // "pix" | "card"
    val shipping: Double,
    val cartItems: List<CartItemDto>,

    // ✅ usado apenas quando for cartão

    // 🔹 Para cartão
    val cardToken: String? = null, // 🔹 token do cartão
    val paymentToken: String?,               // token gerado pelo Efí
    val installments: Int? = 1               // número de parcelas (até 6 sem juros
)

// Itens do carrinho
data class CartItemDto(
    val id: String,
    val title: String,
    val price: Double,
    val quantity: Int,
    val imageUrl: String
)
