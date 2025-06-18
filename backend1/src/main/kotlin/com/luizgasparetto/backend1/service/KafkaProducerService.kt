package com.luizgasparetto.backend1.service

import com.luizgasparetto.backend1.dto.PixPagamentoConfirmadoEvent
import com.luizgasparetto.backend1.dto.PurchaseMessage
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaProducerService(
    private val kafkaTemplate: KafkaTemplate<String, PixPagamentoConfirmadoEvent>
) {
    fun enviarPagamentoConfirmado(event: PixPagamentoConfirmadoEvent) {
        kafkaTemplate.send("pix_pagamentos", event)
        println("📤 Enviado ao Kafka: $event")
    }
}

@Service
class StockService(
    private val kafkaTemplate: KafkaTemplate<String, PurchaseMessage>
) {
    fun sendPurchase(message: PurchaseMessage) {
        kafkaTemplate.send("purchase-topic", message)
    }
}