// src/main/kotlin/.../services/PaymentProcessor.kt
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
    private val events: OrderEventsPublisher,
    private val emailService: EmailService            // << injete
) {
    private val log = LoggerFactory.getLogger(PaymentProcessor::class.java)

    @Transactional
    fun markPaidIfNeededByTxid(txid: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        if (order.paid) return false

        if (status != null && status.lowercase() in listOf("pago", "paid", "approved", "confirmed")) {
            order.paid = true
            order.paidAt = OffsetDateTime.now()
            order.status = OrderStatus.PAGO
            orderRepository.save(order)

            events.publishPaid(requireNotNull(order.id))
            runCatching {
                emailService.sendClientEmail(order)
                emailService.sendAuthorEmail(order)
            }

            return true
        }
        return false
    }

    @Transactional
    fun markPaidIfNeededByChargeId(chargeId: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByChargeId(chargeId) ?: return false
        if (order.paid) return false

        if (status != null && status.lowercase() in listOf("pago", "paid", "approved", "confirmed")) {
            order.paid = true
            order.paidAt = OffsetDateTime.now()
            order.status = OrderStatus.PAGO
            orderRepository.save(order)

            events.publishPaid(requireNotNull(order.id))
            runCatching {
                emailService.sendClientEmail(order)
                emailService.sendAuthorEmail(order)
            }

            return true
        }
        return false
    }
}
