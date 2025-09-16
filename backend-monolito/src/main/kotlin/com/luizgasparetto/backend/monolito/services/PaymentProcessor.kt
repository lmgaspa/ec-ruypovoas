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

    private fun isPaidStatus(status: String?) =
        status != null && status.lowercase() in listOf("pago", "paid", "approved", "confirmed")

    @Transactional
    fun markPaidIfNeededByTxid(txid: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        if (order.paid) return false
        if (!isPaidStatus(status)) return false

        order.paid = true
        order.paidAt = OffsetDateTime.now()
        order.status = OrderStatus.CONFIRMADO
        order.reserveExpiresAt = null
        orderRepository.save(order)

        // efeitos colaterais (uma única vez)
        runCatching { emailService.sendClientEmail(order) }.onFailure { log.warn("email cliente falhou: {}", it.message) }
        runCatching { emailService.sendAuthorEmail(order) }.onFailure { log.warn("email autor falhou: {}", it.message) }
        runCatching { events.publishPaid(order.id!!) }.onFailure { log.warn("SSE falhou: {}", it.message) }

        log.info("Pedido pago via PIX: orderId={}, txid={}", order.id, txid)
        return true
    }

    @Transactional
    fun markPaidIfNeededByChargeId(chargeId: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByChargeId(chargeId) ?: return false
        if (order.paid) return false
        if (!isPaidStatus(status)) return false

        order.paid = true
        order.paidAt = OffsetDateTime.now()
        order.status = OrderStatus.CONFIRMADO
        order.reserveExpiresAt = null
        orderRepository.save(order)

        runCatching { emailService.sendClientEmail(order) }.onFailure { log.warn("email cliente falhou: {}", it.message) }
        runCatching { emailService.sendAuthorEmail(order) }.onFailure { log.warn("email autor falhou: {}", it.message) }
        runCatching { events.publishPaid(order.id!!) }.onFailure { log.warn("SSE falhou: {}", it.message) }

        log.info("Pedido pago via cartão: orderId={}, chargeId={}", order.id, chargeId)
        return true
    }
}
