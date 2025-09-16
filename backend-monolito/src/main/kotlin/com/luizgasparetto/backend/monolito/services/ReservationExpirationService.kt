package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ReservationExpirationService(
    private val orderRepository: OrderRepository,
    private val bookService: BookService
) {

    private val log = LoggerFactory.getLogger(ReservationExpirationService::class.java)

    /**
     * 🔹 Roda a cada 1 minuto para verificar reservas expiradas
     */
    @Scheduled(fixedRate = 60_000) // 60 segundos
    @Transactional
    fun expireReservations() {
        val now = OffsetDateTime.now()
        val expiredOrders = orderRepository.findExpiredReservations(
            now,
            OrderStatus.CRIADO
        )

        if (expiredOrders.isEmpty()) return

        expiredOrders.forEach { order ->
            order.items.forEach { item ->
                bookService.release(item.bookId, item.quantity)
            }
            order.status = OrderStatus.RESERVA_EXPIRADA
            order.reserveExpiresAt = null
            orderRepository.save(order)

            log.info("Reserva expirada automaticamente orderId={}", order.id)
        }
    }
}
