package com.luizgasparetto.backend.monolito.models

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "books")
data class Book(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val imageUrl: String,

    @Column(nullable = false)
    val price: Double,

    @Column(columnDefinition = "TEXT")
    val description: String,

    val author: String? = null,
    val category: String?,
    var stock: Int?
)
