package com.luizgasparetto.backend.monolito.config.swagger

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Ruy Povoas – Ecommerce API")
                .version("v1")
                .description("Endpoints do checkout Pix, webhook e catálogo")
        )
}