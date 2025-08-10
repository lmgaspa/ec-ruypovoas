package com.luizgasparetto.backend.monolito.models

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "orders")
data class Order(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false) val firstName: String,
    @Column(nullable = false) val lastName: String,
    @Column(nullable = false) val email: String,

    @Column(nullable = false) var cpf: String,
    @Column(nullable = false) var number: String,
    @Column(nullable = true) var complement: String? = null,
    @Column(nullable = false) var district: String,

    @Column(nullable = false) val address: String,
    @Column(nullable = false) val city: String,
    @Column(nullable = false) val state: String,
    @Column(nullable = false) val cep: String,
    @Column(nullable = false) val phone: String,

    @Column(columnDefinition = "TEXT")
    var note: String? = null,

    @Column(nullable = false, precision = 10, scale = 2) val total: BigDecimal,
    @Column(nullable = false, precision = 10, scale = 2) val shipping: BigDecimal,

    @Column(nullable = false)
    var paid: Boolean = false,

    @Column(name = "mailed_at")
    var mailedAt: OffsetDateTime? = null,

    @Column(columnDefinition = "TEXT")
    var qrCode: String? = null,

    @Column(columnDefinition = "TEXT")
    var qrCodeBase64: String? = null,

    @Column(nullable = false, unique = true, length = 35)
    var txid: String? = null,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var items: MutableList<OrderItem> = mutableListOf()
)