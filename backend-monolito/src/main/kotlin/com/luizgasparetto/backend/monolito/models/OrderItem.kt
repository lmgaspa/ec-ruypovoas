package com.luizgasparetto.backend.monolito.models

import jakarta.persistence.*

@Entity
data class OrderItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val bookId: String,
    val title: String,
    val quantity: Int,
    val price: Double,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order
)
