package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val orderRepo: OrderRepository
) {
    @GetMapping("/sync")
    fun syncPayment(
        @RequestParam orderId: Long,
        @RequestParam email: String
    ): ResponseEntity<Any> {
        val order = orderRepo.findWithItemsById(orderId)
            ?: return ResponseEntity.notFound().build()

        if (!order.email.equals(email, ignoreCase = true)) {
            return ResponseEntity.status(403).body(mapOf("error" to "Access denied"))
        }

        return ResponseEntity.ok(
            mapOf(
                "paid" to order.paid,
                "status" to order.status,
                "paymentMethod" to order.paymentMethod,
                "txid" to order.txid,
                "chargeId" to order.chargeId,
                "paidAt" to order.paidAt,
                "installments" to order.installments
            )
        )
    }
}
