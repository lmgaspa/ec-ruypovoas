package com.luizgasparetto.backend.monolito.services

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
    private val processor: PaymentProcessor,
    @Value("\${checkout.reserve.ttl-seconds:300}") private val ttlSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(CardWatcher::class.java)
    private val scheduler = Executors.newScheduledThreadPool(2)

    fun watch(chargeId: String, expiresAt: Instant) {
        log.info("CARD watcher agendado chargeId={}, expiresAt={}", chargeId, expiresAt)
        scheduler.scheduleAtFixedRate({
            try {
                val status = cardClient.status(chargeId)
                log.debug("CARD polling chargeId={}, status={}", chargeId, status)

                val applied = processor.markPaidIfNeededByChargeId(chargeId, status)
                val expired = Instant.now().isAfter(expiresAt)

                if (applied || expired) {
                    log.info("CARD watcher encerrado chargeId={}, applied={}, expired={}", chargeId, applied, expired)
                    throw CancellationException()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Erro no watcher cartão chargeId={}: {}", chargeId, e.message)
            }
        }, 0, 10, TimeUnit.SECONDS)
    }
}
