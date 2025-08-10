package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.WebhookEvent
import org.springframework.data.jpa.repository.JpaRepository

interface WebhookEventRepository : JpaRepository<WebhookEvent, Long>
