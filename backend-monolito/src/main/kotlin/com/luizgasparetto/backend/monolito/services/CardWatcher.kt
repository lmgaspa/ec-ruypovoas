package com.luizgasparetto.backend.monolito.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class CardWatcher(
    private val cardClient: CardClient,
    private val processor: PaymentProcessor
) {
    private val log = LoggerFactory.getLogger(CardWatcher::class.java)
    private val scheduler = Executors.newScheduledThreadPool(2)

    fun watch(chargeId: String, expiresAt: Instant) {
        log.info("CARD watcher iniciado chargeId={}, expiresAt={}", chargeId, expiresAt)
        scheduler.scheduleAtFixedRate({
            try {
                val status = cardClient.status(chargeId)
                log.debug("CARD polling chargeId={}, status={}", chargeId, status)

                val applied = processor.markPaidIfNeededByChargeId(chargeId, status)
                if (applied || Instant.now().isAfter(expiresAt)) {
                    log.info("Watcher CARD encerrado chargeId={}, applied={}, status={}", chargeId, applied, status)
                    throw CancellationException()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Erro no watcher CARD chargeId={}: {}", chargeId, e.message)
            }
        }, 0, 10, TimeUnit.SECONDS)
    }
}
