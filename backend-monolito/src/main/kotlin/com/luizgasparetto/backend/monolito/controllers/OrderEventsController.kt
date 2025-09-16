// src/main/kotlin/.../controllers/OrderEventsController.kt
package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.OrderEventsPublisher
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/orders")
class OrderEventsController(
    private val publisher: OrderEventsPublisher,
    private val orderRepo: OrderRepository
) {
    @GetMapping("/{orderId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(@PathVariable orderId: Long): SseEmitter {
        // opcional: valida existência do pedido (evita SSE “zumbi”)
        orderRepo.findById(orderId).orElseThrow()
        return publisher.subscribe(orderId, 0)
    }
}
