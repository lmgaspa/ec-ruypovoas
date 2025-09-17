package com.luizgasparetto.backend.monolito.models.order

enum class OrderStatus {
    CRIADO,
    RESERVADO,
    CONFIRMADO,
    RESERVA_EXPIRADA,
    CANCELADO_ESTORNADO
}