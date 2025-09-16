package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class CardWatcher(
    private val cardClient: CardClient,
    private val orderRepo: OrderRepository,
    private val processor: PaymentProcessor,
    @Value("\${checkout.reserve.ttl-seconds:300}") private val ttlSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(CardWatcher::class.java)
    private val scheduler = Executors.newScheduledThreadPool(4)

    fun watch(chargeId: String, expiresAt: Instant) {
        scheduler.scheduleAtFixedRate({
            try {
                val status = cardClient.status(chargeId)
                val applied = processor.markPaidIfNeededByChargeId(chargeId, status)
                if (applied || Instant.now().isAfter(expiresAt)) {
                    log.info("Watcher encerrado chargeId={}, status={}", chargeId, status)
                    throw CancellationException() // ✅ unchecked, pode ser lançado
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Erro no watcher chargeId={}: {}", chargeId, e.message)
            }
        }, 0, 10, TimeUnit.SECONDS)
    }
}
