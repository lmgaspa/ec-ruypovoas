package com.luizgasparetto.backend.monolito.models.webhook

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "webhook_events")
data class WebhookEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(length = 40)
    var txid: String? = null,

    @Column(length = 40)
    var status: String? = null,

    @Column(columnDefinition = "TEXT")
    var rawBody: String = "",

    var receivedAt: OffsetDateTime = OffsetDateTime.now()
)