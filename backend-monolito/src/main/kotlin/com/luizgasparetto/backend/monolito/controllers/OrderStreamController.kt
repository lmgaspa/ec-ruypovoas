package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.services.OrderSseNotifier
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/orders")
class OrderStreamController(
    private val notifier: OrderSseNotifier
) {
    @GetMapping("/stream/{txid}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@PathVariable txid: String): SseEmitter =
        notifier.subscribe(txid)
}
