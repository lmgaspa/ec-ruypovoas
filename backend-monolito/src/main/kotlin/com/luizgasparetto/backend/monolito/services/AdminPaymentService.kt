package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminPaymentService(
    private val orderRepository: OrderRepository,
    private val bookService: BookService
) {
    private val log = LoggerFactory.getLogger(AdminPaymentService::class.java)

    @Transactional
    fun expireOrder(orderId: Long) {
        val order = orderRepository.findWithItemsById(orderId)
            ?: throw IllegalArgumentException("Pedido não encontrado id=$orderId")

        order.items.forEach { item ->
            bookService.release(item.bookId, item.quantity)
        }

        order.status = OrderStatus.RESERVA_EXPIRADA
        order.reserveExpiresAt = null
        orderRepository.save(order)

        log.info("Pedido expirado manualmente orderId={}", orderId)
    }
}
