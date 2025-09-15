package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.Order
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

    private val paidStatuses = setOf("CONCLUIDA","LIQUIDADO","LIQUIDADA","ATIVA-RECEBIDA","COMPLETED","PAID","APPROVED")
    private val declinedStatuses = setOf("DECLINED","FAILED","REFUSED","REFUNDED")

    fun isPaidStatus(status: String?): Boolean =
        status != null && paidStatuses.contains(status.uppercase())

    fun isDeclinedStatus(status: String?): Boolean =
        status != null && declinedStatuses.contains(status.uppercase())

    // 🔹 Pix
    @Transactional
    fun markPaidIfNeededByTxid(txid: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        return handleOrderStatus(order, status, "txid=$txid")
    }

    // 🔹 Cartão
    @Transactional
    fun markPaidIfNeededByChargeId(chargeId: String, status: String?): Boolean {
        val order = orderRepository.findWithItemsByChargeId(chargeId) ?: return false
        return handleOrderStatus(order, status, "chargeId=$chargeId")
    }

    // 🔹 Unificado
    private fun handleOrderStatus(order: Order, status: String?, ref: String): Boolean {
        val now = OffsetDateTime.now()

        return when {
            // ✅ Pago
            isPaidStatus(status) -> {
                if (order.paid) {
                    log.info("POLL: já pago $ref"); return true
                }
                if (order.status != OrderStatus.RESERVADO) {
                    log.info("POLL: ignorado $ref (status atual=${order.status})"); return false
                }
                if (order.reserveExpiresAt != null && now.isAfter(order.reserveExpiresAt)) {
                    log.info("POLL: ignorado $ref (após TTL)"); return false
                }

                order.paid = true
                order.paidAt = now
                order.status = OrderStatus.CONFIRMADO
                orderRepository.save(order)
                log.info("POLL: confirmado $ref orderId={}", order.id)

                runCatching {
                    emailService.sendClientEmail(order)
                    emailService.sendAuthorEmail(order)
                    order.id?.let { events.publishPaid(it) }
                }.onFailure { e -> log.warn("Erro ao enviar e-mails pós-pagamento: {}", e.message) }

                true
            }

            // ❌ Recusado / Estornado
            isDeclinedStatus(status) -> {
                order.status = OrderStatus.CANCELADO_ESTORNADO
                orderRepository.save(order)
                log.warn("POLL: pagamento recusado/estornado $ref orderId={}", order.id)

                runCatching {
                    emailService.sendClientCardDeclined(order)
                    emailService.sendAuthorCardDeclined(order)
                }.onFailure { e -> log.warn("Erro ao enviar e-mails de recusa: {}", e.message) }

                false
            }

            else -> {
                log.info("POLL: ignorado $ref (status=$status)")
                false
            }
        }
    }
}
