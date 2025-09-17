package com.luizgasparetto.backend.monolito.controllers.checkout

import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutResponse
import com.luizgasparetto.backend.monolito.services.card.CardCheckoutService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/checkout/card")
class CardCheckoutController(
    private val cardCheckoutService: CardCheckoutService
) {
    @PostMapping
    fun checkoutCard(@RequestBody request: CardCheckoutRequest): ResponseEntity<CardCheckoutResponse> =
        ResponseEntity.ok(cardCheckoutService.processCardCheckout(request))
}
