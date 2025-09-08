package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentProcessor(
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val emailService: EmailService,
    private val events: OrderEventsPublisher
) {
    private val log = LoggerFactory.getLogger(PaymentProcessor::class.java)
    private val paidStatuses = setOf("CONCLUIDA","LIQUIDADO","LIQUIDADA","ATIVA-RECEBIDA","COMPLETED","PAID")

    fun isPaidStatus(status: String?): Boolean =
        status != null && paidStatuses.contains(status.uppercase())

    @Transactional
    fun markPaidIfNeededByTxid(txid: String): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        if (order.paid) return true

        order.paid = true
        orderRepository.save(order)
        log.info("POLL: order {} marcado como pago (txid={})", order.id, txid)

        try {
            order.items.forEach { item -> bookService.updateStock(item.bookId, item.quantity) }
            log.info("POLL: estoque baixado para order {}", order.id)
        } catch (e: Exception) {
            log.error("POLL: falha ao baixar estoque: {}", e.message, e)
        }

        try {
            emailService.sendClientEmail(order)
            emailService.sendAuthorEmail(order)
            log.info("POLL: e-mails enviados para order {}", order.id)
        } catch (e: Exception) {
            log.error("POLL: falha ao enviar e-mails: {}", e.message, e)
        }

        order.id?.let {
            try {
                events.publishPaid(it)
                log.info("POLL: SSE publicado para order {}", it)
            } catch (e: Exception) {
                log.warn("POLL: falha ao publicar SSE: {}", e.message)
            }
        }
        return true
    }
}
