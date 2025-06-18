package com.luizgasparetto.backend1.kafka

import com.luizgasparetto.backend1.dto.CartItemDto
import com.luizgasparetto.backend1.dto.PixPagamentoConfirmadoEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Component
@Service
class PixPagamentoConsumer {

    // Função que transforma a lista de CartItemDto em Map<idLivro, quantidade>
    private fun getBooksPurchasedMap(cartItems: List<CartItemDto>): Map<String, Int> {
        return cartItems.associate { it.id to it.quantity }
    }

    @KafkaListener(topics = ["pix_pagamentos"], groupId = "group_id")
    fun consume(event: PixPagamentoConfirmadoEvent) {
        val booksPurchased: Map<String, Int> = getBooksPurchasedMap(event.cartItems)

        // Aqui você já tem o mapa para atualizar o estoque livro a livro
        booksPurchased.forEach { (bookId, quantity) ->
            println("Livro comprado: $bookId, Quantidade: $quantity")
            // Chame o serviço para diminuir o estoque conforme cada livro
            // ex: stockService.decreaseStock(bookId, quantity)
        }
    }
}
