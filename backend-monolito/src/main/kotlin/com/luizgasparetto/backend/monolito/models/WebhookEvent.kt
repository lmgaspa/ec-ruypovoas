package com.luizgasparetto.backend.monolito.models

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "webhook_events")
data class WebhookEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(length = 40)
    var txid: String? = null,

    // 🔹 Novo campo para cartão // ALTERADO
    @Column(length = 40)
    var chargeId: String? = null, // ALTERADO

    @Column(length = 40)
    var status: String? = null,

    @Column(columnDefinition = "TEXT")
    var rawBody: String = "",

    var receivedAt: OffsetDateTime = OffsetDateTime.now()
)
