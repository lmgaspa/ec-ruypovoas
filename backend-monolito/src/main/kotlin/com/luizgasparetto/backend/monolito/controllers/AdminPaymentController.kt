package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.services.PaymentProcessor
import com.luizgasparetto.backend.monolito.services.PixClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/payments")
class AdminPaymentController(
    private val pix: PixClient,
    private val processor: PaymentProcessor
) {
    @PostMapping("/reprocess/{txid}")
    fun reprocess(@PathVariable txid: String): ResponseEntity<String> {
        val status = pix.status(txid)
        val paid = processor.isPaidStatus(status) && processor.markPaidIfNeededByTxid(txid)
        return ResponseEntity.ok("status=$status; applied=$paid")
    }
}
