package com.luizgasparetto.backend.monolito.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {

    @Bean
    fun objectMapper(): ObjectMapper {
        // Cria um ObjectMapper configurado para Kotlin
        return jacksonObjectMapper()
    }
}
