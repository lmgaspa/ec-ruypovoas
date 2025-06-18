package com.luizgasparetto.ecommerceag2.model;

import com.luizgasparetto.ecommerceag2.dto.CartItemDto;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_entity")
@Data
public class PurchaseEntity {

    @Id
    private UUID id;

    private String firstName;
    private String lastName;
    private BigDecimal valor;
    private String cpf;
    private String country;
    private String cep;
    private String address;
    private String number;
    private String complement;
    private String district;
    private String city;
    private String state;
    private String phone;
    private String email;
    private String note;
    private String delivery;
    private String payment;

    @Type(JsonType.class)
    @Column(name = "cart_items", columnDefinition = "jsonb")
    private List<CartItemDto> cartItems;
}

