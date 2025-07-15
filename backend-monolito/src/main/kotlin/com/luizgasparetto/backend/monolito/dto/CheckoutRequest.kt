package com.luizgasparetto.backend.monolito.dto

data class CheckoutRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val cep: String,
    val address: String,
    val city: String,
    val state: String,
    val shipping: Double,
    val total: Double,
    val cartItems: List<CartItemDto>
)

data class CartItemDto(
    val id: String,
    val title: String,
    val quantity: Int,
    val price: Double
)