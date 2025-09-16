package com.luizgasparetto.backend.monolito.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class PixWatcher(
    private val pixClient: PixClient,
    private val processor: PaymentProcessor
) {
    private val log = LoggerFactory.getLogger(PixWatcher::class.java)
    private val scheduler = Executors.newScheduledThreadPool(4)

    private fun isPaidStatus(status: String?): Boolean =
        status != null && status.lowercase() in listOf("pago", "paid", "approved", "confirmed")

    private fun isDeclinedStatus(status: String?): Boolean =
        status != null && status.lowercase() in listOf("cancelado", "canceled", "denied", "declined")

    fun watch(txid: String, expiresAt: Instant) {
        scheduler.scheduleAtFixedRate({
            try {
                val status = pixClient.status(txid)

                if (isPaidStatus(status)) {
                    processor.markPaidIfNeededByTxid(txid, status)
                    log.info("Watcher PIX finalizado: pago txid={}", txid)
                    return@scheduleAtFixedRate
                }

                if (isDeclinedStatus(status) || Instant.now().isAfter(expiresAt)) {
                    log.info("Watcher PIX encerrado: expirado/cancelado txid={}, status={}", txid, status)
                    return@scheduleAtFixedRate
                }
            } catch (e: Exception) {
                log.warn("Erro no watcher PIX txid={}: {}", txid, e.message)
            }
        }, 0, 10, TimeUnit.SECONDS)
    }
}
