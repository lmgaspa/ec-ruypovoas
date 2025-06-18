package com.luizgasparetto.ecommerceag2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseMessage {
     private String firstName;
     private String lastName;
     private BigDecimal valor;
     private List<CartItemDto> cartItems;
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
}
