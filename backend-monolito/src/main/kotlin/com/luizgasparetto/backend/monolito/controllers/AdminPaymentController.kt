package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.services.PaymentProcessor
import com.luizgasparetto.backend.monolito.services.PixClient
import com.luizgasparetto.backend.monolito.services.CardClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/payments")
class AdminPaymentController(
    private val pix: PixClient,
    private val card: CardClient,
    private val processor: PaymentProcessor
) {

    // 🔹 Força checagem manual do Pix por TXID
    @GetMapping("/check-pix/{txid}")
    fun checkPix(@PathVariable txid: String): ResponseEntity<String> {
        val status = pix.status(txid)
        val applied = processor.markPaidIfNeededByTxid(txid, status)
        return ResponseEntity.ok("Pix status=$status; applied=$applied")
    }

    // 🔹 Força checagem manual do Cartão por chargeId
    @GetMapping("/check-card/{chargeId}")
    fun checkCard(@PathVariable chargeId: String): ResponseEntity<String> {
        val status = card.status(chargeId)
        val applied = processor.markPaidIfNeededByChargeId(chargeId, status)
        return ResponseEntity.ok("Card status=$status; applied=$applied")
    }
}
