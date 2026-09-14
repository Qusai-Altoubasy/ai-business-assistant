package com.aibusinessassistant.product.dto;

public record LowStockProductDTO(
        Long id,
        String name,
        Integer stockQuantity,
        Integer minimumStock
) {
}
