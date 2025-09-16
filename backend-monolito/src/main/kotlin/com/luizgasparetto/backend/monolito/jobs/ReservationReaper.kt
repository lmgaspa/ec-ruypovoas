package com.luizgasparetto.backend.monolito.jobs

import com.luizgasparetto.backend.monolito.models.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.BookService
import com.luizgasparetto.backend.monolito.services.PixClient
import com.luizgasparetto.backend.monolito.services.CardClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class ReservationReaper(
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val pixClient: PixClient,
    private val cardClient: CardClient
) {
    private val log = LoggerFactory.getLogger(ReservationReaper::class.java)

    @Scheduled(fixedDelayString = "\${checkout.reserve.reaper-ms:60000}")
    @Transactional
    fun reap() {
        val now = OffsetDateTime.now()
        val expired = orderRepository.findExpiredReservations(now, OrderStatus.RESERVADO)
        if (expired.isEmpty()) return

        var released = 0
        expired.forEach { order ->
            // 1) devolve estoque
            order.items.forEach { item ->
                bookService.release(item.bookId, item.quantity)
                released += item.quantity
            }

            // 2) tenta cancelar cobrança
            runCatching {
                when (order.paymentMethod) {
                    "pix" -> order.txid?.let { pixClient.cancel(it) }
                    "card" -> order.chargeId?.let { cardClient.cancel(it) }
                    else -> null
                }
            }.onSuccess { ok ->
                if (ok == true) log.info("REAPER: cobrança cancelada orderId={}", order.id)
                else log.warn("REAPER: cancel falhou ou não suportado orderId={}", order.id)
            }.onFailure { e ->
                log.warn("REAPER: falha ao cancelar cobrança orderId={}: {}", order.id, e.message)
            }

            // 3) marca como expirado
            order.status = OrderStatus.RESERVA_EXPIRADA
            order.reserveExpiresAt = null
            orderRepository.save(order)
            log.info("Reserva expirada: orderId={} liberada", order.id)
        }

        log.info("REAPER: pedidos expirados={}, unidades devolvidas={}", expired.size, released)
    }
}
