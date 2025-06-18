package com.luizgasparetto.ecommerceag2.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luizgasparetto.ecommerceag2.dto.CartItemDto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

@Converter(autoApply = false)
public class CartItemListConverter implements AttributeConverter<List<CartItemDto>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<CartItemDto> cartItems) {
        try {
            return objectMapper.writeValueAsString(cartItems);
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro convertendo lista para JSON", e);
        }
    }

    @Override
    public List<CartItemDto> convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
