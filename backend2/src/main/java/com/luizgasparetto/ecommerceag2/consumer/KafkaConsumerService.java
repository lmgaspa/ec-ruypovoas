package com.luizgasparetto.ecommerceag2.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luizgasparetto.ecommerceag2.dto.PurchaseMessage;
import com.luizgasparetto.ecommerceag2.model.PurchaseEntity;
import com.luizgasparetto.ecommerceag2.repository.PurchaseRepository;
import com.luizgasparetto.ecommerceag2.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class KafkaConsumerService {

    @Autowired
    private PurchaseRepository repository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "purchase-topic", groupId = "purchase-group", containerFactory = "kafkaListenerContainerFactory")
    public void listen(PurchaseMessage message) {
        PurchaseEntity entity = new PurchaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setFirstName(message.getFirstName());
        entity.setLastName(message.getLastName());
        entity.setCpf(message.getCpf());
        entity.setCountry(message.getCountry());
        entity.setCep(message.getCep());
        entity.setAddress(message.getAddress());
        entity.setNumber(message.getNumber());
        entity.setComplement(message.getComplement());
        entity.setDistrict(message.getDistrict());
        entity.setCity(message.getCity());
        entity.setState(message.getState());
        entity.setPhone(message.getPhone());
        entity.setEmail(message.getEmail());
        entity.setNote(message.getNote());
        entity.setDelivery(message.getDelivery());
        entity.setPayment(message.getPayment());
        entity.setValor(message.getValor());
        entity.setCartItems(message.getCartItems());

        System.out.println("DEBUG valor: " + message.getValor());
        System.out.println("DEBUG cartItems: " + message.getCartItems());

        repository.save(entity);
        emailService.sendPurchaseEmail(message);
    }
}

