package com.luizgasparetto.backend.monolito.models

import jakarta.persistence.*
import java.util.*

@Entity
data class Book(
    @Id
    val id: String = UUID.randomUUID().toString(),

    val title: String,
    val imageUrl: String,
    val price: Double,
    val description: String,
    val author: String,
    val category: String,
    var stock: Int
)