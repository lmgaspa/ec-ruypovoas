package com.luizgasparetto.backend.monolito.realtime

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@Component
class OrderEventsPublisher {
    private val emitters = ConcurrentHashMap<Long, MutableSet<SseEmitter>>()

    fun subscribe(orderId: Long): SseEmitter {
        val emitter = SseEmitter(0L)
        emitters.computeIfAbsent(orderId) { mutableSetOf() }.add(emitter)
        emitter.onCompletion { emitters[orderId]?.remove(emitter) }
        emitter.onTimeout { emitters[orderId]?.remove(emitter) }
        return emitter
    }

    fun publishPaid(orderId: Long) {
        emitters[orderId]?.toList()?.forEach { em ->
            runCatching {
                em.send(SseEmitter.event().name("status").data("PAID"))
            }.onFailure { em.complete() }
        }
    }
}

@RestController
@RequestMapping("/api/orders")
class OrderEventsController(
    private val events: OrderEventsPublisher
) {
    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@PathVariable id: Long): SseEmitter = events.subscribe(id)
}
