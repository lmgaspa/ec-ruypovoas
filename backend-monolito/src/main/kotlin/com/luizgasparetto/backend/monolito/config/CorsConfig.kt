package com.luizgasparetto.backend.monolito.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            registry.addMapping("/**")
                .allowedOrigins(
                    "https://www.agenorgasparetto.com.br",
                    "https://agenorgasparetto.com.br",
                    "http://localhost:5173"
                )
                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type")
        }
    }
}
