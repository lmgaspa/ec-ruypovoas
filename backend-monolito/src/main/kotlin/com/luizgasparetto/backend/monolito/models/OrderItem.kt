package com.luizgasparetto.backend.monolito.models

import jakarta.persistence.*

@Entity
@Table(name = "order_items")
data class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    val bookId: String,
    val title: String,
    val quantity: Int,
    val price: Double,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null
)
