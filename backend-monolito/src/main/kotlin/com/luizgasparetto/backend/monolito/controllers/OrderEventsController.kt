package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.services.OrderEventsPublisher
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/orders")
class OrderEventsController(
    private val events: OrderEventsPublisher
) {
    @GetMapping("/{orderId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@PathVariable orderId: Long, response: HttpServletResponse): SseEmitter {
        response.setHeader("Cache-Control", "no-cache, no-transform")
        response.setHeader("Connection", "keep-alive")
        response.setHeader("X-Accel-Buffering", "no")
        return events.subscribe(orderId, 0L)
    }
}
