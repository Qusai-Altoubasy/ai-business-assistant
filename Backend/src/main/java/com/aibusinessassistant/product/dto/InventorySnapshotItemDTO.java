package com.aibusinessassistant.product.dto;

import com.aibusinessassistant.product.StockClass;

public record InventorySnapshotItemDTO(
        Long id,
        String name,
        Integer stockQuantity,
        Integer minimumStock,
        StockClass stockClass
) {
}
