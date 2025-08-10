package com.luizgasparetto.backend.monolito.dto

data class CobrancaPixResponse(
    val pixCopiaECola: String,
    val imagemQrcodeBase64: String,
    val txid: String
)