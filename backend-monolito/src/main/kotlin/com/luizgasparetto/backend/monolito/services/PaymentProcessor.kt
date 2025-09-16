package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class PaymentProcessor(
    private val orderRepository: OrderRepository
) {
    private val log = LoggerFactory.getLogger(PaymentProcessor::class.java)

    /**
     * Marca um pedido como pago via PIX, se ainda não estiver.
     */
    @Transactional
    fun markPaidIfNeededByTxid(txid: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        if (order.paid) return false

        if (status != null && status.lowercase() in listOf("pago", "paid", "approved", "confirmed")) {
            order.paid = true
            order.paidAt = OffsetDateTime.now()
            order.status = OrderStatus.PAGO
            orderRepository.save(order)
            log.info("Pedido pago via PIX: orderId={}, txid={}", order.id, txid)
            return true
        }
        return false
    }

    /**
     * Marca um pedido como pago via cartão, se ainda não estiver.
     */
    @Transactional
    fun markPaidIfNeededByChargeId(chargeId: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByChargeId(chargeId) ?: return false
        if (order.paid) return false

        if (status != null && status.lowercase() in listOf("pago", "paid", "approved", "confirmed")) {
            order.paid = true
            order.paidAt = OffsetDateTime.now()
            order.status = OrderStatus.PAGO
            orderRepository.save(order)
            log.info("Pedido pago via cartão: orderId={}, chargeId={}", order.id, chargeId)
            return true
        }
        return false
    }
}
