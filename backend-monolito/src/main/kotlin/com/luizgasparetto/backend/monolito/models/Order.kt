package com.luizgasparetto.backend.monolito.models

import jakarta.persistence.*

@Entity
@Table(name = "orders")
data class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    val firstName: String,
    val lastName: String,
    val email: String,
    val address: String,
    val city: String,
    val state: String,
    val cep: String,
    val total: Double,
    val shipping: Double,
    val qrCode: String? = null,
    val qrCodeBase64: String? = null,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var items: MutableList<OrderItem> = mutableListOf()
)