package com.luizgasparetto.backend1.config

import com.luizgasparetto.backend1.dto.PixPagamentoConfirmadoEvent
import com.luizgasparetto.backend1.dto.PurchaseMessage
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaProducerConfig {

    @Bean
    fun pixProducerFactory(): ProducerFactory<String, PixPagamentoConfirmadoEvent> {
        val config = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to false
        )
        return DefaultKafkaProducerFactory(config)
    }

    @Bean
    fun purchaseProducerFactory(): ProducerFactory<String, PurchaseMessage> {
        val config = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to false
        )
        return DefaultKafkaProducerFactory(config)
    }

    @Bean
    fun kafkaTemplatePix(): KafkaTemplate<String, PixPagamentoConfirmadoEvent> {
        return KafkaTemplate(pixProducerFactory())
    }

    @Bean
    fun kafkaTemplatePurchase(): KafkaTemplate<String, PurchaseMessage> {
        return KafkaTemplate(purchaseProducerFactory())
    }
}
