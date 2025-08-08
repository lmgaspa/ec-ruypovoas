package com.luizgasparetto.backend.monolito

import com.luizgasparetto.backend.monolito.config.EfiProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@EnableConfigurationProperties(EfiProperties::class)
@SpringBootApplication
class BackendMonolitoApplication

fun main(args: Array<String>) {
	runApplication<BackendMonolitoApplication>(*args)
}
