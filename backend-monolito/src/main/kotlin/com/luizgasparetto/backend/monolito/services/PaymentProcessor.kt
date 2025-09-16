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
    private val publisher: OrderEventsPublisher,   // notifica frontend (SSE)
    private val emailService: EmailService         // envia e-mails
) {
    private val log = LoggerFactory.getLogger(PaymentProcessor::class.java)

    private fun isPaidStatus(status: String?): Boolean =
        status != null && status.lowercase() in listOf(
            "pago", "paid", "approved", "confirmed",
            "concluida" // ✅ PIX pago
        )

    @Transactional
    fun markPaidIfNeededByTxid(txid: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        if (order.paid) return false
        if (!isPaidStatus(status)) return false

        order.paid = true
        order.paidAt = OffsetDateTime.now()
        order.status = OrderStatus.PAGO
        orderRepository.save(order)

        val id = requireNotNull(order.id)
        log.info("Pedido pago via PIX: orderId={}, txid={}, status={}", id, txid, status)

        publisher.publishPaid(id)
        runCatching {
            emailService.sendClientEmail(order)
            emailService.sendAuthorEmail(order)
        }.onFailure { e ->
            log.warn("Falha ao enviar e-mails orderId={}: {}", id, e.message)
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

        val id = requireNotNull(order.id)
        log.info("Pedido pago via cartão: orderId={}, chargeId={}, status={}", id, chargeId, status)

        publisher.publishPaid(id)
        runCatching {
            emailService.sendClientEmail(order)
            emailService.sendAuthorEmail(order)
        }.onFailure { e ->
            log.warn("Falha ao enviar e-mails orderId={}: {}", id, e.message)
        }
        return true
    }
}
