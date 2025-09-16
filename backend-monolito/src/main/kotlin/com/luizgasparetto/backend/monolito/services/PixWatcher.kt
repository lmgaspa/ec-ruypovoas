package com.luizgasparetto.backend.monolito.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class PixWatcher(
    private val pixClient: PixClient,
    private val processor: PaymentProcessor
) {
    private val log = LoggerFactory.getLogger(PixWatcher::class.java)
    private val scheduler = Executors.newScheduledThreadPool(2)

    private fun isPaidStatus(status: String?): Boolean =
        status != null && status.lowercase() in listOf(
            "pago", "paid", "approved", "confirmed", "concluida"
        )

    private fun isTerminalStatus(status: String?): Boolean =
        status != null && status.lowercase() in listOf(
            "concluida", "expirada", "removida_pelo_usuario_recebedor",
            "cancelada", "canceled"
        )

    fun watch(txid: String, expiresAt: Instant) {
        log.info("PIX watcher iniciado txid={}, expiresAt={}", txid, expiresAt)
        scheduler.scheduleAtFixedRate({
            try {
                val status = pixClient.status(txid)
                log.debug("PIX polling txid={}, status={}", txid, status)

                if (isPaidStatus(status)) {
                    processor.markPaidIfNeededByTxid(txid, status)
                    log.info("Watcher PIX finalizado (pago) txid={}", txid)
                    throw CancellationException()
                }

                if (Instant.now().isAfter(expiresAt) || isTerminalStatus(status)) {
                    log.info("Watcher PIX encerrado (expirado/terminal) txid={}, status={}", txid, status)
                    throw CancellationException()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Erro no watcher PIX txid={}: {}", txid, e.message)
            }
        }, 0, 10, TimeUnit.SECONDS)
    }
}
