package com.luizgasparetto.backend.monolito.services

import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

@Service
class OrderSseNotifier {
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>() // txid -> emitters

    fun subscribe(txid: String, timeoutMs: Long = 10.minutes.inWholeMilliseconds): SseEmitter {
        val emitter = SseEmitter(timeoutMs)
        val list = listeners.computeIfAbsent(txid) { CopyOnWriteArrayList() }
        list += emitter

        val cleanup = {
            list.remove(emitter)
            if (list.isEmpty()) listeners.remove(txid)
        }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup() }

        return emitter
    }

    fun notifyPaid(txid: String, orderId: Long) {
        listeners[txid]?.let { subs ->
            val dead = mutableListOf<SseEmitter>()
            subs.forEach { em ->
                try {
                    em.send(
                        SseEmitter.event()
                            .name("paid")
                            .data(mapOf("txid" to txid, "orderId" to orderId))
                    )
                    em.complete()
                } catch (_: Exception) {
                    dead += em
                }
            }
            subs.removeAll(dead)
            if (subs.isEmpty()) listeners.remove(txid)
        }
    }
}
