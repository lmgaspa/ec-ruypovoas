package com.luizgasparetto.backend.monolito.models

import jakarta.persistence.*

@Entity
@Table(name = "orders")
data class Order(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val firstName: String,
    val lastName: String,
    val email: String,
    val address: String,
    val city: String,
    val state: String,
    val cep: String,
    val total: Double,
    val shipping: Double,
    val paymentId: Long = 0L,

    @Column(columnDefinition = "TEXT")
    val qrCode: String? = null,

    @Column(columnDefinition = "TEXT")
    val qrCodeBase64: String? = null,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<OrderItem> = mutableListOf()
)


