package com.luizgasparetto.backend.monolito.controllers

import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.OrderEventsPublisher
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/orders")
@CrossOrigin( // ajuste o(s) domínio(s) do seu frontend
    origins = ["https://www.editoranossolar.com", "https://editoranossolar"],
    allowedHeaders = ["*"]
)
class OrderEventsController(
    private val publisher: OrderEventsPublisher,
    private val orderRepo: OrderRepository
) {
    @GetMapping("/{orderId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(@PathVariable orderId: Long): SseEmitter {
        if (!orderRepo.existsById(orderId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado")
        }
        // timeout 0 = sem timeout no lado do Spring; os heartbeats do publisher evitam idle timeout do proxy
        return publisher.subscribe(orderId, 0)
    }
}
