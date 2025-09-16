package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.services.AdminPaymentService
import com.luizgasparetto.backend.monolito.services.PaymentProcessor
import com.luizgasparetto.backend.monolito.services.PixClient
import com.luizgasparetto.backend.monolito.services.CardClient
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/payments")
class AdminPaymentController(
    private val paymentProcessor: PaymentProcessor,
    private val pixClient: PixClient,
    private val cardClient: CardClient,
    private val adminPaymentService: AdminPaymentService   // ✅ injetando service dedicado
) {
    private val log = LoggerFactory.getLogger(AdminPaymentController::class.java)

    /**
     * 🔹 Força atualização do status de um pagamento PIX
     */
    @PostMapping("/pix/{txid}/sync")
    fun syncPix(@PathVariable txid: String): Map<String, Any> {
        val status = pixClient.status(txid)
        val updated = paymentProcessor.markPaidIfNeededByTxid(txid, status)

        log.info("Admin PIX sync: txid={}, status={}, updated={}", txid, status, updated)
        return mapOf(
            "txid" to txid,
            "status" to (status ?: "UNKNOWN"),
            "updated" to updated
        )
    }

    /**
     * 🔹 Força atualização do status de um pagamento CARTÃO
     */
    @PostMapping("/card/{chargeId}/sync")
    fun syncCard(@PathVariable chargeId: String): Map<String, Any> {
        val status = cardClient.status(chargeId)
        val updated = paymentProcessor.markPaidIfNeededByChargeId(chargeId, status)

        log.info("Admin CARD sync: chargeId={}, status={}, updated={}", chargeId, status, updated)
        return mapOf(
            "chargeId" to chargeId,
            "status" to (status ?: "UNKNOWN"),
            "updated" to updated
        )
    }

    /**
     * 🔹 Expira manualmente uma reserva (admin force)
     */
    @PostMapping("/{orderId}/expire")
    fun expire(@PathVariable orderId: Long): Map<String, Any> {
        adminPaymentService.expireOrder(orderId)   // ✅ delega para o service
        log.info("Admin expirou manualmente o pedido orderId={}", orderId)
        return mapOf("orderId" to orderId, "expired" to true)
    }
}
