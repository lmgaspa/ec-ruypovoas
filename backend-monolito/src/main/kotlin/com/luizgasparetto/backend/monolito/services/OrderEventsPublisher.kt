package com.luizgasparetto.backend.monolito.services

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture

@Service
class OrderEventsPublisher(
    private val taskScheduler: TaskScheduler
) {
    private val log = LoggerFactory.getLogger(OrderEventsPublisher::class.java)

    private val listeners = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    private val heartbeats = ConcurrentHashMap<SseEmitter, ScheduledFuture<*>>()

    fun subscribe(orderId: Long, timeoutMs: Long = 0L): SseEmitter {
        val emitter = SseEmitter(timeoutMs)
        val list = listeners.computeIfAbsent(orderId) { CopyOnWriteArrayList() }
        list += emitter
        log.info("SSE: subscribed orderId={}, listeners={}", orderId, list.size)

        safeSend(emitter, SseEmitter.event().name("open").data("ok"))

        val hb = taskScheduler.scheduleAtFixedRate(
            { safeSend(emitter, SseEmitter.event().name("ping").data("💓")) },
            Duration.ofSeconds(20)
        )
        if (hb != null) heartbeats[emitter] = hb

        val cleanup = {
            heartbeats.remove(emitter)?.cancel(true)
            list.remove(emitter)
            if (list.isEmpty()) listeners.remove(orderId)
            log.info("SSE: emitter closed orderId={}, remaining={}", orderId, list.size)
        }

        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup() }

        return emitter
    }

    fun publishPaid(orderId: Long) {
        listeners[orderId]?.let { subs ->
            val dead = mutableListOf<SseEmitter>()
            val total = subs.size

            subs.forEach { em ->
                try {
                    em.send(
                        SseEmitter.event()
                            .name("paid")
                            .data(mapOf("orderId" to orderId))
                    )
                    em.complete() // dispara onCompletion -> cleanup
                } catch (_: Exception) {
                    dead += em
                } finally {
                    heartbeats.remove(em)?.cancel(true)
                }
            }

            subs.removeAll(dead)
            if (subs.isEmpty()) listeners.remove(orderId)

            val entregues = total - dead.size
            log.info("SSE: paid enviado orderId={}, entregues={}", orderId, entregues)
        }
    }

    private fun safeSend(em: SseEmitter, ev: SseEmitter.SseEventBuilder) {
        try { em.send(ev) } catch (_: Exception) { /* cleanup é feito pelos handlers */ }
    }

    @PreDestroy
    fun shutdown() {
        heartbeats.values.forEach { it.cancel(true) }
        heartbeats.clear()
        listeners.values.flatten().forEach { it.complete() }
        listeners.clear()
    }
}
