package com.luizgasparetto.backend.monolito.jobs

import com.luizgasparetto.backend.monolito.models.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.BookService
import com.luizgasparetto.backend.monolito.services.PixClient   // ✅ importar
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class ReservationReaper(
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val pixClient: PixClient                        // ✅ injetar
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

            // 2) tenta cancelar a cobrança na Efí (não quebra a transação)
            runCatching { pixClient.cancel(order.txid) }
                .onSuccess { ok ->
                    if (ok) log.info("REAPER: cobrança cancelada txid={}", order.txid)
                    else     log.warn("REAPER: cancel PATCH não-2xx txid={}", order.txid)
                }
                .onFailure { e ->
                    log.warn("REAPER: falha ao cancelar cobrança txid={}: {}", order.txid, e.message)
                }

            // 3) marca pedido como expirado
            order.status = OrderStatus.RESERVA_EXPIRADA
            order.reserveExpiresAt = null
            orderRepository.save(order)
            log.info("RESERVA EXPIRADA: orderId={} liberada", order.id)
        }
        log.info("REAPER: pedidos expirados processados={}, unidades devolvidas={}", expired.size, released)
    }
}
