package com.luizgasparetto.backend1

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
class Backend1Application

fun main(args: Array<String>) {
	runApplication<Backend1Application>(*args)
}
