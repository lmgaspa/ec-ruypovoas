package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.dto.CheckoutRequest
import com.luizgasparetto.backend.monolito.dto.CheckoutResponse
import com.luizgasparetto.backend.monolito.services.CheckoutService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/checkout")
@CrossOrigin(origins = ["*"])
class CheckoutController(
    private val checkoutService: CheckoutService
) {

    @PostMapping
    fun checkout(@RequestBody request: CheckoutRequest): ResponseEntity<CheckoutResponse> {
        return try {
            val response = checkoutService.processCheckout(request)
            ResponseEntity.ok(response)
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                CheckoutResponse(
                    message = "Erro: ${ex.message}",
                    orderId = ""
                )
            )
        } catch (ex: Exception) {
            ResponseEntity.internalServerError().body(
                CheckoutResponse(
                    message = "Erro interno: ${ex.message}",
                    orderId = ""
                )
            )
        }
    }
}