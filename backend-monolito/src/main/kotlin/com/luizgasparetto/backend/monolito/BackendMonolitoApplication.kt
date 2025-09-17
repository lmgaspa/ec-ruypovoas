package com.luizgasparetto.backend.monolito

import com.luizgasparetto.backend.monolito.config.efi.EfiProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableConfigurationProperties(EfiProperties::class)
@SpringBootApplication
@EnableScheduling
class BackendMonolitoApplication

fun main(args: Array<String>) {
	runApplication<BackendMonolitoApplication>(*args)
}
