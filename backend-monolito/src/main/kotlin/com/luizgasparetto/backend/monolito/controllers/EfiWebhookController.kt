package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.EmailService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.collections.get

@RestController
@RequestMapping("/api/efi-webhook")
class EfiWebhookController(
    private val orderRepository: OrderRepository,
    private val emailService: EmailService
) {

    @PostMapping
    fun handlePixNotification(@RequestBody payload: Map<String, Any>): ResponseEntity<String> {
        val pix = (payload["pix"] as? List<*>)?.firstOrNull() as? Map<*, *>
            ?: return ResponseEntity.badRequest().body("pix inválido")

        val txid = pix["txid"] as? String
            ?: return ResponseEntity.badRequest().body("txid não encontrado")

        val order = orderRepository.findByTxid(txid)
            ?: return ResponseEntity.notFound().build()

        if (order.paid != true) {
            order.paid = true
            orderRepository.save(order)

            emailService.sendClientEmail(order)
            emailService.sendAuthorEmail(order)
        }

        return ResponseEntity.ok("Notificação processada com sucesso")
    }
}