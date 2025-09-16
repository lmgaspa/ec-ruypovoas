package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service
class CardWatcher(
    private val cardClient: CardClient,
    private val orderRepo: OrderRepository,
    private val processor: PaymentProcessor,
    @Value("\${checkout.reserve.ttl-seconds:300}") private val ttlSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(CardWatcher::class.java)
    private val scheduler = Executors.newScheduledThreadPool(2)

    private val activeTasks = mutableMapOf<String, ScheduledFuture<*>>() // 🔑 controla tasks ativas por chargeId

    fun watch(chargeId: String, expiresAt: Instant) {
        if (activeTasks.containsKey(chargeId)) {
            log.info("Watcher já ativo para chargeId={}", chargeId)
            return
        }

        val task = scheduler.scheduleAtFixedRate({
            try {
                val status = cardClient.status(chargeId)
                val applied = processor.markPaidIfNeededByChargeId(chargeId, status)

                if (applied || Instant.now().isAfter(expiresAt)) {
                    log.info("Watcher encerrado chargeId={}, status={}", chargeId, status)
                    activeTasks.remove(chargeId)?.cancel(false) // 🔑 encerra limpo
                }
            } catch (e: Exception) {
                log.warn("Erro no watcher chargeId={}: {}", chargeId, e.message)
            }
        }, 0, 10, TimeUnit.SECONDS)

        activeTasks[chargeId] = task
    }
}
