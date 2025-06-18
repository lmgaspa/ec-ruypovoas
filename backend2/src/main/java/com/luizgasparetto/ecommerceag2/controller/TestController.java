package com.luizgasparetto.ecommerceag2.controller;

import com.luizgasparetto.ecommerceag2.dto.PurchaseMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    private final KafkaTemplate<String, PurchaseMessage> kafkaTemplate;

    public TestController(KafkaTemplate<String, PurchaseMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping
    public ResponseEntity<String> sendMessage(@RequestBody PurchaseMessage message) {
        kafkaTemplate.send("purchase-topic", message);
        return ResponseEntity.ok("Mensagem enviada para o Kafka com sucesso.");
    }
}

