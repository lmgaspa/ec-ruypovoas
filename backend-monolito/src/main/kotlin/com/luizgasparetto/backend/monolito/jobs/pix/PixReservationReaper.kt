package com.luizgasparetto.backend.monolito.jobs.pix

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import com.luizgasparetto.backend.monolito.services.pix.PixClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class PixReservationReaper(
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val pixClient: PixClient
) {
    private val log = LoggerFactory.getLogger(PixReservationReaper::class.java)

    @Scheduled(fixedDelayString = "\${checkout.reserve.reaper-ms:60000}")
    @Transactional
    fun reap() {
        val now = OffsetDateTime.now()
        val expired = orderRepository.findExpiredReservations(now, OrderStatus.RESERVADO)
        if (expired.isEmpty()) return

        var released = 0
        expired.forEach { order ->
            // Somente PIX: sem chargeId (cartão) e não marcado explicitamente como "card"
            val isPix = order.chargeId.isNullOrBlank() &&
                    (order.paymentMethod?.equals("pix", ignoreCase = true) != false)
            if (!isPix) return@forEach

            // 1) devolve estoque
            order.items.forEach { item ->
                bookService.release(item.bookId, item.quantity)
                released += item.quantity
            }

            // 2) tenta cancelar a cobrança Pix (não derruba a transação)
            runCatching { pixClient.cancel(order.txid) }
                .onSuccess { ok ->
                    if (ok) log.info("PIX-REAPER: cobrança cancelada txid={}", order.txid)
                    else     log.warn("PIX-REAPER: cancel PATCH não-2xx txid={}", order.txid)
                }
                .onFailure { e ->
                    log.warn("PIX-REAPER: falha ao cancelar txid={}: {}", order.txid, e.message)
                }

            // 3) marca como reserva expirada
            order.status = OrderStatus.RESERVA_EXPIRADA
            order.reserveExpiresAt = null
            orderRepository.save(order)
            log.info("PIX-REAPER: reserva expirada orderId={} liberada", order.id)
        }
        log.info("PIX-REAPER: expirados={}, unidadesDevolvidas={}", expired.size, released)
    }
}
