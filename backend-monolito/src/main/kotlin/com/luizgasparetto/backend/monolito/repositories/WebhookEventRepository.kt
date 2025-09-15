package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.WebhookEvent
import org.springframework.data.jpa.repository.JpaRepository

interface WebhookEventRepository : JpaRepository<WebhookEvent, Long> {
    // 🔹 busca por txid (Pix)
    fun findByTxid(txid: String): List<WebhookEvent>

    // 🔹 busca por chargeId (cartão)
    fun findByChargeId(chargeId: String): List<WebhookEvent>
}
