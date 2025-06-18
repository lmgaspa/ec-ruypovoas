package com.luizgasparetto.backend1.dto

import java.math.BigDecimal

data class PixPagamentoConfirmadoEvent(
    val idPagamento: String,
    val status: String,
    val valor: BigDecimal,
    val email: String,
    val nome: String,
    val sobrenome: String,
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
    val note: String,
    val delivery: String,
    val payment: String,
    val cartItems: List<CartItemDto>
)



