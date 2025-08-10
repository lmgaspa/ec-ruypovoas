package com.luizgasparetto.backend.monolito.services

import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

@Service
class OrderEventsPublisher {
    private val listeners = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(orderId: Long, timeoutMs: Long = 10.minutes.inWholeMilliseconds): SseEmitter {
        val emitter = SseEmitter(timeoutMs)
        val list = listeners.computeIfAbsent(orderId) { CopyOnWriteArrayList() }
        list += emitter

        val cleanup = {
            list.remove(emitter)
            if (list.isEmpty()) listeners.remove(orderId)
        }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup() }

        return emitter
    }

    fun publishPaid(orderId: Long) {
        listeners[orderId]?.let { subs ->
            val dead = mutableListOf<SseEmitter>()
            subs.forEach { em ->
                try {
                    em.send(
                        SseEmitter.event()
                            .name("paid")
                            .data(mapOf("orderId" to orderId))
                    )
                    em.complete()
                } catch (_: Exception) {
                    dead += em
                }
            }
            subs.removeAll(dead)
            if (subs.isEmpty()) listeners.remove(orderId)
        }
    }
}
