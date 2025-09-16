// src/main/kotlin/com/luizgasparetto/backend/monolito/services/PaymentProcessor.kt
package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class PaymentProcessor(
    private val orderRepository: OrderRepository,
    private val emailService: EmailService,
    private val events: OrderEventsPublisher
) {
    private val log = LoggerFactory.getLogger(PaymentProcessor::class.java)

    private fun isPaidStatus(status: String?): Boolean =
        status != null && status.lowercase() in listOf("pago", "paid", "approved", "confirmed")

    @Transactional
    fun markPaidIfNeededByTxid(txid: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        if (order.paid) return false
        if (!isPaidStatus(status)) return false

        order.paid = true
        order.paidAt = OffsetDateTime.now()
        order.status = OrderStatus.PAGO
        orderRepository.save(order)

        log.info("Pedido pago via PIX: orderId={}, txid={}", order.id, txid)

        // dispare SSE e e-mails
        runCatching { events.publishPaid(order.id!!) }.onFailure {
            log.warn("Falha ao publicar SSE pago orderId={}: {}", order.id, it.message)
        }
        runCatching {
            emailService.sendClientEmail(order)
            emailService.sendAuthorEmail(order)
        }.onFailure {
            log.warn("Falha ao enviar e-mails orderId={}: {}", order.id, it.message)
        }

        return true
    }

    @Transactional
    fun markPaidIfNeededByChargeId(chargeId: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByChargeId(chargeId) ?: return false
        if (order.paid) return false
        if (!isPaidStatus(status)) return false

        order.paid = true
        order.paidAt = OffsetDateTime.now()
        order.status = OrderStatus.PAGO
        orderRepository.save(order)

        log.info("Pedido pago via cartão: orderId={}, chargeId={}", order.id, chargeId)

        // dispare SSE e e-mails
        runCatching { events.publishPaid(order.id!!) }.onFailure {
            log.warn("Falha ao publicar SSE pago orderId={}: {}", order.id, it.message)
        }
        runCatching {
            emailService.sendClientEmail(order)
            emailService.sendAuthorEmail(order)
        }.onFailure {
            log.warn("Falha ao enviar e-mails orderId={}: {}", order.id, it.message)
        }

        return true
    }
}
