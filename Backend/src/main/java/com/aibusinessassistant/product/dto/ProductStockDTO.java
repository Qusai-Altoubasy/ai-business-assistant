package com.aibusinessassistant.product.dto;

public record ProductStockDTO(
        Long id,
        String name,
        Integer stockQuantity,
        Integer minimumStock
) {
}