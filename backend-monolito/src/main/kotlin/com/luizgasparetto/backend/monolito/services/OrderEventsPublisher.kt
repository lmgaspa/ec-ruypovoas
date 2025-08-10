package com.luizgasparetto.backend.monolito.services

import jakarta.annotation.PreDestroy
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import org.slf4j.LoggerFactory
import java.time.Duration

@Service
class OrderEventsPublisher(
    private val taskScheduler: TaskScheduler // injete um scheduler (veja bean abaixo)
) {
    private val log = LoggerFactory.getLogger(OrderEventsPublisher::class.java)
    private val listeners = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>() // orderId -> emitters
    private val heartbeats = ConcurrentHashMap<SseEmitter, ScheduledFuture<*>>()

    fun subscribe(orderId: Long, timeoutMs: Long = 0L /* infinito no emitter */): SseEmitter {
        val emitter = SseEmitter(timeoutMs)
        val list = listeners.computeIfAbsent(orderId) { CopyOnWriteArrayList() }
        list += emitter
        log.info("SSE: subscribed orderId={}, listeners={}", orderId, list.size)

        // manda um evento inicial pra “abrir” o fluxo
        try {
            emitter.send(SseEmitter.event().name("ping").data("ok"))
        } catch (_: Exception) { /* ignore */ }

        // agenda heartbeat a cada 25s
        val hb = taskScheduler.scheduleAtFixedRate(
            { safeSend(emitter, SseEmitter.event().comment("hb")) },
            Duration.ofSeconds(25)
        )
        heartbeats[emitter] = hb

        val cleanup = {
            hb?.cancel(true)
            heartbeats.remove(emitter)
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
                } finally {
                    heartbeats.remove(em)?.cancel(true)
                }
            }
            subs.removeAll(dead)
            if (subs.isEmpty()) listeners.remove(orderId)
            log.info("SSE: paid enviado orderId={}, entregues={}", orderId, subs.size - dead.size)
        }
    }

    private fun safeSend(em: SseEmitter, ev: SseEmitter.SseEventBuilder) {
        try { em.send(ev) } catch (_: Exception) { /* se falhar, o cleanup do onError fecha */ }
    }
}
