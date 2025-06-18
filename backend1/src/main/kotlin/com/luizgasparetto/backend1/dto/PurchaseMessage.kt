package com.luizgasparetto.backend1.dto

import java.math.BigDecimal

data class PurchaseMessage(
    val firstName: String,
    val lastName: String,
    val cpf: String,
    val country: String,
    val cep: String,
    val address: String,
    val number: String,
    val complement: String,
    val district: String,
    val city: String,
    val state: String,
    val phone: String,
    val email: String,
    val note: String,
    val delivery: String,
    val payment: String,
    val valor: BigDecimal,
    val cartItems: List<CartItemDto>
)
