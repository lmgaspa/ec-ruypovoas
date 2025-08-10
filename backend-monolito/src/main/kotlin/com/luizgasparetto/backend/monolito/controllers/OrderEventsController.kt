package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.services.OrderEventsPublisher
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(
    origins = [
        "https://www.agenorgasparetto.com.br",
        "https://agenorgasparetto.com.br",
        "http://localhost:5173"
    ]
)
class OrderEventsController(
    private val events: OrderEventsPublisher
) {
    @GetMapping("/{orderId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@PathVariable orderId: Long): SseEmitter = events.subscribe(orderId)
}
