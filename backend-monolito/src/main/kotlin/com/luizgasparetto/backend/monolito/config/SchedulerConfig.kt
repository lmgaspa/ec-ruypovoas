package com.luizgasparetto.backend.monolito.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class SchedulerConfig {
    @Bean
    fun taskScheduler(): TaskScheduler {
        val s = ThreadPoolTaskScheduler()
        s.poolSize = 2
        s.setThreadNamePrefix("sse-hb-")
        s.initialize()
        return s
    }
}
